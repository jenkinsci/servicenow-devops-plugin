package io.jenkins.plugins;

import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.config.DevOpsConfigurationEntry;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.model.DevOpsPipelineInfoConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public abstract class BaseDevOpsTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    protected DevOpsConfiguration devOpsConfiguration;
    protected List<DevOpsConfigurationEntry> sampleEntries = new ArrayList<>();
    protected MockWebServer mockServer;
    protected String mockServerUrl;

    @Before
    public void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();

        mockServerUrl = mockServer.url("/").toString();
        if (mockServerUrl.endsWith("/")) {
            mockServerUrl = mockServerUrl.substring(0, mockServerUrl.length() - 1);
        }

        DevOpsConfigurationEntry entity = new DevOpsConfigurationEntry(
                "DevOpsConfig1",
                true,
                true,
                mockServerUrl,
                "1234",
                "5678",
                "v2",
                "cred1",
                true,
                false,
                "secred1"
        );
        sampleEntries.add(entity);

        devOpsConfiguration = DevOpsConfiguration.get();
        devOpsConfiguration.setEntries(sampleEntries);
        devOpsConfiguration.save();

        setupDefaultTrackedJob();
    }


    protected void setupDefaultTrackedJob() {
        DevOpsRootAction.setTrackedJob("TestJob_1");

        DevOpsPipelineInfoConfig config = new DevOpsPipelineInfoConfig(
                true,
                devOpsConfiguration.getEntries().get(0),
                "1234_" + mockServerUrl
        );
        DevOpsModel.DevOpsPipelineInfo pipelineInfo = new DevOpsModel.DevOpsPipelineInfo(Arrays.asList(config));
        DevOpsRootAction.setSnPipelineInfo("TestJob_1", pipelineInfo);
    }

    protected void enqueueMockResponse(String body, int responseCode) {
        mockServer.enqueue(new MockResponse()
                .setBody(body)
                .setHeader("Content-Type", "application/json")
                .setResponseCode(responseCode));
    }


    protected void enqueueSuccessResponse(String body) {
        enqueueMockResponse(body, 200);
    }

    @After
    public void tearDown() throws Exception {
        // Clear DevOps configuration
        if (devOpsConfiguration != null) {
            devOpsConfiguration.setEntries(null);
            devOpsConfiguration.save();
        }

        // Shutdown MockWebServer
        if (mockServer != null) {
            mockServer.shutdown();
        }
    }

}
