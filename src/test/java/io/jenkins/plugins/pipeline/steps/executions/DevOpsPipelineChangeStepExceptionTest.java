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

public class DevOpsPipelineChangeStepExceptionTest extends BaseDevOpsTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mockServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();

                if (path != null && path.contains("/pipelineInfo")) {
                    return jsonResponse("{\"result\": {\"status\": \"Success\", \"track\": true}}");
                }

                if (path != null && path.contains("/orchestration/changeControl")) {
                    return jsonResponse("{\"result\": {\"status\": \"success\", \"changeControl\": \"true\"}}");
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
     * Test: snDevOpsChange with a non-existent configuration name.
     * Expected: AbortException with "Could not find an active configuration with name NonExistentConfig", build fails.
     */
    @Test
    public void testChangeStepWithNonExistentConfig() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-nonexistent-config.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Could not find an active configuration with name NonExistentConfig", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsChange with a non-existent configuration name and ignoreErrors=true.
     * Expected: "Could not find an active configuration with name NonExistentConfig, but will ignore" logged, build succeeds.
     */
    @Test
    public void testChangeStepWithNonExistentConfigIgnoreErrors() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-nonexistent-config-ignore.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Could not find an active configuration with name NonExistentConfig, but will ignore", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsChange with enabled=false.
     * Expected: Step returns immediately via onSuccess, build succeeds.
     * Note: The message is passed to onSuccess() as return value, not printed to console.
     */
    @Test
    public void testChangeStepDisabled() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-disabled.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsChange when pipeline is not tracked.
     * Expected: "Pipeline is not tracked" message logged, build succeeds.
     */
    @Test
    public void testChangeStepPipelineNotTracked() throws Exception {
        DevOpsRootAction.setTrackedJob("TestJob_1");
        DevOpsPipelineInfoConfig config = new DevOpsPipelineInfoConfig(
                false,
                devOpsConfiguration.getEntries().get(0),
                "1234_" + mockServerUrl
        );
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = new DevOpsModel.DevOpsPipelineInfo(Arrays.asList(config));
        DevOpsRootAction.setSnPipelineInfo("TestJob_1", pipelineInfo);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Pipeline is not tracked", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsChange with applicationName but no snapshotName.
     * Expected: AbortException "Both application name and snapshot name should be provided", build fails.
     */
    @Test
    public void testChangeStepAppNameWithoutSnapshot() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-app-without-snapshot.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("Both application name and snapshot name should be provided", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsChange when no active default configuration exists (entries set to null).
     * Expected: NullPointerException caught by outer catch, error logged, build fails.
     */
    @Test
    public void testChangeStepWithNoActiveDefaultConfig() throws Exception {
        devOpsConfiguration.setEntries(null);
        devOpsConfiguration.save();

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Error occurred registering the change request,Exception:", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }
}
