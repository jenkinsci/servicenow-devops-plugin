package io.jenkins.plugins.config;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import io.jenkins.plugins.utils.CommUtils;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DevOpsConfigurationEntry extends AbstractDescribableImpl<DevOpsConfigurationEntry> {
	private String instanceUrl;
	private String apiVersion;
	private String toolId;
	private String snArtifactToolId; // Skipping validation for Artifact tool Id as it is an optional parameter.
	private boolean defaultConnection;
	private boolean active;
	private String name;
	private String credentialsId;
	private boolean trackCheck;
	private boolean trackPullRequestPipelinesCheck;
	private String secretCredentialId;

	@DataBoundConstructor
	public DevOpsConfigurationEntry(String name, boolean active, boolean defaultConnection, String instanceUrl, String toolId, String snArtifactToolId, String apiVersion, String credentialsId, boolean trackCheck, boolean trackPullRequestPipelinesCheck, String secretCredentialId) {
		this.name = name;
		this.active = active;
		this.defaultConnection = defaultConnection;
		this.instanceUrl = GenericUtils.removeTrailingSlashes(instanceUrl);
		this.toolId = toolId;
		this.snArtifactToolId = snArtifactToolId;
		this.apiVersion = apiVersion;
		this.credentialsId = credentialsId;
		this.trackCheck = trackCheck;
		this.trackPullRequestPipelinesCheck = trackPullRequestPipelinesCheck;
		this.secretCredentialId = secretCredentialId;
	}

	public boolean getActive() {
		return active;
	}

	@DataBoundSetter public void setActive(boolean active) {
		this.active = active;
	}

	public boolean getDefaultConnection() {
		return defaultConnection;
	}

	@DataBoundSetter public void setDefaultConnection(boolean defaultConnection) {
		this.defaultConnection = defaultConnection;
	}

	public String getName() {
		return name;
	}

	@DataBoundSetter public void setName(String name) {
		this.name = name;
	}

	public boolean getSnDevopsEnabled() {
		return active;
	}

	public String getInstanceUrl() {
		return instanceUrl;
	}

	@DataBoundSetter public void setInstanceUrl(String instanceUrl) {
		this.instanceUrl = GenericUtils.removeTrailingSlashes(instanceUrl);
	}

	public String getApiVersion() {
		if (!GenericUtils.isEmptyOrDefault(getSecretCredentialId()))
			return DevOpsConstants.VERSION_V2.toString();
		else if (!GenericUtils.isEmptyOrDefault(getCredentialsId()))
			return DevOpsConstants.VERSION_V1.toString();
		return apiVersion;
	}

	@DataBoundSetter public void setApiVersion(String apiVersion) {
		this.apiVersion = apiVersion;
	}

	public boolean getTrackCheck() {
		return trackCheck;
	}

	@DataBoundSetter public void setTrackCheck(boolean trackCheck) {
		this.trackCheck = trackCheck;
	}

	public boolean getTrackPullRequestPipelinesCheck() {
		return trackPullRequestPipelinesCheck;
	}

	@DataBoundSetter public void setTrackPullRequestPipelinesCheck(boolean trackPullRequestPipelinesCheck) {
		this.trackPullRequestPipelinesCheck = trackPullRequestPipelinesCheck;
	}

	public String getToolId() {
		return toolId;
	}

	@DataBoundSetter public void setToolId(String toolId) {
		this.toolId = toolId;
	}

	public String getSnArtifactToolId() {
		return snArtifactToolId;
	}

	@DataBoundSetter public void setSnArtifactToolId(String snArtifactToolId) {
		this.snArtifactToolId = snArtifactToolId;
	}

	public String getCredentialsId() {
		return credentialsId;
	}

	@DataBoundSetter public void setCredentialsId(String credentialsId) {
		this.credentialsId = credentialsId;
	}

	public String getSecretCredentialId() {
		return secretCredentialId;
	}

	@DataBoundSetter public void setSecretCredentialId(String secretCredentialId) {
		this.secretCredentialId = secretCredentialId;
	}

	public static String getUser(String credentialsId) {
		if (GenericUtils.isEmpty(credentialsId))
			return null;
		StandardUsernamePasswordCredentials sc = getCredentials(credentialsId);
		if (sc != null) {
			return sc.getUsername();
		}
		return null;
	}

	public static String getPwd(String credentialsId) {
		if (GenericUtils.isEmpty(credentialsId))
			return null;
		StandardUsernamePasswordCredentials sc = getCredentials(credentialsId);
		if (sc != null && sc.getPassword() != null) {
			return sc.getPassword().getPlainText();
		}
		return null;
	}

	public static StandardUsernamePasswordCredentials getCredentials(String credentialsId) {
		DomainRequirement dr = null;
		ItemGroup itemGroup = null;
		Authentication authentication = null;
		List<StandardUsernamePasswordCredentials> lc = CredentialsProvider
				.lookupCredentials(StandardUsernamePasswordCredentials.class, itemGroup, authentication, dr);

		for (int i = 0; i < lc.size(); i++) {
			StandardUsernamePasswordCredentials sc = lc.get(i);
			if (sc.getId().equals(credentialsId)) {
				return sc;
			}
		}
		return null;
	}

	public static String getTokenText(String credentialsId) {
		DomainRequirement dr = null;
		ItemGroup itemGroup = null;
		Authentication authentication = null;
		List<StringCredentials> lc = CredentialsProvider.lookupCredentials(StringCredentials.class, itemGroup,
				authentication, dr);

		for (int i = 0; i < lc.size(); i++) {
			StringCredentials sc = lc.get(i);
			if (sc.getId().equals(credentialsId)) {
				return sc.getSecret().getPlainText();
			}
		}
		return null;
	}

	public static boolean callConnectionApi(String apiVersion, JSONObject params, String userId, String password,
	                                 Map<String, String> tokenDetails, String instanceUrl) {
		String changeControlUrl = getChangeControlUrl(instanceUrl, apiVersion);
		String result = GenericUtils.parseResponseResult(CommUtils.callV2Support("GET", changeControlUrl, params, null,
				userId, password, null, null, tokenDetails), DevOpsConstants.TEST_CONNECTION_RESPONSE_ATTR.toString());
		if (result != null && result.equalsIgnoreCase("OK"))
			return true;
		else
			return false;
	}

	public static String getTrimmedUrl(String url) {
		return GenericUtils.isNotEmpty(url) ? url.endsWith("/") ? url.substring(0, url.length() - 1) : url : null;
	}

	public static String getChangeControlUrl(String instanceUrl, String apiVersion) {

		return GenericUtils.isNotEmpty(instanceUrl)
				? String.format("%s/api/sn_devops/%s/devops/orchestration" + "/changeControl",
				getTrimmedUrl(instanceUrl), apiVersion)
				: null;
	}

	public static String getChangeInfoUrl(String instanceUrl, String apiVersion) {

		return GenericUtils.isNotEmpty(instanceUrl)
				? String.format("%s/api/sn_devops/%s/devops/orchestration/changeInfo",
				getTrimmedUrl(instanceUrl), apiVersion)
				: null;
	}

	public static String getTrackingUrl(String instanceUrl, String apiVersion) {

		return GenericUtils.isNotEmpty(instanceUrl)
				? String.format("%s/api/sn_devops/%s/devops/orchestration" + "/pipelineInfo",
				getTrimmedUrl(instanceUrl), apiVersion)
				: null;
	}

	public String getTrackingUrl() {
		return getTrackingUrl(getInstanceUrl(), getApiVersion());
	}

	// change control url
	public String getChangeControlUrl() {
		return getChangeControlUrl(getInstanceUrl(), getApiVersion());
	}

	// change Info url
	public String getChangeInfoUrl() {
		return getChangeInfoUrl(getInstanceUrl(), getApiVersion());
	}

	public String getCallbackUrl() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_devops/%s/devops/orchestration" + "/callback",
				getTrimmedUrl(getInstanceUrl()), getApiVersion())
				: null;
	}

	// mapping url
	public String getMappingUrl() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_devops/%s/devops/orchestration" + "/stepMapping",
				getTrimmedUrl(getInstanceUrl()), getApiVersion())
				: null;
	}

	// notification url
	public String getNotificationUrl() {

		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_devops/%s/devops/tool/orchestration", getTrimmedUrl(getInstanceUrl()),
				getApiVersion())
				: null;
	}

	public String getTestUrl() {

		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_devops/%s/devops/tool/test", getTrimmedUrl(getInstanceUrl()),
				getApiVersion())
				: null;
	}

	// artifact registration url
	public String getArtifactRegistrationUrl() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_devops/%s/devops/artifact/registration", getTrimmedUrl(getInstanceUrl()),
				getApiVersion())
				: null;
	}

	// artifact create package url
	public String getArtifactCreatePackageUrl() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_devops/%s/devops/package/registration", getTrimmedUrl(getInstanceUrl()),
				getApiVersion())
				: null;
	}

	public String getCDMChangeSetCreationURL() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_cdm/changesets/create", getTrimmedUrl(getInstanceUrl()))
				: null;
	}

	public String getCDMUploadToComponentURL() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_cdm/applications/uploads/components", getTrimmedUrl(getInstanceUrl()))
				: null;
	}

	public String getCDMUploadToDeployableURL() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_cdm/applications/uploads/deployables", getTrimmedUrl(getInstanceUrl()))
				: null;
	}

	public String getCDMUploadToCollectionURL() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_cdm/applications/uploads/collections", getTrimmedUrl(getInstanceUrl()))
				: null;
	}

	public String getUploadStatusURL() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_cdm/applications/upload-status/", getTrimmedUrl(getInstanceUrl()))
				: null;
	}

	public String getSnapshotStatusURL() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/now/table/sn_cdm_snapshot", getTrimmedUrl(getInstanceUrl()))
				: null;
	}

	public String getPublishSnapshotURL(String snapshotId) {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_cdm/snapshots/%s/publish", getTrimmedUrl(getInstanceUrl()),
				snapshotId)
				: null;
	}

	public String getExportRequestURL() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_cdm/applications/deployables/exports", getTrimmedUrl(getInstanceUrl()))
				: null;
	}

	public String getExportConfigStatusURL(String exportId) {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_cdm/applications/deployables/exports/"+exportId+"/status", getTrimmedUrl(getInstanceUrl()))
				: null;
	}

	public String getExportConfigDataURL(String exportId) {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_cdm/applications/deployables/exports/"+exportId+"/content", getTrimmedUrl(getInstanceUrl()))
				: null;
	}

	public String getImpactedDeployableURL(String changesetId) {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_cdm/changesets/%s/impacted-deployables", getTrimmedUrl(getInstanceUrl()),
				changesetId)
				: null;
	}

	public String getPipelineRegisterURL() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_devops/%s/devops/config/updatePipeline", getTrimmedUrl(getInstanceUrl()),
				DevOpsConstants.VERSION_V1.toString())
				: null;
	}

	public String getValidateSnapshotURL(String snapshotId) {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_cdm/snapshots/%s/validate", getTrimmedUrl(getInstanceUrl()),
				snapshotId)
				: null;
	}

	public String getChangesetURL() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/now/table/sn_cdm_changeset", getTrimmedUrl(getInstanceUrl()))
				: null;
	}

	public String getValidAppURL() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/now/table/sn_cdm_application", getTrimmedUrl(getInstanceUrl()))
				: null;
	}

	public String getPolicyValidationURL() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/now/table/sn_cdm_policy_validation_result", getTrimmedUrl(getInstanceUrl()))
				: null;
	}

	public String getSecurityResultRegistrationURL() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/sn_devops/%s/devops/tool/security", getTrimmedUrl(getInstanceUrl()),
				getApiVersion())
				: null;
	}

	public String getDeployablesURL() {
		return GenericUtils.isNotEmpty(getInstanceUrl())
				? String.format("%s/api/now/table/sn_cdm_deployable", getTrimmedUrl(getInstanceUrl()))
				: null;
	}

	@Extension public static class DescriptorImpl extends Descriptor<DevOpsConfigurationEntry> {

		@Override
		public String getDisplayName() {
			return "DevOpsConfigurationEntryDescriptor";
		}

		public ListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId) {
			if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
				return new StandardListBoxModel().includeCurrentValue(credentialsId);
			}

			AbstractIdCredentialsListBoxModel<StandardListBoxModel, StandardCredentials> options = new StandardListBoxModel()
					.includeAs(ACL.SYSTEM, Jenkins.get(), StandardUsernamePasswordCredentials.class)
					.includeCurrentValue(credentialsId);
			Option defOption = new Option(DevOpsConstants.SN_DEFAULT_KEY.toString(), DevOpsConstants.SN_DEFUALT.toString());
			options.add(0, defOption);
			if (DevOpsConstants.SN_DEFUALT.toString().equals(credentialsId)) {
				options.get(0).selected = true;
			} else {
				for (ListBoxModel.Option option : options) {
					if (option.value.equals(credentialsId)) {
						option.selected = true;
					}
				}
			}
			return options;
		}

		public ListBoxModel doFillSecretCredentialIdItems(@QueryParameter String secretCredentialId) {
			if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
				return new StandardListBoxModel().includeCurrentValue(secretCredentialId);
			}

			DomainRequirement dr = null;
			ItemGroup itemGroup = null;
			Authentication authentication = null;

			AbstractIdCredentialsListBoxModel<StandardListBoxModel, StandardCredentials> options = new StandardListBoxModel()
					.includeAs(ACL.SYSTEM, Jenkins.get(), StringCredentials.class)
					.includeCurrentValue(secretCredentialId);

			Option defOption = new Option(DevOpsConstants.SN_DEFAULT_KEY.toString(), DevOpsConstants.SN_DEFUALT.toString());
			options.add(0, defOption);
			if (DevOpsConstants.SN_DEFUALT.toString().equals(secretCredentialId)) {
				options.get(0).selected = true;
			} else {
				for (ListBoxModel.Option option : options) {
					if (option.value.equals(secretCredentialId)) {
						option.selected = true;
					}
				}
			}
			return options;
		}

		@POST
		public FormValidation doCheckName(@QueryParameter("name") String name)
				throws IOException, ServletException {
			if (GenericUtils.isEmpty(name))
				return FormValidation.error("Please provide a unique name for this connection");
			return FormValidation.ok();
		}

		public FormValidation doCheckInstanceUrl(@QueryParameter("instanceUrl") String snInstanceUrl)
				throws IOException, ServletException {
			if (GenericUtils.isEmpty(snInstanceUrl))
				return FormValidation.error("Please provide a valid instance URL");
			if (snInstanceUrl.length() > 0 && !snInstanceUrl.startsWith("http"))
				return FormValidation.error("URL must start with http/https");
			if (snInstanceUrl.length() > 9 && !GenericUtils.checkUrlValid(snInstanceUrl)) {
				if (snInstanceUrl.substring(0, 5).equalsIgnoreCase("http:")) {
					return FormValidation.error("HTTP URLs are not allowed");
				}
				return FormValidation.error("Invalid URL");
			}
			return FormValidation.ok();
		}

	    public FormValidation doCheckAuthType(@QueryParameter("authType") String authType)
	            throws IOException, ServletException {
	        if (GenericUtils.isEmpty(authType))
	            return FormValidation.error("Please provide an auth Type");
	        return FormValidation.ok();
	    }

		public FormValidation doCheckCredentialsId(@QueryParameter("credentialsId") String credentialsId,
				@QueryParameter("secretCredentialId") String secretCredentialId) throws IOException, ServletException {

			List<DomainRequirement> drl = null;
			ItemGroup itemGroup = null;
			Authentication authentication = null;

			if (GenericUtils.isEmptyOrDefault(credentialsId) && GenericUtils.isEmptyOrDefault(secretCredentialId))
				return FormValidation.error("Please choose a credential!");
			if (!GenericUtils.isEmptyOrDefault(credentialsId) && CredentialsProvider.listCredentials(StandardUsernamePasswordCredentials.class, itemGroup, authentication,
					drl, CredentialsMatchers.withId(credentialsId)).isEmpty())
				return FormValidation.error("Cannot find currently selected credentials");
			return FormValidation.ok();
		}

		public FormValidation doCheckSecretCredentialId(@QueryParameter("secretCredentialId") String secretCredentialId,
				@QueryParameter("credentialsId") String credentialsId) throws IOException, ServletException {


			List<DomainRequirement> drl = null;
			ItemGroup itemGroup = null;
			Authentication authentication = null;

			if (GenericUtils.isEmptyOrDefault(secretCredentialId) && GenericUtils.isEmptyOrDefault(credentialsId))
				return FormValidation.error("Please choose a secret credential!");
			if (!GenericUtils.isEmptyOrDefault(secretCredentialId) && CredentialsProvider.listCredentials(StringCredentials.class, itemGroup, authentication, drl,
					CredentialsMatchers.withId(secretCredentialId)).isEmpty())
				return FormValidation.error("Cannot find currently selected credentials");
			return FormValidation.ok();
		}

		public FormValidation doCheckToolId(@QueryParameter("toolId") String snToolId)
				throws IOException, ServletException {
			if (GenericUtils.isEmpty(snToolId))
				return FormValidation.error("Please provide a valid tool ID");
			return FormValidation.ok();
		}

		// Skipping validation for Artifact tool Id as it is an optional parameter.
		//@RequirePOST
		@POST
		public FormValidation doTestConnection(@QueryParameter("instanceUrl") String instanceUrl,
				@QueryParameter("toolId") String toolId, @QueryParameter("credentialsId") String credentialsId,
				@QueryParameter("secretCredentialId") String secretCredentialId, @QueryParameter("active") boolean active) throws IOException, ServletException {

			List<DomainRequirement> drl = null;
			ItemGroup itemGroup = null;
			Authentication authentication = null;
			Jenkins.get().checkPermission(Jenkins.ADMINISTER);
			if (!active)
				return FormValidation.error("Connection must be active!");

			if (GenericUtils.isEmpty(instanceUrl))
				return FormValidation.error("Please provide the url!");

			if (GenericUtils.isEmpty(toolId))
				return FormValidation.error("Invalid tool id!");

			if(GenericUtils.isEmptyOrDefault(secretCredentialId) && GenericUtils.isEmptyOrDefault(credentialsId)) {
				return FormValidation.error("Invalid Credential id!");
			}

			if (GenericUtils.isEmpty(instanceUrl) || !GenericUtils.checkUrlValid(instanceUrl)) {
				return FormValidation.error("Invalid URL");
			}

			JSONObject params = new JSONObject();
			String user = null;
			String pwd = null;
			String tokenValue = null;
			boolean basicTest = false;
			boolean tokenTest = false;

			params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), toolId);
			params.put(DevOpsConstants.TEST_CONNECTION_ATTR.toString(), "true");
			params.put(DevOpsConstants.TOOL_TYPE_ATTR.toString(), DevOpsConstants.TOOL_TYPE.toString());

			Map<String, String> tokenDetails = new HashMap<String, String>();

			if (!GenericUtils.isEmptyOrDefault(credentialsId)) {
				if (CredentialsProvider.listCredentials(StandardUsernamePasswordCredentials.class, itemGroup,
						authentication, drl, CredentialsMatchers.withId(credentialsId)).isEmpty())
					return FormValidation.error("Cannot find currently selected credentials");

				StandardUsernamePasswordCredentials credentials = getCredentials(credentialsId);
				if (credentials != null) {
					user = credentials.getUsername();
					if (credentials.getPassword() != null) {
						pwd = credentials.getPassword().getPlainText();
					}
				}
				try {
					basicTest = callConnectionApi(DevOpsConstants.VERSION_V1.toString(), params, user, pwd, null, instanceUrl);
				} catch (Exception e) {
					return FormValidation.error("Client error : " + e.getMessage());
				}

			}

			if (!GenericUtils.isEmptyOrDefault(secretCredentialId)) {
				if (CredentialsProvider.listCredentials(StringCredentials.class, itemGroup, authentication, drl,
						CredentialsMatchers.withId(secretCredentialId)).isEmpty())
					return FormValidation.error("Cannot find currently selected credentials");

				tokenValue = getTokenText(secretCredentialId);
				tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(), tokenValue);
				try {
					tokenTest = callConnectionApi(DevOpsConstants.VERSION_V2.toString(), params, user, pwd, tokenDetails, instanceUrl);
				} catch (Exception e) {
					return FormValidation.error("Client error : " + e.getMessage());
				}
			}

			if (!GenericUtils.isEmptyOrDefault(secretCredentialId)
					&& !GenericUtils.isEmptyOrDefault(credentialsId)) {
				if (basicTest && tokenTest) {
					return FormValidation.ok(DevOpsConstants.BASIC_AUTHENCIATION_SUCCUSS.toString() + "\n"
							+ DevOpsConstants.TOKEN_AUTHENTICATION_SUCCUSS.toString());
				} else if (basicTest) {
					return FormValidation.error(DevOpsConstants.BASIC_AUTHENCIATION_SUCCUSS.toString() + "\n"
							+ DevOpsConstants.TOKEN_AUTHENTICATION_FAILURE.toString());
				} else if (tokenTest) {
					return FormValidation.error(DevOpsConstants.BASIC_AUTHENCIATION_FAILURE.toString() + "\n"
							+ DevOpsConstants.TOKEN_AUTHENTICATION_SUCCUSS.toString());
				} else {
					return FormValidation.error(DevOpsConstants.BASIC_AUTHENCIATION_FAILURE.toString() + "\n"
							+ DevOpsConstants.TOKEN_AUTHENTICATION_FAILURE.toString());
				}
			} else if (!GenericUtils.isEmptyOrDefault(secretCredentialId)) {
				if (tokenTest) {
					return FormValidation.ok(DevOpsConstants.TOKEN_AUTHENTICATION_SUCCUSS.toString());
				} else {
					return FormValidation.error(DevOpsConstants.TOKEN_AUTHENTICATION_FAILURE.toString());
				}

			} else {
				if (basicTest) {
					return FormValidation.ok(DevOpsConstants.BASIC_AUTHENCIATION_SUCCUSS.toString());
				} else {
					return FormValidation.error(DevOpsConstants.BASIC_AUTHENCIATION_FAILURE.toString());
				}
			}

		}
	}
}
