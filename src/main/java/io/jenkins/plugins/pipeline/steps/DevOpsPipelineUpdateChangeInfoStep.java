package io.jenkins.plugins.pipeline.steps;

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineUpdateChangeInfoStepExecution;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.Set;

public class DevOpsPipelineUpdateChangeInfoStep extends Step implements Serializable, DevOpsStep {

	private String changeRequestNumber;
	private String changeRequestDetails;
	private String configurationName;

	public String getChangeRequestDetails() {
		return changeRequestDetails;
	}

	@Override
	public String getConfigurationName() {
		return configurationName;
	}

	@DataBoundSetter
	public void setConfigurationName(String configurationName) {
		if (!GenericUtils.isEmpty(configurationName))
			this.configurationName = configurationName;
	}

	@DataBoundSetter
	public void setChangeRequestDetails(String changeRequestDetails) {
		this.changeRequestDetails = changeRequestDetails;
	}

	public String getChangeRequestNumber() {
		return changeRequestNumber;
	}

	@DataBoundSetter
	public void setChangeRequestNumber(String changeRequestNumber) {
		this.changeRequestNumber = changeRequestNumber;
	}

	@DataBoundConstructor
	public DevOpsPipelineUpdateChangeInfoStep() {

	}

	@Override
	public StepExecution start(StepContext stepContext) throws Exception {
		return new DevOpsPipelineUpdateChangeInfoStepExecution(stepContext, this);
	}


	@Override
	public DevOpsPipelineUpdateChangeInfoStep.DescriptorImpl getDescriptor() {
		return (DevOpsPipelineUpdateChangeInfoStep.DescriptorImpl) super.getDescriptor();
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return DevOpsConstants.CHANGE_REQUEST_UPDATE_INFO_FUNCTION_NAME.toString();
		}

		@Override
		public String getDisplayName() {
			return DevOpsConstants.CHANGE_REQUEST_UPDATE_INFO_DISPLAY_NAME.toString();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
		}

	}

}
