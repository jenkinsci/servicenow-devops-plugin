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

public class DevOpsPipelineRegisterArtifactStepExecutionTest extends BaseDevOpsTest {

    private volatile String artifactRegistrationResponse = null;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        artifactRegistrationResponse = null;
        mockServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();

                if (path != null && path.contains("/pipelineInfo")) {
                    return jsonResponse("{\"result\": {\"status\": \"Success\", \"track\": true}}");
                }

                if (path != null && path.contains("/artifact/registration") && artifactRegistrationResponse != null) {
                    return jsonResponse(artifactRegistrationResponse);
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
     * Test: Valid artifact registration with tracked pipeline.
     * Expected: Artifact is registered successfully, build succeeds.
     */
    @Test
    public void testArtifactStepSuccessful() throws Exception {
        artifactRegistrationResponse = "{\"result\": {\"status\": \"success\", \"response\": \"ok\"}}";

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("artifact-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Using DevOps configuration DevOpsConfig1", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsArtifact with a non-existent configuration name.
     * Expected: AbortException thrown with "Could not find an active configuration" message, build fails.
     */
    @Test
    public void testArtifactStepWithNonExistentConfig() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("artifact-nonexistent-config.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Could not find an active configuration with name NonExistentConfig", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsArtifact with a non-existent configuration name and ignoreErrors=true.
     * Expected: Error is ignored gracefully, build succeeds with "but will ignore" message.
     */
    @Test
    public void testArtifactStepWithNonExistentConfigIgnoreErrors() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("artifact-nonexistent-config-ignore.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Could not find an active configuration with name NonExistentConfig, but will ignore", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsArtifact when pipeline is not tracked (pipelineInfoConfig.isTrack() == false).
     * Expected: "Pipeline is not tracked" message logged, build succeeds without registering artifact.
     */
    @Test
    public void testArtifactStepPipelineNotTracked() throws Exception {
        DevOpsRootAction.setTrackedJob("TestJob_1");
        DevOpsPipelineInfoConfig config = new DevOpsPipelineInfoConfig(
                false,
                devOpsConfiguration.getEntries().get(0),
                "1234_" + mockServerUrl
        );
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = new DevOpsModel.DevOpsPipelineInfo(Arrays.asList(config));
        DevOpsRootAction.setSnPipelineInfo("TestJob_1", pipelineInfo);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("artifact-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Pipeline is not tracked", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsArtifact with invalid (non-JSON) artifacts payload.
     * Expected: Exception caught and logged, build fails with error message.
     */
    @Test
    public void testArtifactStepWithInvalidPayload() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("artifact-invalid-payload.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Error occurred while registering the artifact, Exception:", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsArtifact when ServiceNow server returns an error response.
     * Expected: "FAILED: Artifact could not be registered." message logged, build fails.
     */
    @Test
    public void testArtifactStepServerErrorResponse() throws Exception {
        artifactRegistrationResponse = "{\n" +
                "  \"result\": {\n" +
                "    \"status\": \"error\",\n" +
                "    \"details\": {\n" +
                "      \"errors\": [\n" +
                "        {\n" +
                "          \"message\": \"Artifact registration failed on server\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}";

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("artifact-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("FAILED: Artifact could not be registered.", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsArtifact when no active default configuration exists.
     * Expected: NullPointerException caught, error logged to console, build fails.
     */
    @Test
    public void testArtifactStepWithNoActiveDefaultConfig() throws Exception {
        devOpsConfiguration.setEntries(null);
        devOpsConfiguration.save();

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("artifact-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Error occurred while registering the artifact, Exception:", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }
}
