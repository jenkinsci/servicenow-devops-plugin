package io.jenkins.plugins;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.FormValidation;
import hudson.util.Secret;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.config.DevOpsConfigurationEntry;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class DevopsJenkinsAPITest {

	private static final String API_URL = "sndevops-api/plugin-configuration";

	@Rule
	public JenkinsRule j = new JenkinsRule();

	DevOpsConfiguration devOpsConfiguration ;
	List<DevOpsConfigurationEntry> sampleEntries = new ArrayList<>();
	private MockWebServer mockServer;
	private String mockServerUrl;

	@Before
	public void setUp() throws Exception {
		mockServer = new MockWebServer();
		mockServer.start();

		DevOpsConfigurationEntry entity = new DevOpsConfigurationEntry( "DevOpsConfig1" ,true   ,true,
				"localhost:8080","1234" ,"5678" ,"v1" ,
				"cred1",true,true,"secredCredId"
		);
		sampleEntries.add(entity);
		devOpsConfiguration = DevOpsConfiguration.get();
         mockServerUrl = mockServer.url("/").toString();
        if (mockServerUrl.endsWith("/")) {
            mockServerUrl = mockServerUrl.substring(0, mockServerUrl.length() - 1);
        }
	}

	@After
	public void tearDown() throws Exception {
		devOpsConfiguration.setEntries(null);
		devOpsConfiguration.save();
		if (mockServer != null) {
			mockServer.shutdown();
		}
	}

	@Test
	public void testGetEmptyEntry() throws Exception {
		
		JenkinsRule.WebClient webClient = j.createWebClient();
		WebResponse response = webClient.goTo(API_URL, "application/json").getWebResponse();
		String responseText = response.getContentAsString();
		JSONObject jsonResponse = JSONObject.fromObject(responseText);
		assert response.getStatusCode() == 200;
		JSONArray entries = (JSONArray) jsonResponse.get("entries");
		assert  entries ==null || entries.size () == 0 || entries.get(0) == JSONNull.getInstance();

	}

	@Test
	public void testGetSingleEntryConfiguration() throws Exception {

		devOpsConfiguration.setEntries(sampleEntries);
		devOpsConfiguration.save();

		JenkinsRule.WebClient webClient = j.createWebClient();
		WebResponse response = webClient.goTo(API_URL, "application/json").getWebResponse();

		String responseText = response.getContentAsString();
		JSONObject jsonResponse = JSONObject.fromObject(responseText);
		assert response.getStatusCode() == 200;
		JSONArray entities = (JSONArray) jsonResponse.get("entries");
		assert entities != null || entities.size() == 1;
		JSONObject entity = (JSONObject) entities.get(0);
		assert  entity.get("toolId").equals(sampleEntries.get(0).getToolId()) ;
		assert  entity.get("instanceUrl").equals(sampleEntries.get(0).getInstanceUrl()) ;
		assert  entity.get("name").equals(sampleEntries.get(0).getName()) ;

	}

	@Test
	public void testGetMultipleEntryConfiguration() throws Exception {
		DevOpsConfigurationEntry sampleEntity2 = new DevOpsConfigurationEntry( "DevOpsConfig2" ,true   ,true,
				"localhost:8080","2222" ,"8888" ,"v1" ,
				"cred2",true,true,"secredCredId2"
		);
		sampleEntries.add(sampleEntity2);
		devOpsConfiguration.setEntries(sampleEntries);
		devOpsConfiguration.save();

		JenkinsRule.WebClient webClient = j.createWebClient();
		WebResponse response = webClient.goTo(API_URL, "application/json").getWebResponse();

		String responseText = response.getContentAsString();
		JSONObject jsonResponse = JSONObject.fromObject(responseText);
		assert response.getStatusCode() == 200;
		JSONArray entities = (JSONArray) jsonResponse.get("entries");
		assert entities != null || entities.size() == 2;
		JSONObject entity = (JSONObject) entities.get(0);
		assert  entity.get("toolId").equals(sampleEntries.get(0).getToolId()) ;
		assert  entity.get("instanceUrl").equals(sampleEntries.get(0).getInstanceUrl()) ;
		assert  entity.get("name").equals(sampleEntries.get(0).getName()) ;
		entity = (JSONObject) entities.get(1);
		assert  entity.get("toolId").equals(sampleEntries.get(1).getToolId()) ;
		assert  entity.get("instanceUrl").equals(sampleEntries.get(1).getInstanceUrl()) ;
		assert  entity.get("name").equals(sampleEntries.get(1).getName()) ;
	}

    @Test
    public void testDoCheckInstanceUrl() throws Exception {
        DevOpsConfigurationEntry.DescriptorImpl descriptor = 
                (DevOpsConfigurationEntry.DescriptorImpl) j.jenkins.getDescriptor(DevOpsConfigurationEntry.class);

        // Test with null instance URL
        Assert.assertNotNull(descriptor);
        hudson.util.FormValidation result = descriptor.doCheckInstanceUrl(null);
        assertEquals("FormValidation should be ERROR", hudson.util.FormValidation.Kind.ERROR, result.kind);
        assertTrue("Response should contain the expected error message: Please provide a valid instance URL",
                result.getMessage().contains("Please provide a valid instance URL"));

        // Test with incorrect transfer protocol
        result = descriptor.doCheckInstanceUrl("PTTH");
        assertTrue("Response should contain the expected error message: URL must start with http/https",
                        result.getMessage().contains("URL must start with http/https"));

        // Test with HTTP
        result = descriptor.doCheckInstanceUrl("http://www.servicenow.com/");
        assertTrue("Response should contain the expected error message: HTTP URLs are not allowed",
                result.getMessage().contains("HTTP URLs are not allowed"));

        // Test with invalid url
        result = descriptor.doCheckInstanceUrl("https:--sdfjodsjlfhsdcurlgxnoise");
        assertTrue("Response should contain the expected error message: Invalid URL",
                result.getMessage().contains("Invalid URL"));

        // Test with valid url
        result = descriptor.doCheckInstanceUrl("https://www.servicenow.com/");
        assertEquals("FormValidation should be OK", FormValidation.Kind.OK, result.kind);
    }

	@Test
	public void testDeleteInvalidConfiguration() throws Exception{

		devOpsConfiguration.setEntries(sampleEntries);
		devOpsConfiguration.save();

		DevOpsConfigurationEntry sampleEntity2 = new DevOpsConfigurationEntry( "DevOpsConfig2" ,true   ,true,
				"localhost:8080","2222" ,"8888" ,"v1" ,
				"cred2",true,true,"secredCredId2"
		);
		JenkinsRule.WebClient webClient = j.createWebClient();
		URL requestUrl = new URL(webClient.getContextPath() + API_URL +"?name=DevOpsConfig2");
		WebRequest request = new WebRequest(requestUrl, HttpMethod.DELETE);
		try {
			WebResponse response = webClient.getPage(request).getWebResponse();
			assert response.getStatusCode() == 404;
		} catch (FailingHttpStatusCodeException e) {
			assert e.getStatusCode() == 404;
		}

	}
	@Test
	public void testDeleteValidConfiguration() throws Exception{


		DevOpsConfigurationEntry sampleEntity2 = new DevOpsConfigurationEntry( "DevOpsConfig2" ,true   ,true,
				"localhost:8080","2222" ,"8888" ,"v1" ,
				"cred2",true,true,"secredCredId2"
		);
		sampleEntries.add(sampleEntity2);
		devOpsConfiguration.setEntries(sampleEntries);
		devOpsConfiguration.save();

		JenkinsRule.WebClient webClient = j.createWebClient();
		URL requestUrl = new URL(webClient.getContextPath() + API_URL +"?name=" + sampleEntity2.getName());
		WebRequest request = new WebRequest(requestUrl, HttpMethod.DELETE);
		try {
			WebResponse response = webClient.getPage(request).getWebResponse();
			assert response.getStatusCode() == 200;
		} catch (FailingHttpStatusCodeException e) {
			assert e.getStatusCode() == 200;
		}

	}

    @Test
    public void testCallbackApi() throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();
        String callbackRequest = "{\"result\": \"succeeded\",     \"changeComments\": \"Change request created with errors in DevOps data retrieval. Processing errors: {\"pipelineExecution\":\"PE0051879\",\"pipelineName\":\"MAL/Non-prod/CD_Jobs/Allocation-Web-Service-ADM-Europe/Allocation-Web-Service-ADM-Europe-CE\",\"processingErrors\":{\"failed_commit_event\":[{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"}]}} Approver: Abdul Khadar Mohammad, Date: 2024-09-16 06:12:59\"}";
        URL requestUrl = new URL(webClient.getContextPath() + "sn-devops/pipeline_191f54df-b2a9-4d49-837c-2047b1214156");
        WebRequest request = new WebRequest(requestUrl, HttpMethod.POST);
        request.setRequestBody(callbackRequest);
        request.setAdditionalHeader("Content-Type", "application/json");
        try {
            WebResponse response = webClient.loadWebResponse(request);
            assert response.getStatusCode() == 410;
        } catch (FailingHttpStatusCodeException e) {
            assert e.getStatusCode() == 410;
        }
    }

	@Test
	public void testDoTestConnectionWithBasicAuth() throws Exception {
		mockServer.enqueue(new MockResponse()
				.setBody("{\"result\": {\"status\": \"success\", \"connectionStatus\": \"OK\"}}")
				.setHeader("Content-Type", "application/json")
				.setResponseCode(200));

		// Create credentials with SYSTEM ACL context to bypass permission checks
		try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
			UsernamePasswordCredentialsImpl credentials = new UsernamePasswordCredentialsImpl(
					CredentialsScope.GLOBAL,
					"test-cred-id",
					"Test Credentials",
					"testuser",
					"testpassword"
			);
			CredentialsProvider.lookupStores(j.jenkins).iterator().next()
					.addCredentials(Domain.global(), credentials);

			// Call the descriptor method directly with SYSTEM permissions
			DevOpsConfigurationEntry.DescriptorImpl descriptor = 
					(DevOpsConfigurationEntry.DescriptorImpl) j.jenkins.getDescriptor(DevOpsConfigurationEntry.class);
			
			hudson.util.FormValidation result = descriptor.doTestConnection(
					mockServerUrl,
					"test-tool-id",
					"test-cred-id",
					"",
					true
			);

			// Assert successful connection
			assertEquals("FormValidation should be OK", hudson.util.FormValidation.Kind.OK, result.kind);
			assertTrue("Response should contain success message", 
					result.getMessage().contains("Connection using &#039;Credentials&#039; is successful!"));
		}
	}

	@Test
	public void testDoTestConnectionWithTokenAuth() throws Exception {
		mockServer.enqueue(new MockResponse()
				.setBody("{\"result\": {\"status\": \"success\", \"connectionStatus\": \"OK\"}}")
				.setHeader("Content-Type", "application/json")
				.setResponseCode(200));

		// Create secret credentials (token) with SYSTEM ACL context
		try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
			StringCredentialsImpl secretCredentials = new StringCredentialsImpl(
					CredentialsScope.GLOBAL,
					"test-secret-cred-id",
					"Test Secret Credentials",
					Secret.fromString("test-token-value")
			);
			CredentialsProvider.lookupStores(j.jenkins).iterator().next()
					.addCredentials(Domain.global(), secretCredentials);

			// Call the descriptor method directly with SYSTEM permissions
			DevOpsConfigurationEntry.DescriptorImpl descriptor = 
					(DevOpsConfigurationEntry.DescriptorImpl) j.jenkins.getDescriptor(DevOpsConfigurationEntry.class);
			
			hudson.util.FormValidation result = descriptor.doTestConnection(
					mockServerUrl,
					"test-tool-id",
					"",
					"test-secret-cred-id",
					true
			);

			// Assert successful connection
			assertEquals("FormValidation should be OK", hudson.util.FormValidation.Kind.OK, result.kind);
			assertTrue("Response should contain success message", 
					result.getMessage().contains("Connection using &#039;Secret Credentials&#039; is successful!"));
		}
	}

	@Test
	public void testDoTestConnectionWithBothAuthMethods() throws Exception {
		mockServer.enqueue(new MockResponse()
				.setBody("{\"result\": {\"status\": \"success\", \"connectionStatus\": \"OK\"}}")
				.setHeader("Content-Type", "application/json")
				.setResponseCode(200));
		// Second response for token auth
		mockServer.enqueue(new MockResponse()
				.setBody("{\"result\": {\"status\": \"success\", \"connectionStatus\": \"OK\"}}")
				.setHeader("Content-Type", "application/json")
				.setResponseCode(200));

		// Create both types of credentials with SYSTEM ACL context
		try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
			UsernamePasswordCredentialsImpl credentials = new UsernamePasswordCredentialsImpl(
					CredentialsScope.GLOBAL,
					"test-cred-id",
					"Test Credentials",
					"testuser",
					"testpassword"
			);
			StringCredentialsImpl secretCredentials = new StringCredentialsImpl(
					CredentialsScope.GLOBAL,
					"test-secret-cred-id",
					"Test Secret Credentials",
					Secret.fromString("test-token-value")
			);
			CredentialsProvider.lookupStores(j.jenkins).iterator().next()
					.addCredentials(Domain.global(), credentials);
			CredentialsProvider.lookupStores(j.jenkins).iterator().next()
					.addCredentials(Domain.global(), secretCredentials);

			// Call the descriptor method directly with SYSTEM permissions
			DevOpsConfigurationEntry.DescriptorImpl descriptor = 
					(DevOpsConfigurationEntry.DescriptorImpl) j.jenkins.getDescriptor(DevOpsConfigurationEntry.class);
			
			hudson.util.FormValidation result = descriptor.doTestConnection(
					mockServerUrl,
					"test-tool-id",
					"test-cred-id",
					"test-secret-cred-id",
					true
			);

			// Assert successful connection for both methods
			assertEquals("FormValidation should be OK", hudson.util.FormValidation.Kind.OK, result.kind);
			assertTrue("Response should contain basic auth success message", 
					result.getMessage().contains("Connection using &#039;Credentials&#039; is successful!"));
            assertTrue("Response should contain success message",
                    result.getMessage().contains("Connection using &#039;Secret Credentials&#039; is successful!"));
		}
	}

	@Test
	public void testDoTestConnectionWithInactiveConnection() throws Exception {
		try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
			DevOpsConfigurationEntry.DescriptorImpl descriptor = 
					(DevOpsConfigurationEntry.DescriptorImpl) j.jenkins.getDescriptor(DevOpsConfigurationEntry.class);
			
			hudson.util.FormValidation result = descriptor.doTestConnection(
					mockServerUrl,
					"test-tool-id",
					"test-cred-id",
					"",
					false  // inactive connection
			);

			// Assert error for inactive connection
			assertEquals("FormValidation should be ERROR", hudson.util.FormValidation.Kind.ERROR, result.kind);
			assertTrue("Response should contain error about inactive connection", 
					result.getMessage().contains("Connection must be active!"));
		}
	}

	@Test
	public void testDoTestConnectionWithMissingCredentials() throws Exception {
		try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
			DevOpsConfigurationEntry.DescriptorImpl descriptor = 
					(DevOpsConfigurationEntry.DescriptorImpl) j.jenkins.getDescriptor(DevOpsConfigurationEntry.class);
			
			hudson.util.FormValidation result = descriptor.doTestConnection(
					mockServerUrl,
					"test-tool-id",
					"",  // no basic credentials
					"",  // no token credentials
					true
			);

			// Assert error for missing credentials
			assertEquals("FormValidation should be ERROR", hudson.util.FormValidation.Kind.ERROR, result.kind);
			assertTrue("Response should contain error about invalid credentials", 
					result.getMessage().contains("Invalid Credential id!"));
		}
	}

	@Test(expected = org.springframework.security.access.AccessDeniedException.class)
	public void testDoTestConnectionWithNonAdminUser() throws Exception {
		mockServer.enqueue(new MockResponse()
				.setBody("{\"result\": {\"status\": \"success\", \"connectionStatus\": \"OK\"}}")
				.setHeader("Content-Type", "application/json")
				.setResponseCode(200));

		// Create credentials with SYSTEM ACL context
		try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
			UsernamePasswordCredentialsImpl credentials = new UsernamePasswordCredentialsImpl(
					CredentialsScope.GLOBAL,
					"test-cred-id",
					"Test Credentials",
					"testuser",
					"testpassword"
			);
			CredentialsProvider.lookupStores(j.jenkins).iterator().next()
					.addCredentials(Domain.global(), credentials);
		}

		// Setup security with a non-admin user
		j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
		j.jenkins.setAuthorizationStrategy(new org.jvnet.hudson.test.MockAuthorizationStrategy()
				.grant(hudson.model.Hudson.READ).everywhere().to("user"));

		// Try to call doTestConnection as a non-admin user (should throw AccessDeniedException)
		try (ACLContext ignored = ACL.as2(hudson.model.User.getById("user", true).impersonate2())) {
			DevOpsConfigurationEntry.DescriptorImpl descriptor = 
					(DevOpsConfigurationEntry.DescriptorImpl) j.jenkins.getDescriptor(DevOpsConfigurationEntry.class);
			
			// This should throw AccessDeniedException due to Jenkins.ADMINISTER permission check
			descriptor.doTestConnection(
					mockServerUrl,
					"test-tool-id",
					"test-cred-id",
					"",
					true
			);
		}
	}
}
