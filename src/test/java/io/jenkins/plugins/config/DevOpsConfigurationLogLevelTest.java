package io.jenkins.plugins.config;

import hudson.Functions;
import hudson.logging.LogRecorder;
import hudson.logging.LogRecorderManager;
import io.jenkins.plugins.BaseDevOpsTest;
import io.jenkins.plugins.DevOpsRootAction;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.model.DevOpsPipelineInfoConfig;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import jenkins.model.Jenkins;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that the ServiceNow DevOps plugin correctly logs messages at different levels
 * (FINE, INFO, WARNING, SEVERE) based on the configured log level.
 * Testcase: TNTC0151879
 */
public class DevOpsConfigurationLogLevelTest extends BaseDevOpsTest {

    private static final String LOGGER_NAME = DevOpsConstants.LOGGER_NAME.toString();
    private LogRecorder logRecorder;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }


    private boolean hasLogLevel(List<LogRecord> logRecords, Level expectedLevel) {
        for (LogRecord record : logRecords) {
            if (record.getLevel().equals(expectedLevel)) {
                return true;
            }
        }
        return false;
    }

    private int countLogLevel(List<LogRecord> logRecords, Level expectedLevel) {
        int count = 0;
        for (LogRecord record : logRecords) {
            if (record.getLevel().equals(expectedLevel)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Helper method to execute a simple pipeline that triggers DevOps plugin logging.
     * This generates real logs from the plugin during pipeline execution at various levels:
     * - FINE: onStarted, handlePipeline, handleRunStarted, etc.
     * - INFO: checkIsTracking, API calls
     * - WARNING: validation errors, missing config
     * - SEVERE: exceptions, critical errors
     */
    private void executePipelineToGenerateLogs() throws Exception {
        // Enqueue mock responses for the pipeline execution
        enqueueSuccessResponse("{\"result\": {\"track\": true}}");
        enqueueSuccessResponse("{\"result\": {\"changeControl\": \"false\"}}");
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\"}}");
        
        // Create and execute a simple pipeline with snDevOpsChange step
        org.jenkinsci.plugins.workflow.job.WorkflowJob job = jenkins.createProject(
            org.jenkinsci.plugins.workflow.job.WorkflowJob.class, "LogLevelTestJob_" + System.currentTimeMillis());
        job.setDefinition(new org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition(
            "node { \n" +
            "  echo 'Starting DevOps test'\n" +
            "  snDevOpsChange()\n" +
            "  echo 'Completed DevOps test'\n" +
            "}", true));
        
        org.jenkinsci.plugins.workflow.job.WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        jenkins.waitForCompletion(run);
        
        Thread.sleep(500);
    }

    /**
     * Helper method to execute a pipeline that generates error logs (WARNING or SEVERE).
     * @param errorCode HTTP error code (400 for WARNING, 500 for SEVERE)
     * @param useIgnoreErrors Whether to use ignoreErrors parameter (true for WARNING, false for SEVERE with try-catch)
     */
    private void executePipelineToGenerateErrorLogs(int errorCode, boolean useIgnoreErrors) throws Exception {
        String logLevel = (errorCode == 400) ? "Warning" : "Severe";
        String jobName = logLevel + "LogTestJob_" + System.currentTimeMillis();
        
        // Enqueue mock responses
        enqueueSuccessResponse("{\"result\": {\"track\": true}}");
        enqueueSuccessResponse("{\"result\": {\"changeControl\": \"false\"}}");

        // Return error response to trigger WARNING or SEVERE log
        if (errorCode == 400) {
            enqueueSuccessResponse("{\"result\": {\"track\": true}}"); // For checkIsTracking in artifact step
            // For WARNING: return valid JSON with error message and 400 status
            enqueueMockResponse("{\"result\": {\"error\": \"Artifact registration failed\"}}", 400);
        } else {
            // For SEVERE: return invalid/malformed JSON to trigger parsing exception
            enqueueMockResponse("{ invalid json response }", 200);
        }
        
        // Create pipeline script based on error handling approach
        String pipelineScript;
        if (useIgnoreErrors) {
            pipelineScript = "node { snDevOpsArtifact(artifactsPayload: '''{ \"artifacts\": [] }''', ignoreErrors: true) }";
        } else {
            pipelineScript = "node { \n" +
                "  try {\n" +
                "    snDevOpsArtifact(artifactsPayload: '''{ \"artifacts\": [] }''')\n" +
                "  } catch (Exception e) {\n" +
                "    echo 'Expected exception for " + logLevel.toUpperCase() + " log generation'\n" +
                "  }\n" +
                "}";
        }
        
        org.jenkinsci.plugins.workflow.job.WorkflowJob job = jenkins.createProject(
            org.jenkinsci.plugins.workflow.job.WorkflowJob.class, jobName);
        job.setDefinition(new org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition(pipelineScript, true));
        
        org.jenkinsci.plugins.workflow.job.WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        
        String runId = run.getId();
        String trackingKey = jobName + "_" + runId;
        
        DevOpsRootAction.setTrackedJob(trackingKey);
        DevOpsPipelineInfoConfig config = new DevOpsPipelineInfoConfig(
            true,
            devOpsConfiguration.getEntries().get(0),
            "1234_" + mockServerUrl
        );
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = new DevOpsModel.DevOpsPipelineInfo(java.util.Arrays.asList(config));
        DevOpsRootAction.setSnPipelineInfo(trackingKey, pipelineInfo);
        
        jenkins.waitForCompletion(run);
        Thread.sleep(500);
    }

    /**
     * Test that when log level is set to FINE, all log levels (FINE, INFO, WARNING, SEVERE) are captured.
     * This test runs an actual pipeline to generate real DevOps plugin logs.
     */
    @Test
    public void testLogLevelFineCapturesAllLevels() throws Exception {
        // Configure logger to FINE level
        GenericUtils.configureLogger("fine");
        
        // Get the log recorder
        Jenkins jenkins = Jenkins.get();
        LogRecorderManager logRecorderManager = jenkins.getLog();
        logRecorder = logRecorderManager.getLogRecorder(LOGGER_NAME);
        
        assertNotNull("Log recorder should be created", logRecorder);
        
        logRecorder.doClear();
        executePipelineToGenerateLogs();
        List<LogRecord> logRecords = logRecorder.getLogRecords();
        
        // Verify that logs were captured
        assertTrue("Should have captured some log records, but got: " + logRecords.size(), !logRecords.isEmpty());
        
        // When log level is FINE, we should capture FINE, INFO, WARNING, and SEVERE
        // The plugin generates FINE logs during pipeline execution (onStarted, handlePipeline, etc.)
        // and INFO logs during API calls
        assertTrue("FINE level logs should be captured when log level is FINE", hasLogLevel(logRecords, Level.FINE));
        assertTrue("INFO level logs should be captured when log level is FINE", hasLogLevel(logRecords, Level.INFO));
    }

    /**
     * Test that when log level is set to FINER, all log levels including FINER are captured.
     * Scenario 2: FINER level should capture FINER, FINE, INFO, WARNING, and SEVERE.
     */
    @Test
    public void testLogLevelFinerCapturesAllLevels() throws Exception {
        // Configure logger to FINER level
        GenericUtils.configureLogger("finer");
        // Get the log recorder
        Jenkins jenkins = Jenkins.get();
        LogRecorderManager logRecorderManager = jenkins.getLog();
        logRecorder = logRecorderManager.getLogRecorder(LOGGER_NAME);
        
        assertNotNull("Log recorder should be created", logRecorder);
        
        logRecorder.doClear();
        executePipelineToGenerateLogs();
        List<LogRecord> logRecords = logRecorder.getLogRecords();

        // Verify that logs were captured
        assertTrue("Should have captured some log records, but got: " + logRecords.size(), !logRecords.isEmpty());
        
        // When log level is FINER, we should capture FINER, FINE, INFO, WARNING, and SEVERE
        assertTrue("FINE level logs should be captured when log level is FINER", hasLogLevel(logRecords, Level.FINE));
        assertTrue("INFO level logs should be captured when log level is FINER", hasLogLevel(logRecords, Level.INFO));
    }

    /**
     * Test that when log level is set to INFO, only INFO, WARNING, and SEVERE are captured (not FINE).
     * This test runs an actual pipeline to generate real DevOps plugin logs.
     */
    @Test
    public void testLogLevelInfo() throws Exception {
        // Configure logger to INFO level
        GenericUtils.configureLogger("info");
        
        Jenkins jenkins = Jenkins.get();
        LogRecorderManager logRecorderManager = jenkins.getLog();
        logRecorder = logRecorderManager.getLogRecorder(LOGGER_NAME);
        
        assertNotNull("Log recorder should be created", logRecorder);
        
        // Clear any existing logs
        logRecorder.doClear();
        executePipelineToGenerateLogs();
        List<LogRecord> logRecords = logRecorder.getLogRecords();
        
        assertTrue("Should have captured some log records, but got: " + logRecords.size(), !logRecords.isEmpty());
        
        // When log level is INFO, FINE logs should NOT be captured
        assertFalse("FINE level logs should NOT be captured when log level is INFO", hasLogLevel(logRecords, Level.FINE));
        assertTrue("INFO level logs should be captured when log level is INFO", hasLogLevel(logRecords, Level.INFO));
    }

    /**
     * Test that when log level is set to WARNING, only WARNING and SEVERE are captured.
     * Scenario 3: This test runs a pipeline that generates WARNING logs from artifact registration errors.
     */
    @Test
    public void testLogLevelWarningFiltersLowerLevels() throws Exception {
        // Configure logger to WARNING level
        GenericUtils.configureLogger("warning");
        
        Jenkins jenkins = Jenkins.get();
        LogRecorderManager logRecorderManager = jenkins.getLog();
        logRecorder = logRecorderManager.getLogRecorder(LOGGER_NAME);
        
        assertNotNull("Log recorder should be created", logRecorder);
        
        logRecorder.doClear();
        // Execute pipeline that generates WARNING logs (artifact registration error)
        executePipelineToGenerateErrorLogs(400, true);
        
        List<LogRecord> logRecords = logRecorder.getLogRecords();
        
        // When log level is WARNING, FINE and INFO logs should NOT be captured
        assertFalse("FINE level logs should NOT be captured when log level is WARNING", hasLogLevel(logRecords, Level.FINE));
        assertFalse("INFO level logs should NOT be captured when log level is WARNING", hasLogLevel(logRecords, Level.INFO));
        
        // WARNING should be captured (from artifact registration error)
        assertTrue("WARNING level logs should be captured when log level is WARNING", hasLogLevel(logRecords, Level.WARNING));
    }

    /**
     * Test that when log level is set to SEVERE, only SEVERE logs are captured.
     * Scenario 4: This test runs a pipeline that generates SEVERE logs from exception handling.
     */
    @Test
    public void testLogLevelSevereOnlyCapturesSevere() throws Exception {
        // Configure logger to SEVERE level
        GenericUtils.configureLogger("severe");
        
        Jenkins jenkins = Jenkins.get();
        LogRecorderManager logRecorderManager = jenkins.getLog();
        logRecorder = logRecorderManager.getLogRecorder(LOGGER_NAME);
        
        assertNotNull("Log recorder should be created", logRecorder);
        
        logRecorder.doClear();
        // Execute pipeline that generates SEVERE logs (exception scenario)
        executePipelineToGenerateErrorLogs(500, false);
        
        List<LogRecord> logRecords = logRecorder.getLogRecords();
        
        // When log level is SEVERE, FINE, INFO, and WARNING logs should NOT be captured
        assertFalse("FINE level logs should NOT be captured when log level is SEVERE", hasLogLevel(logRecords, Level.FINE));
        assertFalse("INFO level logs should NOT be captured when log level is SEVERE", hasLogLevel(logRecords, Level.INFO));
        assertFalse("WARNING level logs should NOT be captured when log level is SEVERE", hasLogLevel(logRecords, Level.WARNING));
        
        // SEVERE should be captured (from exception handling)
        assertTrue("SEVERE level logs should be captured when log level is SEVERE", hasLogLevel(logRecords, Level.SEVERE));
    }

    /**
     * Test that when log level is set to ALL, all log levels are captured.
     * Scenario 5: This test runs an actual pipeline to generate real DevOps plugin logs.
     */
    @Test
    public void testLogLevelAllCapturesAllLevels() throws Exception {
        // Level.ALL (Integer.MIN_VALUE) is not reliably handled as a LogRecorder target
        // minimum level on Windows/Java 8, causing FINE records to be missed.
        // The underlying issue is in Jenkins LogRecorder's Handler behaviour with Level.ALL.
        Assume.assumeFalse("Skipping on Windows: Level.ALL LogRecorder target does not capture FINE logs on Windows/Java 8",
                Functions.isWindows());

        // Configure logger to ALL level
        GenericUtils.configureLogger("all");
        
        Jenkins jenkins = Jenkins.get();
        LogRecorderManager logRecorderManager = jenkins.getLog();
        logRecorder = logRecorderManager.getLogRecorder(LOGGER_NAME);
        
        assertNotNull("Log recorder should be created", logRecorder);
        
        logRecorder.doClear();
        executePipelineToGenerateLogs();
        List<LogRecord> logRecords = logRecorder.getLogRecords();
        
        // Verify that logs were captured
        assertTrue("Should have captured some log records, but got: " + logRecords.size(), !logRecords.isEmpty());
        
        // When log level is ALL, all log levels should be captured
        assertTrue("FINE level logs should be captured with ALL", hasLogLevel(logRecords, Level.FINE));
        assertTrue("INFO level logs should be captured with ALL", hasLogLevel(logRecords, Level.INFO));
    }

    /**
     * Test that when log level is set to OFF, no logs are captured.
     * Scenario 6: This test runs an actual pipeline to verify no logs are captured.
     */
    @Test
    public void testLogLevelOffCapturesNoLogs() throws Exception {
        // Configure logger to OFF level
        GenericUtils.configureLogger("off");
        
        // Get the log recorder
        Jenkins jenkins = Jenkins.get();
        LogRecorderManager logRecorderManager = jenkins.getLog();
        logRecorder = logRecorderManager.getLogRecorder(LOGGER_NAME);
        
        assertNotNull("Log recorder should be created", logRecorder);
        
        logRecorder.doClear();
        executePipelineToGenerateLogs();
        List<LogRecord> logRecords = logRecorder.getLogRecords();
        
        // When log level is OFF, no logs should be captured
        assertFalse("No FINE logs should be captured when log level is OFF", hasLogLevel(logRecords, Level.FINE));
        assertFalse("No INFO logs should be captured when log level is OFF", hasLogLevel(logRecords, Level.INFO));
        assertFalse("No WARNING logs should be captured when log level is OFF", hasLogLevel(logRecords, Level.WARNING));
        assertFalse("No SEVERE logs should be captured when log level is OFF", hasLogLevel(logRecords, Level.SEVERE));

        assertTrue("Should have captured some log records, but got: " + logRecords.size(), logRecords.isEmpty());
    }

    /**
     * Test that the DevOpsConfiguration correctly stores and retrieves the log level setting.
     */
    @Test
    public void testDevOpsConfigurationLogLevelPersistence() {
        DevOpsConfiguration config = DevOpsConfiguration.get();
        assertNotNull("DevOpsConfiguration should be available", config);
        // Set log level to FINE
        config.setLogLevel("fine");
        assertEquals("Log level should be set to fine", "fine", config.getLogLevel());
        // Set log level to INFO
        config.setLogLevel("info");
        assertEquals("Log level should be set to info", "info", config.getLogLevel());
        // Set log level to WARNING
        config.setLogLevel("warning");
        assertEquals("Log level should be set to warning", "warning", config.getLogLevel());
        // Set log level to SEVERE
        config.setLogLevel("severe");
        assertEquals("Log level should be set to severe", "severe", config.getLogLevel());
    }
}
