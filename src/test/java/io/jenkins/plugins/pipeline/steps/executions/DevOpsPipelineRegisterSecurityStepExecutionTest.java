package io.jenkins.plugins.pipeline.steps.executions;

import io.jenkins.plugins.BaseDevOpsTest;
import io.jenkins.plugins.DevOpsRootAction;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.model.DevOpsPipelineInfoConfig;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class DevOpsPipelineRegisterSecurityStepExecutionTest extends BaseDevOpsTest {

    private volatile String securityRegistrationResponse = null;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        securityRegistrationResponse = null;
        mockServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();

                if (path != null && path.contains("/pipelineInfo")) {
                    return jsonResponse("{\"result\": {\"status\": \"Success\", \"track\": true}}");
                }

                if (path != null && path.contains("/tool/security") && securityRegistrationResponse != null) {
                    return jsonResponse(securityRegistrationResponse);
                }

                return jsonResponse("{\"result\": {\"status\": \"success\"}}");
            }
        });
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    /**
     * Test: Valid security result registration with tracked pipeline.
     * Expected: Security result is registered successfully, build succeeds.
     */
    @Test
    public void testSecurityResultStepSuccessful() throws Exception {
        securityRegistrationResponse = "{\"result\": {\"status\": \"success\"}}";

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("security-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] ServiceNow DevOps - Register Security Step - Security step information is successfully sent to ServiceNow", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsSecurityResult with a non-existent configuration name.
     * Note: handleConfigurationNotFound is called with isIgnoreErrors=false,
     * so a missing config throws AbortException.
     * Expected: "Could not find an active configuration with name NonExistentConfig" logged, build fails.
     */
    @Test
    public void testSecurityResultStepWithNonExistentConfig() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("security-nonexistent-config.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Could not find an active configuration with name NonExistentConfig", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsSecurityResult when pipeline is not tracked.
     * Expected: "Pipeline is not tracked" message logged, build succeeds.
     */
    @Test
    public void testSecurityResultStepPipelineNotTracked() throws Exception {
        DevOpsRootAction.setTrackedJob("TestJob_1");
        DevOpsPipelineInfoConfig config = new DevOpsPipelineInfoConfig(
                false,
                devOpsConfiguration.getEntries().get(0),
                "1234_" + mockServerUrl
        );
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = new DevOpsModel.DevOpsPipelineInfo(Arrays.asList(config));
        DevOpsRootAction.setSnPipelineInfo("TestJob_1", pipelineInfo);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("security-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Pipeline is not tracked", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsSecurityResult with invalid (non-JSON) securityResultAttributes.
     * Expected: JSONException caught, handleException called with "securityResultAttributes should be in stringified JSON format",
     * AbortException thrown (isIgnoreSNErrors defaults to false), build fails.
     */
    @Test
    public void testSecurityResultStepWithInvalidPayload() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("security-invalid-payload.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("snDevOpsSecurityResult - securityResultAttributes should be in stringified JSON format", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsSecurityResult when ServiceNow server returns a failure status.
     * Expected: handleException called with the error message, AbortException thrown, build fails.
     */
    @Test
    public void testSecurityResultStepServerFailureResponse() throws Exception {
        securityRegistrationResponse = "{\"result\": {\"status\": \"Failure\", \"message\": \"Security result registration failed on server\"}}";

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("security-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("snDevOpsSecurityResult - Security result registration failed on server", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsSecurityResult when ServiceNow server returns a response without status field.
     * Expected: JSONException caught trying to read status, handleException called with
     * "Register step failed : No Response From the Server", build fails.
     */
    @Test
    public void testSecurityResultStepServerNoStatusResponse() throws Exception {
        securityRegistrationResponse = "{\"result\": {}}";

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("security-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("snDevOpsSecurityResult - Register step failed : No Response From the Server", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsSecurityResult when no active default configuration exists.
     * Expected: NullPointerException caught by outer catch block, error logged, build fails.
     */
    @Test
    public void testSecurityResultStepWithNoActiveDefaultConfig() throws Exception {
        devOpsConfiguration.setEntries(null);
        devOpsConfiguration.save();

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("security-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Error occurred while registering the Security scan results,Exception:", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }
}
