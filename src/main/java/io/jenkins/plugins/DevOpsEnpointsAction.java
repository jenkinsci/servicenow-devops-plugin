package io.jenkins.plugins;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.json.JsonBody;
import org.kohsuke.stapler.json.JsonHttpResponse;
import org.kohsuke.stapler.verb.PUT;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.RootAction;
import hudson.model.Descriptor.FormException;
import hudson.util.Secret;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.model.DevOpsConfigurationEntity;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;


@Extension
public class DevOpsEnpointsAction implements RootAction {
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
	
	private static final Logger LOGGER = Logger.getLogger(DevOpsEnpointsAction.class.getName());
	
	@PUT
	@WebMethod(name = "plugin-configuration")
	public JsonHttpResponse configurePlugin(@JsonBody DevOpsConfigurationEntity body) {

		Jenkins.get().checkPermission(Jenkins.ADMINISTER);
		JSONObject response = new JSONObject();
		String resMessage = "";

		DevOpsConfiguration configuration = GlobalConfiguration.all().get(DevOpsConfiguration.class);
		String logLevel;
		boolean trackPullRequestPipelinesCheck;
		boolean trackCheck;
		String snArtifactToolId;

		if (null == configuration) {
			configuration = new DevOpsConfiguration();
			logLevel = body.getLogLevel();
			trackPullRequestPipelinesCheck = false;
			trackCheck = false;
			snArtifactToolId = "";
		} else {
			logLevel = (!StringUtils.isBlank(configuration.getLogLevel())) ? configuration.getLogLevel()
					: body.getLogLevel();
			trackPullRequestPipelinesCheck = configuration.isTrackPullRequestPipelinesCheck();
			trackCheck = configuration.isTrackCheck();
			snArtifactToolId = (!StringUtils.isBlank(configuration.getSnArtifactToolId()))
					? configuration.getSnArtifactToolId()
					: "";
		}
		StaplerRequest staplerRequest = null;

		JSONObject params = new JSONObject();
		boolean tokenTest = false;

		params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), body.getToolId());
		params.put(DevOpsConstants.TEST_CONNECTION_ATTR.toString(), "true");
		params.put(DevOpsConstants.TOOL_TYPE_ATTR.toString(), DevOpsConstants.TOOL_TYPE.toString());

		if (!GenericUtils.isEmptyOrDefault(body.getToken())) {
			Map<String, String> tokenDetails = new HashMap<String, String>();
			tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(), body.getToken());
			try {
				tokenTest = configuration.callConnectionApi(DevOpsConstants.VERSION_V2.toString(), params, null, null,
						tokenDetails, body.getInstanceUrl());
			} catch (Exception e) {
				LOGGER.log(Level.INFO, "Test connection failied for toolID" + body.getToolId());
			}
		}

		if (tokenTest) {
			JSONObject snDevopsObject = new JSONObject();
			JSONObject configInfo = new JSONObject();
			String credential = null;
			credential = addSecretTextCredential(body.getToken(), body.getTokenId());
			if (credential != null) {
				configInfo.put("secretCredentialId", credential);
				configInfo.put("credentialsId", "");
				configInfo.put("instanceUrl", body.getInstanceUrl());
				configInfo.put("toolId", body.getToolId());
				configInfo.put("logLevel", logLevel);
				configInfo.put("snArtifactToolId", snArtifactToolId);
				configInfo.put("trackCheck", trackCheck);
				configInfo.put("trackPullRequestPipelinesCheck", trackPullRequestPipelinesCheck);

				snDevopsObject.put("snDevopsEnabled", configInfo);
				try {
					configuration.configure(staplerRequest, snDevopsObject);
					response.put("message", DevOpsConstants.JENKINS_CONFIGURATION_SUCCESS.toString());
					return new JsonHttpResponse(response, 200);
				} catch (FormException e) {
					resMessage = DevOpsConstants.JENKINS_CONFIGURATION_SAVE_FAILURE.toString();
					LOGGER.log(Level.INFO, "Error while saving config details" + e.getMessage());
				}
			} else {
				resMessage = DevOpsConstants.JENKINS_CONFIGURATION_CRED_CREATION_FAILURE.toString();
			}

		} else {
			resMessage = DevOpsConstants.JENKINS_CONFIGURATION_TEST_CONNECTION_FAILURE.toString();
		}

		response.put("message", resMessage);
		return new JsonHttpResponse(response, 500);

	}


	private String addSecretTextCredential(String secretToken, String tokenId) {
		String credId = null;
		try {
			CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
			StringCredentials tokenExists = getStringCredentialForTokenIfExists(tokenId);
			StringCredentialsImpl credential = new StringCredentialsImpl(CredentialsScope.GLOBAL, tokenId,
					tokenId, Secret.fromString(secretToken));
			 
			if(null !=tokenExists) {
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
		Authentication authentication = null;
		List<StringCredentials> lc = CredentialsProvider.lookupCredentials(StringCredentials.class, itemGroup,
				authentication, dr);
		for (int i = 0; i < lc.size(); i++) {
			StringCredentials sc = lc.get(i);
			if (sc.getId().equals(credentialsId)) {			
				return sc;
			}
		}
		return null;
	}


}