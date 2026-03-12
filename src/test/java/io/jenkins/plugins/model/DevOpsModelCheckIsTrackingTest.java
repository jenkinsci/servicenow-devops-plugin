package io.jenkins.plugins.model;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import io.jenkins.plugins.BaseDevOpsTest;
import io.jenkins.plugins.DevOpsRootAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Integration tests for {@link DevOpsModel#checkIsTracking} using a live Jenkins
 * instance ({@link org.jvnet.hudson.test.JenkinsRule}) and a MockWebServer that
 * stands in for the ServiceNow DevOps API.
 *
 * <p>Follows the same style as
 * {@link io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineChangeStepExecutionTest}:
 * real Jenkins jobs are created and run, mock HTTP responses are enqueued on the
 * MockWebServer, and assertions are made against build logs and
 * {@link DevOpsRootAction#getSnPipelineInfo}.
 */
public class DevOpsModelCheckIsTrackingTest extends BaseDevOpsTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // Clear the pre-seeded pipeline info so checkIsTracking always hits the
        // tracking endpoint (making the mock HTTP response the source of truth).
        DevOpsRootAction.removeTrackedJob("TestJob_1");
        DevOpsRootAction.removeSnPipelineInfo("TestJob_1");
    }

    // -------------------------------------------------------------------------
    // Scripted Pipeline (pronoun = "Pipeline")
    // -------------------------------------------------------------------------

    @Test
    public void testScriptedPipelineTracked() throws Exception {
        // HTTP sequence (Pipeline pronoun → queue dispatcher skips, no queue-phase HTTP calls):
        // 1. onStarted checkIsTracking → tracking endpoint
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\", \"track\": true}}");
        // 2. onStarted handleRunStarted → run-started notification
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\"}}");
        // 3. DevOpsStageListener.onNewHead → stage-start notification
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\"}}");
        // 4. DevOpsStageListener.onNewHead → stage-end notification
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\"}}");
        // 5. onCompleted handleRunCompleted → run-completed notification
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\"}}");

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(
                "node { stage('Test') { echo 'scripted pipeline tracked' } }", true));
        WorkflowRun run = jenkins.waitForCompletion(job.scheduleBuild2(0).waitForStart());

        jenkins.assertBuildStatus(Result.SUCCESS, run);
        assertEquals("Expected 5 HTTP calls for tracked scripted pipeline (tracking, run-started, stage-start, stage-end, run-completed)",
                5, mockServer.getRequestCount());
    }

    @Test
    public void testScriptedPipelineNotTracked() throws Exception {
        // HTTP sequence (track=false → no notifications or stage listener, but
        // checkIsTracking is called 3 times: onStarted, snDevOpsChange, onCompleted):
        // 1. onStarted checkIsTracking → tracking endpoint
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\", \"track\": false}}");
        // 2. snDevOpsChange checkIsTracking → tracking endpoint (non-tracked jobs are not
        //    added to the tracking cache, so each call hits the endpoint)
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\", \"track\": false}}");
        // 3. onCompleted checkIsTracking → tracking endpoint
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\", \"track\": false}}");

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(
                "node { stage('Test') { snDevOpsChange() } }", true));
        WorkflowRun run = jenkins.waitForCompletion(job.scheduleBuild2(0).waitForStart());

        jenkins.assertBuildStatus(Result.SUCCESS, run);
        jenkins.assertLogContains("[ServiceNow DevOps] Pipeline is not tracked", run);
        assertEquals("Expected 3 HTTP calls for non-tracked scripted pipeline (onStarted, snDevOpsChange, onCompleted)",
                3, mockServer.getRequestCount());
    }

    // -------------------------------------------------------------------------
    // FreeStyle Job (pronoun = "Project")
    // -------------------------------------------------------------------------

    @Test
    public void testFreestyleJobTracked() throws Exception {
        // HTTP sequence (Freestyle → DevOpsQueueTaskDispatcher.canRun() fires before onStarted):
        // 1. canRun → handleFreestyle → checkIsTracking(item) → tracking endpoint
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\", \"track\": true}}");
        // 2. canRun → handleFreestyle → sendIsUnderChgControl → changeControl=false (not blocked)
        enqueueSuccessResponse("{\"result\": {\"changeControl\": \"false\"}}");
        // 3. onStarted checkIsTracking(job, runId) → tracking endpoint (cache miss: no runId from queue call)
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\", \"track\": true}}");
        // 4. onStarted handleRunStarted → run-started notification
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\"}}");
        // 5. onCompleted handleRunCompleted → run-completed notification
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\"}}");

        FreeStyleProject job = jenkins.createProject(FreeStyleProject.class, "TestJob");
        FreeStyleBuild build = jenkins.buildAndAssertSuccess(job);

        assertNotNull("Build must have completed", build);
        assertEquals("Expected 5 HTTP calls for tracked freestyle job (2x checkIsTracking, changeControl check, run-started, run-completed)",
                5, mockServer.getRequestCount());
    }

    @Test
    public void testFreeStyleJobNotTracked() throws Exception {
        // HTTP sequence (Freestyle → DevOpsQueueTaskDispatcher.canRun() fires before onStarted):
        // 1. canRun → handleFreestyle → checkIsTracking(item) → tracking endpoint
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\", \"track\": false}}");
        // 2. canRun → handleFreestyle → sendIsUnderChgControl → changeControl=false (not blocked)
        enqueueSuccessResponse("{\"result\": {\"changeControl\": \"false\"}}");
        // 3. onStarted checkIsTracking(job, runId) → tracking endpoint (cache miss: no runId from queue call)
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\", \"track\": false}}");
        // 4. onCompleted checkIsTracking → tracking endpoint (non-tracked jobs are not added
        //    to the tracking cache, so onCompleted also hits the endpoint)
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\", \"track\": false}}");

        FreeStyleProject job = jenkins.createProject(FreeStyleProject.class, "TestJob");
        FreeStyleBuild build = jenkins.buildAndAssertSuccess(job);

        assertNotNull("Build must have completed", build);
        assertEquals("Expected 4 HTTP calls for non-tracked freestyle job (2x checkIsTracking, changeControl check, onCompleted checkIsTracking)",
                4, mockServer.getRequestCount());
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    public void testCheckIsTracking_DevOpsConfigDisabled_PipelineRunsNormally() throws Exception {
        devOpsConfiguration.setEntries(null);
        devOpsConfiguration.save();

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "UnconfiguredJob");
        job.setDefinition(new CpsFlowDefinition("node { echo 'no devops config' }", true));
        jenkins.assertBuildStatus(Result.SUCCESS,jenkins.waitForCompletion(job.scheduleBuild2(0).waitForStart()));
        assertEquals("No HTTP calls should be made when DevOps config is disabled", 0, mockServer.getRequestCount());
    }

    @Test
    public void testTrackingWhenInvalidCredentialsAndPipelineNotTracked() throws Exception {
        // track value contains failureReason → getPipelineInfoConfig sets isUnreachable=true,
        // isTrack=false.  snDevOpsChange(ignoreErrors:true) logs the contactability warning and
        // succeeds rather than failing the build.
        // checkIsTracking is called 3 times: onStarted, snDevOpsChange, onCompleted.
        enqueueSuccessResponse(
                "{\"result\": {\"status\": \"success\", \"track\": \"failureReason: User Not Authenticated\"}}");
        enqueueSuccessResponse(
                "{\"result\": {\"status\": \"success\", \"track\": \"failureReason: User Not Authenticated\"}}");
        enqueueSuccessResponse(
                "{\"result\": {\"status\": \"success\", \"track\": \"failureReason: User Not Authenticated\"}}");

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(
                "node { stage('Test') { snDevOpsChange(ignoreErrors: true) } }", true));
        WorkflowRun run = jenkins.waitForCompletion(job.scheduleBuild2(0).waitForStart());

        jenkins.assertBuildStatus(Result.SUCCESS, run);
        jenkins.assertLogContains("[ServiceNow DevOps] ServiceNow instance not contactable, but will ignore", run);
        assertEquals("Expected 3 HTTP calls for invalid credentials pipeline (onStarted, snDevOpsChange, onCompleted)",
                3, mockServer.getRequestCount());
    }
}
