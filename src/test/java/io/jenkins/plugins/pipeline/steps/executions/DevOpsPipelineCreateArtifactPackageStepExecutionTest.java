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

public class DevOpsPipelineCreateArtifactPackageStepExecutionTest extends BaseDevOpsTest {

    private volatile String packageRegistrationResponse = null;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        packageRegistrationResponse = null;
        mockServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();

                if (path != null && path.contains("/pipelineInfo")) {
                    return jsonResponse("{\"result\": {\"status\": \"Success\", \"track\": true}}");
                }

                if (path != null && path.contains("/package/registration") && packageRegistrationResponse != null) {
                    return jsonResponse(packageRegistrationResponse);
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
     * Test: Valid package creation with tracked pipeline.
     * Expected: Package is created successfully, build succeeds.
     */
    @Test
    public void testPackageStepSuccessful() throws Exception {
        packageRegistrationResponse = "{\"result\": {\"status\": \"success\", \"response\": \"ok\"}}";

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("package-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Using DevOps configuration DevOpsConfig1", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsPackage with a non-existent configuration name.
     * Note: The package step hardcodes isIgnoreErrors=true in handleConfigurationNotFound,
     * so a missing config is always ignored gracefully.
     * Expected: "Could not find an active configuration ... but will ignore" logged, build succeeds.
     */
    @Test
    public void testPackageStepWithNonExistentConfig() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("package-nonexistent-config.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Could not find an active configuration with name NonExistentConfig, but will ignore", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsPackage when pipeline is not tracked (pipelineInfoConfig.isTrack() == false).
     * Expected: "Pipeline is not tracked" message logged, build succeeds without creating package.
     */
    @Test
    public void testPackageStepPipelineNotTracked() throws Exception {
        DevOpsRootAction.setTrackedJob("TestJob_1");
        DevOpsPipelineInfoConfig config = new DevOpsPipelineInfoConfig(
                false,
                devOpsConfiguration.getEntries().get(0),
                "1234_" + mockServerUrl
        );
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = new DevOpsModel.DevOpsPipelineInfo(Arrays.asList(config));
        DevOpsRootAction.setSnPipelineInfo("TestJob_1", pipelineInfo);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("package-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Pipeline is not tracked", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsPackage with invalid (non-JSON) artifacts payload.
     * Expected: Exception caught and logged to console, build fails.
     */
    @Test
    public void testPackageStepWithInvalidPayload() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("package-invalid-payload.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Error occured while registering the artifact package,Exception:", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsPackage when ServiceNow server returns an error response.
     * Expected: "FAILED: Artifact package could not be created." message logged, build fails.
     */
    @Test
    public void testPackageStepServerErrorResponse() throws Exception {
        packageRegistrationResponse = "{\n" +
                "  \"result\": {\n" +
                "    \"status\": \"error\",\n" +
                "    \"details\": {\n" +
                "      \"errors\": [\n" +
                "        {\n" +
                "          \"message\": \"Package creation failed on server\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}";

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("package-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("FAILED: Artifact package could not be created.", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }

    /**
     * Test: snDevOpsPackage when no active default configuration exists.
     * Expected: NullPointerException caught before config lookup, error logged to console, build fails.
     */
    @Test
    public void testPackageStepWithNoActiveDefaultConfig() throws Exception {
        devOpsConfiguration.setEntries(null);
        devOpsConfiguration.save();

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("package-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Error occured while registering the artifact package,Exception:", run);
        jenkins.assertBuildStatus(hudson.model.Result.FAILURE, jenkins.waitForCompletion(run));
    }
}
