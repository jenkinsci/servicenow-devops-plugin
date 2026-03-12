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

public class DevOpsFreestyleCreateArtifactPackageStepTest extends BaseDevOpsTest {

    private volatile String packageRegistrationResponse = null;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        packageRegistrationResponse = null;
        mockServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();

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
     * Test: Successful artifact package creation in freestyle project.
     * Expected: Build succeeds.
     */
    @Test
    public void testPackageCreationSuccess() throws Exception {
        packageRegistrationResponse = "{\"result\": {\"status\": \"success\", \"response\": \"ok\"}}";

        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsCreateArtifactPackageBuildStep step = new DevOpsCreateArtifactPackageBuildStep();
        step.setName("TestPackage");
        step.setArtifactsPayload("{\"artifacts\": [{\"name\": \"test.jar\", \"version\": \"1.0\"}]}");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
        jenkins.assertLogContains("[ServiceNow DevOps] Create Artifact package payload:", build);
    }

    /**
     * Test: Artifact package creation with invalid JSON payload.
     * Expected: Exception caught in createArtifactPackage, returns null,
     * "FAILED: Artifact package could not be created." logged, AbortException thrown, build fails.
     */
    @Test
    public void testPackageCreationInvalidPayload() throws Exception {
        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsCreateArtifactPackageBuildStep step = new DevOpsCreateArtifactPackageBuildStep();
        step.setName("TestPackage");
        step.setArtifactsPayload("this-is-not-valid-json");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
        jenkins.assertLogContains("FAILED: Artifact package could not be created.", build);
    }

    /**
     * Test: Artifact package creation when server returns failure response.
     * Expected: "FAILED: Artifact package could not be created." logged, AbortException thrown, build fails.
     */
    @Test
    public void testPackageCreationServerFailure() throws Exception {
        packageRegistrationResponse = "{\"failureReason\": \"Package creation failed on server\"}";

        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsCreateArtifactPackageBuildStep step = new DevOpsCreateArtifactPackageBuildStep();
        step.setName("TestPackage");
        step.setArtifactsPayload("{\"artifacts\": [{\"name\": \"test.jar\", \"version\": \"1.0\"}]}");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
        jenkins.assertLogContains("FAILED: Artifact package could not be created.", build);
    }

    /**
     * Test: Artifact package creation when server returns failure but ignoreSNErrors is true on job property.
     * Expected: "IGNORED: Artifact package creation error ignored." logged, build succeeds.
     */
    @Test
    public void testPackageCreationServerFailureIgnoreErrors() throws Exception {
        packageRegistrationResponse = "{\"failureReason\": \"Package creation failed\"}";

        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsJobProperty jobProperty = new DevOpsJobProperty();
        jobProperty.setIgnoreSNErrors(true);
        project.addProperty(jobProperty);

        DevOpsCreateArtifactPackageBuildStep step = new DevOpsCreateArtifactPackageBuildStep();
        step.setName("TestPackage");
        step.setArtifactsPayload("{\"artifacts\": [{\"name\": \"test.jar\", \"version\": \"1.0\"}]}");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
        jenkins.assertLogContains("IGNORED: Artifact package creation error ignored.", build);
    }

    /**
     * Test: Artifact package creation when server returns null/no-match response.
     * Expected: createArtifactPackage returns null → "FAILED: Artifact package could not be created." logged, build fails.
     */
    @Test
    public void testPackageCreationNullResponse() throws Exception {
        // packageRegistrationResponse is null, so dispatcher returns default response
        // which doesn't have "response" key → parseResponseResult returns null → result is null → FAILED path

        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsCreateArtifactPackageBuildStep step = new DevOpsCreateArtifactPackageBuildStep();
        step.setName("TestPackage");
        step.setArtifactsPayload("{\"artifacts\": [{\"name\": \"test.jar\", \"version\": \"1.0\"}]}");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
        jenkins.assertLogContains("FAILED: Artifact package could not be created.", build);
    }
}
