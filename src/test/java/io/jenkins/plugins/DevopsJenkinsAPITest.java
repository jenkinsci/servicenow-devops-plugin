package io.jenkins.plugins;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.config.DevOpsConfigurationEntry;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;


public class DevopsJenkinsAPITest {

	private static final String API_URL = "sndevops-api/plugin-configuration";
	private static final Logger LOGGER = Logger.getLogger(DevopsJenkinsAPITest.class.getName());

	@Rule
	public JenkinsRule j = new JenkinsRule();

	DevOpsConfiguration devOpsConfiguration ;
	List<DevOpsConfigurationEntry> sampleEntries = new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		DevOpsConfigurationEntry entity = new DevOpsConfigurationEntry( "DevOpsConfig1" ,true   ,true,
				"localhost:8080","1234" ,"5678" ,"v1" ,
				"cred1",true,true,"secredCredId"
		);
		sampleEntries.add(entity);
		devOpsConfiguration = DevOpsConfiguration.get();
	}

	@After
	public void tearDown() throws Exception {
		devOpsConfiguration.setEntries(null);
		devOpsConfiguration.save();
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
}
