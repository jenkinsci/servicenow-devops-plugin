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

public class DevOpsPipelineGetChangeNumberStepExecutionTest extends BaseDevOpsTest {

    private volatile String changeInfoResponse = null;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        changeInfoResponse = null;
        mockServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();

                if (path != null && path.contains("/pipelineInfo")) {
                    return jsonResponse("{\"result\": {\"status\": \"Success\", \"track\": true}}");
                }

                if (path != null && path.contains("/changeInfo") && changeInfoResponse != null) {
                    return jsonResponse(changeInfoResponse);
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
     * Test: snDevOpsGetChangeNumber with valid changeDetails returns change number.
     * Expected: Change number logged to console, build succeeds.
     */
    @Test
    public void testGetChangeNumberSuccessful() throws Exception {
        changeInfoResponse = "{\"result\": {\"number\": \"CHG0001234\"}}";

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("get-change-number.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] snDevOpsGetChangeNumber,  'Change Request Number' => CHG0001234", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsGetChangeNumber with a non-existent configuration name.
     * Note: handleConfigurationNotFound is called with isIgnoreErrors=true, so missing config is ignored.
     * Expected: "Could not find an active configuration with name NonExistentConfig, but will ignore" logged, build succeeds.
     */
    @Test
    public void testGetChangeNumberWithNonExistentConfig() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("get-change-number-nonexistent-config.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Could not find an active configuration with name NonExistentConfig, but will ignore", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsGetChangeNumber when pipeline is not tracked.
     * Expected: "Pipeline is not tracked" message logged, build succeeds.
     */
    @Test
    public void testGetChangeNumberPipelineNotTracked() throws Exception {
        DevOpsRootAction.setTrackedJob("TestJob_1");
        DevOpsPipelineInfoConfig config = new DevOpsPipelineInfoConfig(
                false,
                devOpsConfiguration.getEntries().get(0),
                "1234_" + mockServerUrl
        );
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = new DevOpsModel.DevOpsPipelineInfo(Arrays.asList(config));
        DevOpsRootAction.setSnPipelineInfo("TestJob_1", pipelineInfo);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("get-change-number-no-details.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Pipeline is not tracked", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsGetChangeNumber with changeDetails missing required fields (build_number and stage_name).
     * Expected: "Couldn't get 'Change Request Number'. Please provide Build Number, Stage Name." logged, build succeeds.
     */
    @Test
    public void testGetChangeNumberMissingFields() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("get-change-number-missing-fields.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("Couldn't get 'Change Request Number'. Please provide", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsGetChangeNumber when server returns error message instead of number.
     * Expected: Error message logged, build succeeds (step returns null but doesn't throw).
     */
    @Test
    public void testGetChangeNumberServerErrorResponse() throws Exception {
        changeInfoResponse = "{\"result\": {\"errorMessage\": \"Change not found for given details\"}}";

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("get-change-number.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("Couldn't get 'Change Request Number', Change not found for given details", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsGetChangeNumber when no active default configuration exists.
     * Expected: NullPointerException caught, error logged to console, build fails.
     */
    @Test
    public void testGetChangeNumberWithNoActiveDefaultConfig() throws Exception {
        devOpsConfiguration.setEntries(null);
        devOpsConfiguration.save();

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("get-change-number-no-details.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Error occurred while running snDevOpsGetChangeNumber, Exception:", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }
}
