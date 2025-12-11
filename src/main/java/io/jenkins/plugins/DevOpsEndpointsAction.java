package io.jenkins.plugins;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.gson.Gson;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.RootAction;
import hudson.util.Secret;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.config.DevOpsConfigurationEntry;
import io.jenkins.plugins.model.DevOpsConfigurationEntity;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.json.JsonBody;
import org.kohsuke.stapler.json.JsonHttpResponse;
import org.kohsuke.stapler.verb.DELETE;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.PUT;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main entry point for ServiceNow DevOps API endpoints
 */
@Extension
public class DevOpsEndpointsAction implements RootAction {
	private static final Logger LOGGER = Logger.getLogger(DevOpsEndpointsAction.class.getName());

	@Override
	public String getIconFileName() {
		return null;
	}

	@Override
	public String getDisplayName() {
		return "DevOps API";
	}

	@Override
	public String getUrlName() {
		return "sndevops-api";
	}

	private Predicate<DevOpsConfigurationEntry> isValidConfiguration = (entry) -> StringUtils.isNotBlank(entry.getToolId()) && StringUtils.isNotBlank(entry.getInstanceUrl());

	/**
	 * Pipeline details endpoint - routes request to DevOpsDataApiAction
	 */
	@GET
	@WebMethod(name = "pipeline-details")
	public void getPipelineDetails(StaplerRequest request, StaplerResponse response) throws IOException {
		// Check administrator permission
		Jenkins.get().checkPermission(Jenkins.ADMINISTER);
		
		// Get DevOpsDataApiAction instance
		DevOpsDataApiAction dataApiAction = new DevOpsDataApiAction();
		// Forward the request to DevOpsDataApiAction's business logic handler
		dataApiAction.handlePipelineDetailsRequest(request, response);
	}

	@PUT
	@WebMethod(name = "plugin-configuration")
	public JsonHttpResponse configurePlugin(@JsonBody DevOpsConfigurationEntity request) {

		Jenkins.get().checkPermission(Jenkins.ADMINISTER);
		Integer allowedConfigurations = Integer.valueOf(DevOpsConstants.MAX_ALLOWED_DEVOPS_CONFIGURATIONS.toString());
		JSONObject response = new JSONObject();
		String resMessage = "";

		JSONObject params = new JSONObject();
		boolean tokenTest = false;

		request.setInstanceUrl(GenericUtils.removeTrailingSlashes(request.getInstanceUrl()));
		params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), request.getToolId());
		params.put(DevOpsConstants.TEST_CONNECTION_ATTR.toString(), "true");
		params.put(DevOpsConstants.TOOL_TYPE_ATTR.toString(), DevOpsConstants.TOOL_TYPE.toString());

		boolean isActive= true;
		if (!request.isSkipValidation()) {
			if (!GenericUtils.isEmptyOrDefault(request.getToken())) {
				Map<String, String> tokenDetails = new HashMap();
				tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(), request.getToken());
				try {
					tokenTest = DevOpsConfigurationEntry.callConnectionApi(DevOpsConstants.VERSION_V2.toString(), params, null, null,
							tokenDetails, request.getInstanceUrl());
				} catch (Exception e) {
					LOGGER.log(Level.INFO, "Test connection failed for toolID" + request.getToolId());
				}
			}
		} else{
			LOGGER.log(Level.INFO, "Skipping token and connection validation for toolid: " + request.getToolId());
			tokenTest= true;
			isActive = false;
		}

		DevOpsConfiguration configuration = DevOpsConfiguration.get();
		List<DevOpsConfigurationEntry> existingEntries = configuration.getEntries();
		int numEntries = existingEntries.size();
		DevOpsConfigurationEntry devOpsConfigurationEntry = findMatchingConfiguration(request, existingEntries);
		if (devOpsConfigurationEntry == null && numEntries >= allowedConfigurations) {
			response.put("message", String.format("Unable to configure as we reached the maximum allowed ServiceNow DevOps configurations"));
			return new JsonHttpResponse(response, 500);
		}
		try {
			if (tokenTest) {
				String credential = addSecretTextCredential(request.getToken(), request.getTokenId());
				if (credential != null) {
					if (devOpsConfigurationEntry == null) {
						DevOpsConfigurationEntry entry = new DevOpsConfigurationEntry(buildConfigurationName(request), isActive , request.isDefaultConnection(), request.getInstanceUrl(), request.getToolId(), "", DevOpsConstants.VERSION_V2.toString(), "", false, false, credential);
						if (numEntries == 0)
							entry.setDefaultConnection(true);
						existingEntries.add(entry);
					} else {
						devOpsConfigurationEntry.setInstanceUrl(request.getInstanceUrl());
						devOpsConfigurationEntry.setToolId(request.getToolId());
						devOpsConfigurationEntry.setSecretCredentialId(credential);
						devOpsConfigurationEntry.setActive(isActive);
						if (StringUtils.isNotBlank(request.getName())) {
							devOpsConfigurationEntry.setName(request.getName());
						}
						if (!devOpsConfigurationEntry.getDefaultConnection()) {
							devOpsConfigurationEntry.setDefaultConnection(request.isDefaultConnection());
						}
						if (StringUtils.isBlank(devOpsConfigurationEntry.getSnArtifactToolId())) {
							devOpsConfigurationEntry.setSnArtifactToolId("");
						}
						if (numEntries == 1)
							devOpsConfigurationEntry.setDefaultConnection(true);
					}
					configuration.setEntries(existingEntries);
					response.put("message", DevOpsConstants.JENKINS_CONFIGURATION_SUCCESS.toString());
					response.put("totalConfigurationsCount", existingEntries.size());
					response.put("jenkinsVersion", GenericUtils.getJenkinsVersion());
					return new JsonHttpResponse(response, 200);
				} else {
					resMessage = DevOpsConstants.JENKINS_CONFIGURATION_CRED_CREATION_FAILURE.toString();
				}
			} else {
				resMessage = DevOpsConstants.JENKINS_CONFIGURATION_TEST_CONNECTION_FAILURE.toString();
			}
		} catch (Exception e) {
			resMessage = DevOpsConstants.JENKINS_CONFIGURATION_SAVE_FAILURE.toString();
			LOGGER.log(Level.INFO, "Error while saving config details" + e.getMessage());
		}
		response.put("message", resMessage);
		return new JsonHttpResponse(response, 500);
	}

	@DELETE
	@WebMethod(name = "plugin-configuration" )
	public JsonHttpResponse deletePluginConfiguration(@QueryParameter("name") String name) {
		Jenkins.get().checkPermission(Jenkins.ADMINISTER);

		LOGGER.log(Level.INFO, "Deleting configuration with name: " + name);
		boolean removed = false;

		DevOpsConfiguration configuration = DevOpsConfiguration.get();
		List<DevOpsConfigurationEntry> entries = configuration.getEntries();
		if (entries != null) {
			removed = entries.removeIf(entry -> entry.getName().equals(name));

			if (removed) {
				configuration.setEntries(entries);
				configuration.save();
			}
		}
		JSONObject response = new  JSONObject();
		if (removed) {

			response.put("message", "Configuration deleted successfully");
			return new JsonHttpResponse(response, 200);
		}{
			response.put("message", "Configuration not found");
			return new JsonHttpResponse(response, 404);
		}

	}



	@GET
	@WebMethod(name = "plugin-configuration")
	public JsonHttpResponse getPluginConfiguration() {
		Jenkins.get().checkPermission(Jenkins.ADMINISTER);
		List<DevOpsConfigurationEntry> entries = DevOpsConfiguration.get().getEntries();
		String entriesToJson =  new Gson().toJson(entries);
		JSONArray jsonEntities = JSONArray.fromObject(entriesToJson);
		JSONObject response = new JSONObject();
		response.put("entries", jsonEntities);
		return new JsonHttpResponse(response, 200);
	}

	/**
	 * @return {"permissions":{"hasAdminRole":true}}
	 */
	@GET
	@WebMethod(name = "permissions")
	public JsonHttpResponse getUserPermissions() {
		Jenkins jenkins = Jenkins.get();
		Map<String, Boolean> userPermissions = new HashMap<>();
		userPermissions.put("hasAdminRole", jenkins.hasPermission(Jenkins.ADMINISTER));
		JSONObject response = new JSONObject();
		response.put("permissions", userPermissions);
		return new JsonHttpResponse(response, 200);
	}

	private DevOpsConfigurationEntry findMatchingConfiguration(DevOpsConfigurationEntity body, List<DevOpsConfigurationEntry> existingEntries) {
		if (existingEntries == null) {
			return null;
		}
		return existingEntries.stream()
				.filter(isValidConfiguration)
				.filter(entry -> entry.getToolId().equalsIgnoreCase(body.getToolId()) && entry.getInstanceUrl().equalsIgnoreCase(body.getInstanceUrl()))
				.findFirst().orElse(null);
	}


	private String addSecretTextCredential(String secretToken, String tokenId) {
		String credId = null;
		try {
			CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
			StringCredentials tokenExists = getStringCredentialForTokenIfExists(tokenId);
			StringCredentialsImpl credential = new StringCredentialsImpl(CredentialsScope.GLOBAL, tokenId,
					tokenId, Secret.fromString(secretToken));

			if (null != tokenExists) {
				store.removeCredentials(Domain.global(), tokenExists);
			}
			store.addCredentials(Domain.global(), credential);
			credId = credential.getId();
		} catch (Exception e) {
			LOGGER.log(Level.INFO, "Error while creating the secret credential");
		}
		return credId;
	}


	public StringCredentials getStringCredentialForTokenIfExists(String credentialsId) {
		DomainRequirement dr = null;
		ItemGroup itemGroup = null;
		List<StringCredentials> lc = CredentialsProvider.lookupCredentials(StringCredentials.class, itemGroup,
				null, dr);
		for (int i = 0; i < lc.size(); i++) {
			StringCredentials sc = lc.get(i);
			if (sc.getId().equals(credentialsId)) {
				return sc;
			}
		}
		return null;
	}

	private String buildConfigurationName(DevOpsConfigurationEntity request) throws URISyntaxException {
		if (StringUtils.isBlank(request.getName())) {
			String domain = new URI(request.getInstanceUrl()).getHost();
			String instanceName = domain.startsWith("www.") ? domain.substring(4) : domain;
			return String.format("DevOps-%s-%s", instanceName, new Date().getTime());
		}
		return request.getName();
	}


}