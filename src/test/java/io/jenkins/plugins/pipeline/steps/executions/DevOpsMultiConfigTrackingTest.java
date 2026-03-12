package io.jenkins.plugins.pipeline.steps.executions;

import io.jenkins.plugins.BaseDevOpsTest;
import io.jenkins.plugins.DevOpsRootAction;
import io.jenkins.plugins.config.DevOpsConfigurationEntry;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.model.DevOpsPipelineInfoConfig;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test ID: TNTC0356339
 * Story: STRY56797422 - Enhance pipeline tracking check to support multiple configurations
 *
 * Verifies that pipeline tracking works correctly with multiple ServiceNow configurations,
 * including force tracking, pull request flags, default/non-default configuration selection,
 * and all DevOps pipeline steps (snDevOpsChange, snDevOpsArtifact, snDevOpsPackage,
 * snDevOpsSecurityResult).
 */
public class DevOpsMultiConfigTrackingTest extends BaseDevOpsTest {

    private MockWebServer mockServer2;
    private String mockServer2Url;

    /** Records all requests received by mockServer (config1). */
    private CopyOnWriteArrayList<RecordedRequest> server1Requests;
    /** Records all requests received by mockServer2 (config2). */
    private CopyOnWriteArrayList<RecordedRequest> server2Requests;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        server1Requests = new CopyOnWriteArrayList<>();
        server2Requests = new CopyOnWriteArrayList<>();

        // Replace mockServer's default queue dispatcher with a catch-all dispatcher
        // so that tests never hang waiting for an enqueued response.
        mockServer.setDispatcher(createDispatcher(server1Requests, true));

        // Start a second MockWebServer to simulate a second ServiceNow instance
        mockServer2 = new MockWebServer();
        mockServer2.setDispatcher(createDispatcher(server2Requests, true));
        mockServer2.start();
        mockServer2Url = mockServer2.url("/").toString();
        if (mockServer2Url.endsWith("/")) {
            mockServer2Url = mockServer2Url.substring(0, mockServer2Url.length() - 1);
        }
    }

    @After
    public void tearDownMultiConfig() throws Exception {
        if (mockServer2 != null) {
            mockServer2.shutdown();
        }
    }

    /**
     * Creates a Dispatcher that records requests and returns appropriate responses
     * based on URL path patterns. This prevents tests from hanging due to missing
     * enqueued responses — every request gets an immediate response.
     *
     * @param requestLog  list to record incoming requests for assertions
     * @param tracked     whether the tracking endpoint should report the pipeline as tracked
     */
    private Dispatcher createDispatcher(CopyOnWriteArrayList<RecordedRequest> requestLog, boolean tracked) {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                requestLog.add(request);
                String path = request.getPath();

                // Pipeline tracking check (/pipelineInfo)
                if (path != null && path.contains("/pipelineInfo")) {
                    return jsonResponse("{\"result\": {\"status\": \"Success\", \"track\": " + tracked + "}}");
                }

                // Change control check (/changeControl) — "status" required by parseResponseResult
                if (path != null && path.contains("/changeControl")) {
                    return jsonResponse("{\"result\": {\"status\": \"success\", \"changeControl\": \"false\"}}");
                }

                // Artifact registration (/artifact/registration)
                if (path != null && path.contains("/artifact/registration")) {
                    return jsonResponse("{\"result\": {\"status\": \"success\", \"response\": \"ok\"}}");
                }

                // Package registration (/package/registration)
                if (path != null && path.contains("/package/registration")) {
                    return jsonResponse("{\"result\": {\"status\": \"success\", \"response\": \"ok\"}}");
                }

                // Security result registration (/tool/security)
                if (path != null && path.contains("/tool/security")) {
                    return jsonResponse("{\"result\": {\"status\": \"success\", \"response\": \"ok\"}}");
                }

                // Notification / orchestration calls
                if (path != null && path.contains("/orchestration")) {
                    return jsonResponse("{\"result\": {\"status\": \"success\"}}");
                }

                // Default catch-all response
                return jsonResponse("{\"result\": {\"status\": \"success\"}}");
            }
        };
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    /**
     * Adds a second (non-default) configuration entry pointing to mockServer2.
     */
    private DevOpsConfigurationEntry addSecondConfiguration(boolean trackCheck, boolean trackPRCheck) {
        DevOpsConfigurationEntry entry2 = new DevOpsConfigurationEntry(
                "DevOpsConfig2",
                true,
                false, // not default
                mockServer2Url,
                "5678",
                "9012",
                "v2",
                "cred2",
                trackCheck,
                trackPRCheck,
                ""
        );
        sampleEntries.add(entry2);
        devOpsConfiguration.setEntries(sampleEntries);
        devOpsConfiguration.save();
        return entry2;
    }

    /**
     * Reconfigures the default (first) config entry with the given tracking flags.
     */
    private void reconfigureDefaultEntry(boolean trackCheck, boolean trackPRCheck) {
        DevOpsConfigurationEntry defaultEntry = sampleEntries.get(0);
        defaultEntry.setTrackCheck(trackCheck);
        defaultEntry.setTrackPullRequestPipelinesCheck(trackPRCheck);
        devOpsConfiguration.setEntries(sampleEntries);
        devOpsConfiguration.save();
    }

    /**
     * Sets up pipeline tracking info with both configs for a single job.
     */
    private void setupDualConfigTracking(String jobKey, boolean config1Tracked, boolean config2Tracked,
                                          DevOpsConfigurationEntry entry2) {
        DevOpsRootAction.setTrackedJob(jobKey);

        String configKey1 = sampleEntries.get(0).getToolId() + "_" + sampleEntries.get(0).getInstanceUrl();
        DevOpsPipelineInfoConfig pipeConfig1 = new DevOpsPipelineInfoConfig(
                config1Tracked,
                sampleEntries.get(0),
                configKey1
        );

        String configKey2 = entry2.getToolId() + "_" + entry2.getInstanceUrl();
        DevOpsPipelineInfoConfig pipeConfig2 = new DevOpsPipelineInfoConfig(
                config2Tracked,
                entry2,
                configKey2
        );

        DevOpsModel.DevOpsPipelineInfo pipelineInfo = new DevOpsModel.DevOpsPipelineInfo(
                Arrays.asList(pipeConfig1, pipeConfig2));
        DevOpsRootAction.setSnPipelineInfo(jobKey, pipelineInfo);
    }

    /**
     * Sets up pipeline tracking info for a job against a single config entry.
     */
    private void setupSingleConfigTracking(String jobKey, boolean tracked, DevOpsConfigurationEntry entry) {
        DevOpsRootAction.setTrackedJob(jobKey);
        String configKey = entry.getToolId() + "_" + entry.getInstanceUrl();
        DevOpsPipelineInfoConfig config = new DevOpsPipelineInfoConfig(
                tracked,
                entry,
                configKey
        );
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = new DevOpsModel.DevOpsPipelineInfo(Arrays.asList(config));
        DevOpsRootAction.setSnPipelineInfo(jobKey, pipelineInfo);
    }

    // ============================================================================================
    // TC1: Multiple configs, no config name, force tracking=false, PR flag=false
    //      → Pipeline not tracked, no events sent to any ServiceNow instance
    // ============================================================================================

    /**
     * Test ID: TNTC0356339 - TC1
     * Two or more ServiceNow configurations with force tracking and PR flag both false.
     * Run pipeline without explicit configuration name.
     * Expected: Pipeline is not tracked, no task execution created, no events sent.
     */
    @Test
    public void testMultiConfigNoConfigNameForceTrackFalsePRFlagFalse() throws Exception {
        reconfigureDefaultEntry(false, false);
        DevOpsConfigurationEntry entry2 = addSecondConfiguration(false, false);

        // Both servers report pipelines as not tracked
        mockServer.setDispatcher(createDispatcher(server1Requests, false));
        mockServer2.setDispatcher(createDispatcher(server2Requests, false));

        setupDualConfigTracking("TestJob_1", false, false, entry2);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Pipeline is not tracked", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    // ============================================================================================
    // TC2: Multiple configs, no config name, force tracking=true, PR flag=true
    //      → Pipeline tracked, events sent to all ServiceNow instances
    // ============================================================================================

    /**
     * Test ID: TNTC0356339 - TC2
     * Two or more ServiceNow configurations with force tracking and PR flag both true.
     * Run pipeline without explicit configuration name.
     * Expected: Pipeline is tracked, task execution created, events sent to all instances.
     */
    @Test
    public void testMultiConfigNoConfigNameForceTrackTruePRFlagTrue() throws Exception {
        reconfigureDefaultEntry(true, true);
        DevOpsConfigurationEntry entry2 = addSecondConfiguration(true, true);

        // Both servers report pipelines as tracked
        mockServer.setDispatcher(createDispatcher(server1Requests, true));
        mockServer2.setDispatcher(createDispatcher(server2Requests, true));

        setupDualConfigTracking("TestJob_1", true, true, entry2);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Using DevOps configuration DevOpsConfig1", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    // ============================================================================================
    // TC3: Default config, no explicit config name in snDevOpsArtifact/Package/Change
    //      → Uses default configuration for all steps
    // ============================================================================================

    /**
     * Test ID: TNTC0356339 - TC3
     * Instance configured in Jenkins. Run pipeline without configuration name
     * in snDevOpsArtifact, snDevOpsPackage, and snDevOpsChange steps.
     * Expected: All steps use the default configuration name.
     */
    @Test
    public void testDefaultConfigUsedWhenNoConfigNameInSteps() throws Exception {
        DevOpsConfigurationEntry entry2 = addSecondConfiguration(true, true);

        // Both servers report pipelines as tracked
        mockServer.setDispatcher(createDispatcher(server1Requests, true));
        mockServer2.setDispatcher(createDispatcher(server2Requests, true));

        setupDualConfigTracking("TestJob_1", true, false, entry2);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("all-steps-no-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Using DevOps configuration DevOpsConfig1", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    // ============================================================================================
    // TC4: Repeat TC1-3 scenarios with scripted pipeline
    // ============================================================================================

    /**
     * Test ID: TNTC0356339 - TC4a
     * Scripted pipeline variant of TC1: Multiple configs, not tracked, no config name.
     */
    @Test
    public void testScriptedPipelineMultiConfigNotTracked() throws Exception {
        reconfigureDefaultEntry(false, false);
        DevOpsConfigurationEntry entry2 = addSecondConfiguration(false, false);

        mockServer.setDispatcher(createDispatcher(server1Requests, false));
        mockServer2.setDispatcher(createDispatcher(server2Requests, false));

        setupDualConfigTracking("TestJob_1", false, false, entry2);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-scripted-no-config.groovy"), false));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Pipeline is not tracked", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test ID: TNTC0356339 - TC4b
     * Scripted pipeline variant of TC2: Multiple configs, tracked, no config name.
     */
    @Test
    public void testScriptedPipelineMultiConfigTracked() throws Exception {
        reconfigureDefaultEntry(true, true);
        DevOpsConfigurationEntry entry2 = addSecondConfiguration(true, true);

        mockServer.setDispatcher(createDispatcher(server1Requests, true));
        mockServer2.setDispatcher(createDispatcher(server2Requests, true));

        setupDualConfigTracking("TestJob_1", true, true, entry2);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-scripted-no-config.groovy"), false));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Using DevOps configuration DevOpsConfig1", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    // ============================================================================================
    // TC5: Two configs, snDevOpsChange with non-default config name
    //      → Change request created for the non-default configuration instance
    // ============================================================================================

    /**
     * Test ID: TNTC0356339 - TC5
     * Two webhook configurations from different instances.
     * Pipeline uses snDevOpsChange with non-default configuration name.
     * Expected: Change request is created for the non-default configuration instance.
     */
    @Test
    public void testChangeStepWithNonDefaultConfigName() throws Exception {
        DevOpsConfigurationEntry entry2 = addSecondConfiguration(true, true);

        mockServer.setDispatcher(createDispatcher(server1Requests, true));
        mockServer2.setDispatcher(createDispatcher(server2Requests, true));

        setupSingleConfigTracking("TestJob_1", true, entry2);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-with-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Using DevOps configuration DevOpsConfig2", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    // ============================================================================================
    // TC6: Repeat TC5 for scripted pipeline with non-default config
    // ============================================================================================

    /**
     * Test ID: TNTC0356339 - TC6
     * Scripted pipeline variant of TC5: snDevOpsChange with non-default configuration name.
     * Expected: Change request created for the non-default configuration.
     */
    @Test
    public void testScriptedPipelineChangeStepWithNonDefaultConfig() throws Exception {
        DevOpsConfigurationEntry entry2 = addSecondConfiguration(true, true);

        mockServer.setDispatcher(createDispatcher(server1Requests, true));
        mockServer2.setDispatcher(createDispatcher(server2Requests, true));

        setupSingleConfigTracking("TestJob_1", true, entry2);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-scripted-with-config.groovy"), false));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Using DevOps configuration DevOpsConfig2", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    // ============================================================================================
    // TC7: Non-default config for snDevOpsArtifact, snDevOpsPackage, snDevOpsSecurityResult
    //      → Artifacts, packages, and security results created in non-default instance
    // ============================================================================================

    /**
     * Test ID: TNTC0356339 - TC7a
     * snDevOpsArtifact step with non-default configuration name.
     * Expected: Artifact is registered against the non-default configuration instance.
     */
    @Test
    public void testArtifactStepWithNonDefaultConfigName() throws Exception {
        DevOpsConfigurationEntry entry2 = addSecondConfiguration(true, true);

        mockServer.setDispatcher(createDispatcher(server1Requests, true));
        mockServer2.setDispatcher(createDispatcher(server2Requests, true));

        setupSingleConfigTracking("TestJob_1", true, entry2);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("artifact-with-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Using DevOps configuration DevOpsConfig2", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test ID: TNTC0356339 - TC7b
     * snDevOpsPackage step with non-default configuration name.
     * Expected: Package is created against the non-default configuration instance.
     */
    @Test
    public void testPackageStepWithNonDefaultConfigName() throws Exception {
        DevOpsConfigurationEntry entry2 = addSecondConfiguration(true, true);

        mockServer.setDispatcher(createDispatcher(server1Requests, true));
        mockServer2.setDispatcher(createDispatcher(server2Requests, true));

        setupSingleConfigTracking("TestJob_1", true, entry2);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("package-with-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Using DevOps configuration DevOpsConfig2", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }

    /**
     * Test ID: TNTC0356339 - TC7c
     * snDevOpsSecurityResult step with non-default configuration name.
     * Expected: Security results are registered against the non-default configuration instance.
     */
    @Test
    public void testSecurityResultStepWithNonDefaultConfigName() throws Exception {
        DevOpsConfigurationEntry entry2 = addSecondConfiguration(true, true);

        mockServer.setDispatcher(createDispatcher(server1Requests, true));
        mockServer2.setDispatcher(createDispatcher(server2Requests, true));

        setupSingleConfigTracking("TestJob_1", true, entry2);

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("security-with-config-name.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForMessage("[ServiceNow DevOps] Using DevOps configuration DevOpsConfig2", run);
        jenkins.assertBuildStatus(hudson.model.Result.SUCCESS, jenkins.waitForCompletion(run));
    }
}
