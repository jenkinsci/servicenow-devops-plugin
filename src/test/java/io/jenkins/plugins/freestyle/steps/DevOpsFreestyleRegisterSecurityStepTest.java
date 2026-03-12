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

public class DevOpsFreestyleRegisterSecurityStepTest extends BaseDevOpsTest {

    private volatile String securityRegistrationResponse = null;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        securityRegistrationResponse = null;
        mockServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();

                if (path != null && path.contains("/tool/security") && securityRegistrationResponse != null) {
                    return jsonResponse(securityRegistrationResponse);
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
     * Test: Successful security result registration in freestyle project.
     * Expected: Build succeeds.
     */
    @Test
    public void testSecurityRegistrationSuccess() throws Exception {
        securityRegistrationResponse = "{\"result\": {\"status\": \"success\"}}";

        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsFreestyleRegisterSecurityStep step = new DevOpsFreestyleRegisterSecurityStep();
        step.setSecurityResultAttributes("{\"scanner\": \"Veracode\", \"applicationName\": \"testApp\"}");
        step.setSecurityTool("Veracode");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
    }

    /**
     * Test: Security result registration with invalid (non-JSON) securityResultAttributes.
     * Expected: JSONException caught, AbortException thrown, build fails.
     */
    @Test
    public void testSecurityRegistrationInvalidPayload() throws Exception {
        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsFreestyleRegisterSecurityStep step = new DevOpsFreestyleRegisterSecurityStep();
        step.setSecurityResultAttributes("this-is-not-valid-json");
        step.setSecurityTool("Veracode");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
    }

    /**
     * Test: Security result registration with empty JSON securityResultAttributes.
     * Expected: Parses as empty JSON, registration proceeds, build succeeds.
     */
    @Test
    public void testSecurityRegistrationEmptyJsonPayload() throws Exception {
        securityRegistrationResponse = "{\"result\": {\"status\": \"success\"}}";

        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsFreestyleRegisterSecurityStep step = new DevOpsFreestyleRegisterSecurityStep();
        step.setSecurityResultAttributes("{}");
        step.setSecurityTool("Veracode");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
    }

    /**
     * Test: Security result registration with a different valid security tool.
     * Expected: Registration proceeds, build succeeds.
     */
    @Test
    public void testSecurityRegistrationWithCheckmarxTool() throws Exception {
        securityRegistrationResponse = "{\"result\": {\"status\": \"success\"}}";

        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, "TestJob");
        DevOpsFreestyleRegisterSecurityStep step = new DevOpsFreestyleRegisterSecurityStep();
        step.setSecurityResultAttributes("{\"scanner\": \"Checkmarx One\", \"applicationName\": \"testApp\"}");
        step.setSecurityTool("Checkmarx One");
        project.getBuildersList().add(step);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
    }
}
