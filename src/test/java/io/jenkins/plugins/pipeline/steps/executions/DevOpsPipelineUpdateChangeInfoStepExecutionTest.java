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

public class DevOpsPipelineUpdateChangeInfoStepExecutionTest extends BaseDevOpsTest {

    private volatile String updateChangeInfoResponse = null;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        updateChangeInfoResponse = null;
        mockServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                String method = request.getMethod();

                if (path != null && path.contains("/pipelineInfo")) {
                    return jsonResponse("{\"result\": {\"status\": \"Success\", \"track\": true}}");
                }

                if (path != null && path.contains("/changeInfo") && "PUT".equalsIgnoreCase(method) && updateChangeInfoResponse != null) {
                    return jsonResponse(updateChangeInfoResponse);
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
     * Test: snDevOpsUpdateChangeInfo with valid change number and details.
     * Expected: Update successful message logged, build succeeds.
     */
    @Test
    public void testUpdateChangeInfoSuccessful() throws Exception {
        updateChangeInfoResponse = "{\"result\": {\"status\": \"success\"}}";

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("update-change-info.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("Update Successful for 'Change Request Number' => CHG0001234", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsUpdateChangeInfo with a non-existent configuration name.
     * Note: handleConfigurationNotFound is called with isIgnoreErrors=true, so missing config is ignored.
     * Expected: "Could not find an active configuration with name NonExistentConfig, but will ignore" logged, build succeeds.
     */
    @Test
    public void testUpdateChangeInfoWithNonExistentConfig() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("update-change-info-nonexistent-config.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Could not find an active configuration with name NonExistentConfig, but will ignore", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsUpdateChangeInfo when pipeline is not tracked.
     * Expected: "Pipeline is not tracked" message logged, build succeeds.
     */
    @Test
    public void testUpdateChangeInfoPipelineNotTracked() throws Exception {
        DevOpsRootAction.setTrackedJob("TestJob_1");
        DevOpsPipelineInfoConfig config = new DevOpsPipelineInfoConfig(
                false,
                devOpsConfiguration.getEntries().get(0),
                "1234_" + mockServerUrl
        );
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = new DevOpsModel.DevOpsPipelineInfo(Arrays.asList(config));
        DevOpsRootAction.setSnPipelineInfo("TestJob_1", pipelineInfo);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("update-change-info.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Pipeline is not tracked", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsUpdateChangeInfo without changeRequestNumber.
     * Expected: "UPDATE Failed, Please provide a valid 'Change Request Number'" logged, build succeeds (returns false but no exception).
     */
    @Test
    public void testUpdateChangeInfoNoChangeNumber() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("update-change-info-no-number.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("UPDATE Failed, Please provide a valid 'Change Request Number' to proceed.", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsUpdateChangeInfo without changeRequestDetails.
     * Expected: "UPDATE Failed, Please provide a valid 'Change Request Details'" logged, build succeeds.
     */
    @Test
    public void testUpdateChangeInfoNoDetails() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("update-change-info-no-details.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("UPDATE Failed, Please provide a valid 'Change Request Details' to proceed.", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsUpdateChangeInfo with invalid (non-JSON) changeRequestDetails.
     * Expected: Exception caught in updateChangeRequestDetails, error logged, build succeeds (returns false).
     */
    @Test
    public void testUpdateChangeInfoInvalidDetails() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("update-change-info-invalid-details.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("Couldn't Update 'Change Request' with provided details,", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsUpdateChangeInfo when server returns a non-success response.
     * Expected: "Couldn't Update 'Change Request' with provided details" logged, build succeeds (returns false).
     */
    @Test
    public void testUpdateChangeInfoServerFailureResponse() throws Exception {
        updateChangeInfoResponse = "{\"result\": {\"status\": \"failure\", \"message\": \"Change request not found\"}}";

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("update-change-info.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("Couldn't Update 'Change Request' with provided details,", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsUpdateChangeInfo when no active default configuration exists.
     * Expected: NullPointerException caught, error logged, build fails.
     */
    @Test
    public void testUpdateChangeInfoWithNoActiveDefaultConfig() throws Exception {
        devOpsConfiguration.setEntries(null);
        devOpsConfiguration.save();

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("update-change-info.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Error occurred while updating the change request,Exception:", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }
}
