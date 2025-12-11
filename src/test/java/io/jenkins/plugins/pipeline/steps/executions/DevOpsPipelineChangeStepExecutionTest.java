package io.jenkins.plugins.pipeline.steps.executions;

import io.jenkins.plugins.BaseDevOpsTest;
import okhttp3.mockwebserver.MockResponse;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Ignore;
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

    @Ignore("Disabling until assertion format is fixed")
    @Test
    public void tesDevOpsChangeStepInPipeline() throws Exception {
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\", \"changeControl\": \"true\", \"message\": \"Change request registered\"}}");
        enqueueSuccessResponse("{\"result\": {\"status\": \"success\"}}");

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "    agent any\n" +
                        "    stages {\n" +
                        "        stage('Test') {\n" +
                        "            steps {\n" +
                        "                snDevOpsChange(\n" +
                        "                    changeRequestDetails: '''{\n" +
                        "                        \"attributes\": {\n" +
                        "                            \"short_description\": \"Test description\",\n" +
                        "                            \"priority\": \"1\",\n" +
                        "                            \"start_date\": \"2021-02-05 08:00:00\",\n" +
                        "                            \"end_date\": \"2022-04-05 08:00:00\",\n" +
                        "                            \"justification\": \"test justification\",\n" +
                        "                            \"description\": \"test description\",\n" +
                        "                            \"cab_required\": true,\n" +
                        "                            \"comments\": \"This update for work notes is from jenkins file\",\n" +
                        "                            \"work_notes\": \"test work notes\",\n" +
                        "                            \"assignment_group\": \"a715cd759f2002002920bde8132e7018\"\n" +
                        "                        },\n" +
                        "                        \"setCloseCode\": false,\n" +
                        "                        \"autoCloseChange\": true\n" +
                        "                    }'''\n" +
                        "                )\n" +
                        "            }\n" +
                        "        }\n" +
                        "    }\n" +
                        "}",
                true
        ));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        Thread.sleep(2000);
        jenkins.assertLogContains("[ServiceNow DevOps] Job is under change control", run);
    }

    @Test
    public void tesDevOpsChangeStepWithInvalidChangeRequestPayload() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "TestJob");
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "    agent any\n" +
                        "    stages {\n" +
                        "        stage('Test') {\n" +
                        "            steps {\n" +
                        "                snDevOpsChange(\n" +
                        "                    changeRequestDetails: '''{\n" +
                        "                        \"attributes\": {\n" +
                        "                            \"state\": \"Test description\"\n" +
                        "                            \"priority\": \"1\"\n" +
                        "                    }'''\n" +
                        "                )\n" +
                        "            }\n" +
                        "        }\n" +
                        "    }\n" +
                        "}",
                true
        ));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        Thread.sleep(2000);
        jenkins.assertLogContains("[ServiceNow DevOps] {\"failureReason\":\"Failed to parse changeRequestDetails json.Expected a ", run);
    }

    @Test
    public void tesDevOpsChangeStepWithInvalidChangeRequestFields() throws Exception {
        // Enqueue error response for invalid change request fields
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
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" +
                        "    agent any\n" +
                        "    stages {\n" +
                        "        stage('Test') {\n" +
                        "            steps {\n" +
                        "                snDevOpsChange(\n" +
                        "                    changeRequestDetails: '''{\n" +
                        "                        \"attributes\": {\n" +
                        "                            \"state\": \"Approved\",\n" +
                        "                            \"watch_list\": \"1\"\n" +
                        "                        }\n" +
                        "                    }'''\n" +
                        "                )\n" +
                        "            }\n" +
                        "        }\n" +
                        "    }\n" +
                        "}",
                true
        ));
        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        Thread.sleep(2000);
        jenkins.assertLogContains("[ServiceNow DevOps] {\"failureReason\":\"Invalid Change Request Attributes found in params [watch_list]\"}", run);
    }
}
