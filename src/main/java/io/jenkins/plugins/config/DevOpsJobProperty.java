package io.jenkins.plugins.config;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.utils.DevOpsConstants;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.List;

public class DevOpsJobProperty extends JobProperty<Job<?, ?>> {

	private boolean ignoreSNErrors;
	private String changeRequestDetails;
	private String configurationName;


	public DevOpsJobProperty() {
		ignoreSNErrors = false;
	}

	public boolean isIgnoreSNErrors() {
		return ignoreSNErrors;
	}

	public String getConfigurationName() {
		return configurationName;
	}

	@DataBoundSetter
	public void setConfigurationName(String configurationName) {
		this.configurationName = configurationName;
	}

	@DataBoundSetter
	public void setIgnoreSNErrors(boolean ignoreSNErrors) {
		this.ignoreSNErrors = ignoreSNErrors;
	}


	public String getChangeRequestDetails() {
		return changeRequestDetails;
	}

	@DataBoundSetter
	public void setChangeRequestDetails(String changeRequestDetails) {
		this.changeRequestDetails = changeRequestDetails;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	public boolean isFreeStyle() {
		String pronoun = this.owner != null ? this.owner.getPronoun() : "";
		if(pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) ||
				pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString())) {
			return true;
		}
		return false;
	}

	@Extension
	public static class DescriptorImpl extends JobPropertyDescriptor {
		@Override
		public boolean isApplicable(
				@SuppressWarnings("rawtypes") Class<? extends Job> jobType) {
			return true;
		}


		@Override
		public DevOpsJobProperty newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
			DevOpsJobProperty property = new DevOpsJobProperty();
			if (formData != null && !formData.isNullObject()) {
				if (!formData.isEmpty()) {
					property.setIgnoreSNErrors(formData.getBoolean("ignoreSNErrors"));
					Object changeRequestDetails = formData.get("changeRequestDetails");
					if (changeRequestDetails != null)
						property.setChangeRequestDetails(formData.getString("changeRequestDetails"));
					Object configurationName = formData.get("configurationName");
					if (configurationName != null)
						property.setConfigurationName(formData.getString("configurationName"));
				}
			}
			return property;
		}

		public ListBoxModel doFillConfigurationNameItems() {
			DevOpsConfiguration configuration = DevOpsConfiguration.get();
			List<DevOpsConfigurationEntry> existingEntries = configuration.getEntries();
			ListBoxModel items = new ListBoxModel();
			items.add("", "");
			for (DevOpsConfigurationEntry entry : existingEntries) {
				if(StringUtils.isNotBlank(entry.getName())) {
					items.add(entry.getName(), entry.getName());
				}
			}
			return items;
		}

		@Override
		public String getDisplayName() {
			return "ServiceNow DevOps Job Settings";
		}

	}
}