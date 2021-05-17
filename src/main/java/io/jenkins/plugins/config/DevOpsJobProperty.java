package io.jenkins.plugins.config;

import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import net.sf.json.JSONObject;

public class DevOpsJobProperty extends JobProperty<Job<?, ?>> {

	private boolean ignoreSNErrors;
	private String changeRequestDetails;


	public DevOpsJobProperty() {
		ignoreSNErrors = false;
	}

	public boolean isIgnoreSNErrors() {
		return ignoreSNErrors;
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
					property.setChangeRequestDetails(formData.getString("changeRequestDetails"));
				}
			}
			return property;
		}

		@Override
		public String getDisplayName() {
			return "ServiceNow DevOps Job Settings";
		}

	}
}