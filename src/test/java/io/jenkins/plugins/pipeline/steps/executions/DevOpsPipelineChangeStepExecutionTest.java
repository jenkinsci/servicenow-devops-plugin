package io.jenkins.plugins.pipeline.steps.executions;

import io.jenkins.plugins.BaseDevOpsTest;
import io.jenkins.plugins.DevOpsRootAction;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.model.DevOpsPipelineInfoConfig;
import okhttp3.mockwebserver.MockResponse;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Test;

public class DevOpsPipelineChangeStepExecutionTest extends BaseDevOpsTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mockServer.enqueue(new MockResponse()
                .setBody("{\"result\": {\"track\": true}}")
                .setHeader("Content-Type", "application/json")
                .setResponseCode(200));

        mockServer.enqueue(new MockResponse()
                .setBody("{\"result\": {\"changeControl\": \"true\"}}")
                .setHeader("Content-Type", "application/json")
                .setResponseCode(200));

    }

    @Test
    public void tesDevOpsChangeStepInPipeline() throws Exception {
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\", \"changeControl\": \"true\", \"message\": \"Change request registered\"}}");
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\"}}");

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-valid.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        Thread.sleep(2000);
        jenkins.assertLogContains("[ServiceNow DevOps] Job is under change control", run);
    }

    @Test
    public void tesDevOpsChangeStepWithInvalidChangeRequestPayload() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-invalid-payload.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        Thread.sleep(2000);
        jenkins.assertLogContains("[ServiceNow DevOps] {\"failureReason\":\"Failed to parse changeRequestDetails json.Expected a ", run);
    }

    @Test
    public void tesDevOpsChangeStepWithInvalidChangeRequestFields() throws Exception {
        enqueueSuccessResponse("{\n" +
                "  \"result\": {\n" +
                "    \"status\": \"error\",\n" +
                "    \"details\": {\n" +
                "      \"errors\": [\n" +
                "        {\n" +
                "          \"message\": \"Invalid Change Request Attributes found in params [watch_list]\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}");

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-invalid-fields.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        Thread.sleep(2000);
        jenkins.assertLogContains("[ServiceNow DevOps] {\"failureReason\":\"Invalid Change Request Attributes found in params [watch_list]\"}", run);
    }

    @Test
    public void testDevOpsGetChangeStep() throws Exception {
        DevOpsRootAction.setTrackedJob("GetChangeJob_1");
        DevOpsPipelineInfoConfig config = new DevOpsPipelineInfoConfig(
                true,
                devOpsConfiguration.getEntries().get(0),
                "1234_" + mockServerUrl
        );
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = new DevOpsModel.DevOpsPipelineInfo(java.util.Arrays.asList(config));
        DevOpsRootAction.setSnPipelineInfo("GetChangeJob_1", pipelineInfo);

        enqueueSuccessResponse("{  \"result\": {  \"number\": \"CHG0001234\"  } }");

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "GetChangeJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("get-change-number.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        Thread.sleep(2000);
        jenkins.assertLogContains("[ServiceNow DevOps] snDevOpsGetChangeNumber,  'Change Request Number' => CHG0001234", run);
    }

    /**
     * Test ID: TNTC0149545
     * Verifies that when a change request is cancelled during polling/callback,
     * the pipeline properly handles the cancellation and logs appropriate messages.
     */
    @Test
    public void testDevOpsChangeStepWhenChangeCancelled() throws Exception {
        // Third response: Change request creation with pending status - token will be generated dynamically
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\", \"changeControl\": \"true\", \"message\": \"Change request registered\", \"result\": \"pending\", \"token\": \"pipeline_dynamic-token\"}}");
        // Fourth response: For sendBuildAndToken call after callback
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\"}}");

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(loadPipelineScript("change-step-valid.groovy"), true));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        
        // Set up tracking info for the job so callback can find pipeline info
        DevOpsRootAction.setTrackedJob("TestJob_1");
        DevOpsPipelineInfoConfig config = new DevOpsPipelineInfoConfig(
                true,
                devOpsConfiguration.getEntries().get(0),
                "1234_" + mockServerUrl
        );
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = new DevOpsModel.DevOpsPipelineInfo(java.util.Arrays.asList(config));
        DevOpsRootAction.setSnPipelineInfo("TestJob_1", pipelineInfo);
        
        // Wait for pipeline to process change control and register webhook
        Thread.sleep(3000);
        jenkins.assertLogContains("[ServiceNow DevOps] Job is under change control", run);
        jenkins.assertLogContains("[ServiceNow DevOps] Change request registered", run);
        
        // Extract the actual token from the logs
        String log = run.getLog();
        String token = null;
        String searchPattern = "register callback hook successful with token: ";
        int tokenIndex = log.indexOf(searchPattern);
        if (tokenIndex != -1) {
            int tokenStart = tokenIndex + searchPattern.length();
            int tokenEnd = log.indexOf("\n", tokenStart);
            token = log.substring(tokenStart, tokenEnd).trim();
        }

        if (token == null) {
            throw new AssertionError("Could not extract token from logs");
        }
        
        // Simulate ServiceNow callback with cancellation
        String callbackUrl = jenkins.getURL().toString() + "sn-devops/" + token;
        String callbackPayload = "{\"result\": \"canceled\", \"changeComments\": \"Change request was cancelled by user\"}";
        
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(callbackUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Content-Type", "application/json");
        try (java.io.OutputStream os = connection.getOutputStream()) {
            os.write(callbackPayload.getBytes("UTF-8"));
            os.flush();
        }
        
        int responseCode = connection.getResponseCode();
        connection.disconnect();
        Thread.sleep(2000);
        jenkins.assertLogContains("[ServiceNow DevOps] Job was canceled", run);
        jenkins.assertLogContains("Cancel comments:", run);
    }
}
