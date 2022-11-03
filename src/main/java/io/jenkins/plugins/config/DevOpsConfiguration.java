package io.jenkins.plugins.config;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.utils.CommUtils;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
@Symbol("snDevOpsConfig")
public class DevOpsConfiguration extends GlobalConfiguration {

	private static final Logger LOGGER = Logger.getLogger(DevOpsConfiguration.class.getName());

	private boolean snDevopsEnabled;
	private String instanceUrl;
	private String apiVersion;
	private String toolId;
	private String snArtifactToolId; // Skipping validation for Artifact tool Id as it is an optional parameter.
	private boolean debug;
	private String logLevel;
	private String credentialsId;
	private String user;
	private String pwd;
	private boolean trackCheck;
	private boolean trackPullRequestPipelinesCheck;

	public DevOpsConfiguration() {
		load();
		// To handle upgrade case
		if (this.logLevel == null)
			this.logLevel = (this.debug) ? "info" : "off";
		GenericUtils.configureLogger(this.logLevel);
	}

	@Override
	public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
		final JSONObject snDevOpsJSON = formData.getJSONObject("snDevopsEnabled");
		if ((snDevOpsJSON != null) && !(snDevOpsJSON.isNullObject())) {
			if (snDevOpsJSON.isEmpty()) {
				reIntialize();
			} else {
				this.snDevopsEnabled = true;
				this.instanceUrl = snDevOpsJSON.getString("instanceUrl");
				this.toolId = snDevOpsJSON.getString("toolId");
				this.snArtifactToolId = snDevOpsJSON.getString("snArtifactToolId");
				this.apiVersion = snDevOpsJSON.getString("apiVersion");
				this.credentialsId = snDevOpsJSON.getString("credentialsId");
				this.logLevel = snDevOpsJSON.getString("logLevel");
				GenericUtils.configureLogger(this.logLevel);
				this.user = null;
				this.pwd = null;
				this.trackCheck = snDevOpsJSON.getBoolean("trackCheck");
				this.trackPullRequestPipelinesCheck = snDevOpsJSON.getBoolean("trackPullRequestPipelinesCheck");
			}
		} else {
			this.snDevopsEnabled = false;
		}

		this.save();
		return super.configure(req, formData);
	}

	private void reIntialize() {
		this.snDevopsEnabled = false;
		this.instanceUrl = null;
		this.toolId = null;
		this.snArtifactToolId = null;
		this.apiVersion = null;
		this.debug = false;
		this.logLevel = "off";
		this.user = null;
		this.pwd = null;
		this.credentialsId = null;
		this.trackCheck = false;
		this.trackPullRequestPipelinesCheck = false;
	}

	@Nonnull
	public static DevOpsConfiguration get() {
		return (DevOpsConfiguration) GlobalConfiguration.all().getInstance(DevOpsConfiguration.class);
	}

	@Restricted(NoExternalUse.class)
	public static @Nonnull DevOpsConfiguration getOrDie() {
		DevOpsConfiguration config = DevOpsConfiguration.get();
		if (config == null) {
			throw new IllegalStateException(
					"DevOpsConfiguration instance is missing. Probably the Jenkins instance is not fully loaded at this time.");
		}
		return config;
	}

	public boolean isSnDevopsEnabled() {
		return snDevopsEnabled;
	}

	public String getInstanceUrl() {
		return instanceUrl;
	}

	public String getApiVersion() {
		return apiVersion;
	}

	public boolean isTrackCheck() {
		return trackCheck;
	}

	public boolean isTrackPullRequestPipelinesCheck() {
		return trackPullRequestPipelinesCheck;
	}

	public String getUser() {
		// To ensure backward compatibility we are using saved user details
		if (!GenericUtils.isEmpty(this.user)) {
			return this.user;
		}
		StandardUsernamePasswordCredentials sc = getCredentials(this.credentialsId);
		if (sc != null) {
			return sc.getUsername();
		}
		return null;
	}

	public String getPwd() {
		// To ensure backward compatibility we are using saved user details
		if (!GenericUtils.isEmpty(this.pwd)) {
			return this.pwd;
		}
		StandardUsernamePasswordCredentials sc = getCredentials(this.credentialsId);
		if (sc != null && sc.getPassword() != null) {
			return sc.getPassword().getPlainText();
		}
		return null;
	}

	public StandardUsernamePasswordCredentials getCredentials(String credentialsId) {
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

	public String getToolId() {
		return toolId;
	}

	public String getSnArtifactToolId() {
		return snArtifactToolId;
	}

	public String getLogLevel() {
		return logLevel;
	}

	public FormValidation doCheckInstanceUrl(@QueryParameter("instanceUrl") String snInstanceUrl)
			throws IOException, ServletException {
		if (GenericUtils.isEmpty(snInstanceUrl))
			return FormValidation.error("Please provide a valid instance URL");
		if (snInstanceUrl.length() > 0 && !snInstanceUrl.startsWith("http"))
			return FormValidation.error("URL must start with http/https");
		if (snInstanceUrl.length() > 9 && !GenericUtils.checkUrlValid(snInstanceUrl))
			return FormValidation.error("Invalid URL");
		return FormValidation.ok();
	}

	public FormValidation doCheckApiVersion(@QueryParameter("apiVersion") String snApiVersion)
			throws IOException, ServletException {
		if (GenericUtils.isEmpty(snApiVersion))
			return FormValidation.error("Please provide an api version");
		return FormValidation.ok();
	}

	public FormValidation doCheckCredentialsId(@QueryParameter("credentialsId") String credentialsId)
			throws IOException, ServletException {

		List<DomainRequirement> drl = null;
		ItemGroup itemGroup = null;
		Authentication authentication = null;
		if (GenericUtils.isEmpty(credentialsId))
			return FormValidation.error("Please choose a credential!");
		if (CredentialsProvider.listCredentials(StandardUsernamePasswordCredentials.class, itemGroup, authentication,
				drl, CredentialsMatchers.withId(credentialsId)).isEmpty())
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
	public FormValidation doTestConnection(@QueryParameter("instanceUrl") String instanceUrl,
			@QueryParameter("apiVersion") String apiVersion, @QueryParameter("toolId") String toolId,
			@QueryParameter("credentialsId") String credentialsId) throws IOException, ServletException {

		List<DomainRequirement> drl = null;
		ItemGroup itemGroup = null;
		Authentication authentication = null;

		if (GenericUtils.isEmpty(instanceUrl))
			return FormValidation.error("Please provide the url!");

		if (GenericUtils.isEmpty(credentialsId))
			return FormValidation.error("Please choose a credential!");

		if (CredentialsProvider.listCredentials(StandardUsernamePasswordCredentials.class, itemGroup, authentication,
				drl, CredentialsMatchers.withId(credentialsId)).isEmpty())
			return FormValidation.error("Cannot find currently selected credentials");

		if (GenericUtils.isEmpty(toolId))
			return FormValidation.error("Invalid tool id!");

		if (GenericUtils.isEmpty(apiVersion))
			return FormValidation.error("Invalid API Version!");

		String changeControlUrl = getChangeControlUrl(instanceUrl, apiVersion);

		LOGGER.log(Level.INFO, "changeControlUrl ->" + changeControlUrl);

		if (GenericUtils.isEmpty(changeControlUrl) || !GenericUtils.checkUrlValid(changeControlUrl)) {
			return FormValidation.error("Invalid URL");
		}

		StandardUsernamePasswordCredentials credentials = getCredentials(credentialsId);
		String user = null;
		String pwd = null;
		if (credentials != null) {
			user = credentials.getUsername();
			if (credentials.getPassword() != null) {
				pwd = credentials.getPassword().getPlainText();
			}
		}

		JSONObject params = new JSONObject();
		params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), toolId);
		params.put(DevOpsConstants.TEST_CONNECTION_ATTR.toString(), "true");
		params.put(DevOpsConstants.TOOL_TYPE_ATTR.toString(), DevOpsConstants.TOOL_TYPE.toString());
		try {
			String result = GenericUtils.parseResponseResult(
					CommUtils.call("GET", changeControlUrl, params, null, user, pwd, null, null),
					DevOpsConstants.TEST_CONNECTION_RESPONSE_ATTR.toString());
			if (result != null && result.equalsIgnoreCase("OK"))
				return FormValidation.ok("Connection successful!");
			else
				throw new Exception("Connection failed!");
		} catch (Exception e) {
			return FormValidation.error("Client error : " + e.getMessage());
		}

	}

	private String getTrimmedUrl(String url) {
		return GenericUtils.isNotEmpty(url) ? url.endsWith("/") ? url.substring(0, url.length() - 1) : url : null;
	}

	private String getChangeControlUrl(String instanceUrl, String apiVersion) {

		return GenericUtils.isNotEmpty(instanceUrl)
				? String.format("%s/api/sn_devops/%s/devops/orchestration" + "/changeControl",
						getTrimmedUrl(instanceUrl), apiVersion)
				: null;
	}

	private String getTrackingUrl(String instanceUrl, String apiVersion) {

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

	public ListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId) {
		if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
			return new StandardListBoxModel().includeCurrentValue(credentialsId);
		}

		credentialsId = GenericUtils.isEmpty(credentialsId) ? this.credentialsId : credentialsId;
		AbstractIdCredentialsListBoxModel<StandardListBoxModel, StandardCredentials> options = new StandardListBoxModel()
				.includeEmptyValue().includeAs(ACL.SYSTEM, Jenkins.get(), StandardUsernamePasswordCredentials.class)
				.includeCurrentValue(credentialsId);
		for (ListBoxModel.Option option : options) {
			if (option.value.equals(credentialsId)) {
				option.selected = true;
			}
		}
		return options;
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
						getApiVersion())
				: null;
	}
	
	public ListBoxModel doFillLogLevelItems(@QueryParameter String logLevel) {
		ListBoxModel options = new ListBoxModel();
		options.add("inherit");
		options.add("off");
		options.add("severe");
		options.add("warning");
		options.add("info");
		options.add("config");
		options.add("fine");
		options.add("finer");
		options.add("finest");
		options.add("all");
		for (ListBoxModel.Option option : options) {
			if (option.value.equals(logLevel)) {
				option.selected = true;
			}
		}
		return options;
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
}