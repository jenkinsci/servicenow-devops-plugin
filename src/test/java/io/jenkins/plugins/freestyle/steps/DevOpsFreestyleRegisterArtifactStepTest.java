package io.jenkins.plugins.freestyle.steps;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import io.jenkins.plugins.BaseDevOpsTest;
import io.jenkins.plugins.config.DevOpsJobProperty;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Test;

public class DevOpsFreestyleRegisterArtifactStepTest extends BaseDevOpsTest {

    private volatile String artifactRegistrationResponse = null;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        artifactRegistrationResponse = null;
        mockServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();

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
     * Test: Successful artifact registration in freestyle project.
     * Expected: Build succeeds.
     */
    @Test
    public void testArtifactRegistrationSuccess() throws Exception {
        artifactRegistrationResponse = "{\"result\": {\"status\": \"success\", \"response\": \"ok\"}}";

        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsRegisterArtifactBuildStep step = new DevOpsRegisterArtifactBuildStep();
        step.setArtifactsPayload("{\"artifacts\": [{\"name\": \"test.jar\", \"version\": \"1.0\"}]}");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
        jenkins.assertLogContains("[ServiceNow DevOps] Register artifact payload:", build);
    }

    /**
     * Test: Artifact registration with invalid JSON payload.
     * Expected: Exception caught in registerArtifact, returns null, "FAILED: Artifact could not be registered." logged,
     * AbortException thrown, build fails.
     */
    @Test
    public void testArtifactRegistrationInvalidPayload() throws Exception {
        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsRegisterArtifactBuildStep step = new DevOpsRegisterArtifactBuildStep();
        step.setArtifactsPayload("this-is-not-valid-json");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
        jenkins.assertLogContains("FAILED: Artifact could not be registered.", build);
    }

    /**
     * Test: Artifact registration when server returns a failure response.
     * Expected: "FAILED: Artifact could not be registered." logged, AbortException thrown, build fails.
     */
    @Test
    public void testArtifactRegistrationServerFailure() throws Exception {
        artifactRegistrationResponse = "{\"failureReason\": \"Artifact registration failed on server\"}";

        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsRegisterArtifactBuildStep step = new DevOpsRegisterArtifactBuildStep();
        step.setArtifactsPayload("{\"artifacts\": [{\"name\": \"test.jar\", \"version\": \"1.0\"}]}");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
        jenkins.assertLogContains("FAILED: Artifact could not be registered.", build);
    }

    /**
     * Test: Artifact registration when server returns failure but ignoreSNErrors is true on job property.
     * Expected: "IGNORED: Artifact registration error ignored." logged, build succeeds.
     */
    @Test
    public void testArtifactRegistrationServerFailureIgnoreErrors() throws Exception {
        artifactRegistrationResponse = "{\"failureReason\": \"Artifact registration failed\"}";

        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsJobProperty jobProperty = new DevOpsJobProperty();
        jobProperty.setIgnoreSNErrors(true);
        project.addProperty(jobProperty);

        DevOpsRegisterArtifactBuildStep step = new DevOpsRegisterArtifactBuildStep();
        step.setArtifactsPayload("{\"artifacts\": [{\"name\": \"test.jar\", \"version\": \"1.0\"}]}");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
        jenkins.assertLogContains("IGNORED: Artifact registration error ignored.", build);
    }

    /**
     * Test: Artifact registration when server returns null response (no response body matched).
     * Expected: registerArtifact returns null → "FAILED: Artifact could not be registered." logged, build fails.
     */
    @Test
    public void testArtifactRegistrationNullResponse() throws Exception {
        // artifactRegistrationResponse is null, so dispatcher returns default response
        // which doesn't have "response" key → parseResponseResult returns null → result is null → FAILED path

        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsRegisterArtifactBuildStep step = new DevOpsRegisterArtifactBuildStep();
        step.setArtifactsPayload("{\"artifacts\": [{\"name\": \"test.jar\", \"version\": \"1.0\"}]}");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
        jenkins.assertLogContains("FAILED: Artifact could not be registered.", build);
    }
}
