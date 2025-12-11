package io.jenkins.plugins;

import io.jenkins.plugins.model.DevOpsModel;
import okhttp3.mockwebserver.MockResponse;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static io.jenkins.plugins.utils.DevOpsConstants.FAILURE_REASON_CONN_REFUSED_UI;
import static io.jenkins.plugins.utils.DevOpsConstants.FAILURE_REASON_USER_NOAUTH_UI;

public class DevOpsJenkinsConfigurationTest extends BaseDevOpsTest {
    private WorkflowJob job;



    @Before
    public void setUp() throws Exception {
        super.setUp();
        DevOpsRootAction.setSnPipelineInfo("TestJob_1", null);
        job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "    agent any\n" +
                        "    stages {\n" +
                        "        stage('Test') {\n" +
                        "            steps {\n" +
                        "               snDevOpsPackage artifactsPayload: '{\"artifacts\":[{\"name\": \"sa-web.jar\", \"version\": \"1.9\", \"repositoryName\": \"services-1031\"}, {\"name\": \"sa-db.jar\", \"version\": \"1.3.2\", \"repositoryName\": \"services-1032\"}], \"branchName\": \"master\"}', name: 'packageName'\n" +
                        "            }\n" +
                        "        }\n" +
                        "    }\n" +
                        "}",
                true
        ));
    }

    @Test
    public void testJenkinsConfigurationWithInvalidCredentials() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"result\": {\"status\": \"success\", \"track\": \"failureReason: User Not Authenticated\"}}")
                .setHeader("Content-Type", "application/json")
                .setResponseCode(200));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Pipeline is not tracked", run);
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = DevOpsRootAction.getSnPipelineInfo("TestJob_1");
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
        assertData(pipelineInfo, FAILURE_REASON_USER_NOAUTH_UI.toString());
    }

    @Test
    public void testJenkinsConfigurationWithInstanceDown() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"result\": {\"status\": \"success\", \"track\": \"failureReason: IOException: Connection refused\"}}")
                .setHeader("Content-Type", "application/json")
                .setResponseCode(200));

        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Pipeline is not tracked", run);
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = DevOpsRootAction.getSnPipelineInfo("TestJob_1");
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
        assertData(pipelineInfo, FAILURE_REASON_CONN_REFUSED_UI.toString());
    }

    @Test
    public void testJenkinsConfigurationWithInvalidToolId() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"result\": {\"status\": \"success\", \"track\": \"failureReason: IOException: Connection refused\"}}")
                .setHeader("Content-Type", "application/json")
                .setResponseCode(200));

        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Pipeline is not tracked", run);
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = DevOpsRootAction.getSnPipelineInfo("TestJob_1");
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
        assertData(pipelineInfo, FAILURE_REASON_CONN_REFUSED_UI.toString());
    }

    private void assertData(DevOpsModel.DevOpsPipelineInfo pipelineInfo, String expectedMessage) {
        if(pipelineInfo != null) {
            Assert.assertNotNull("Pipeline info should not be null", pipelineInfo);
            Assert.assertFalse("Pipeline configs should not be empty", pipelineInfo.getDevopsPipelineConfigs().isEmpty());
            Assert.assertEquals(expectedMessage, pipelineInfo.getDevopsPipelineConfigs().get(0).getErrorMessage());
        }
    }
}
