package io.jenkins.plugins.pipeline.steps;

import java.io.Serializable;
import java.util.Set;

import hudson.util.ListBoxModel;
import io.jenkins.plugins.utils.GenericUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineRegisterSecurityStepExecution;
import io.jenkins.plugins.utils.DevOpsConstants;
import org.kohsuke.stapler.QueryParameter;

public class DevOpsPipelineRegisterSecurityStep extends Step implements Serializable, DevOpsStep {
	private String securityResultAttributes;
	private String configurationName;

	public DevOpsPipelineRegisterSecurityStep() {
	}

	@DataBoundConstructor
	public DevOpsPipelineRegisterSecurityStep(String securityResultAttributes) {
		this.securityResultAttributes = securityResultAttributes;
		configurationName = null;
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

	public String getSecurityResultAttributes() {
		return securityResultAttributes;
	}

	@DataBoundSetter
	public void setSecurityResultAttributes(String securityResultAttributes) {
		this.securityResultAttributes = securityResultAttributes;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new DevOpsPipelineRegisterSecurityStepExecution(context, this);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return DevOpsConstants.SECURITY_RESULT_STEP_FUNCTION_NAME.toString();
		}

		@Override
		public String getDisplayName() {
			return DevOpsConstants.SECURITY_RESULT_STEP_DISPLAY_NAME.toString();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
		}

		public ListBoxModel doFillSecurityToolItems(@QueryParameter String securityTool) {
			ListBoxModel options = new ListBoxModel();
			options.add("Veracode");
			options.add("Checkmarx One");
			options.add("Checkmarx SAST");
			options.add("Others");
			for (ListBoxModel.Option option : options) {
				if(GenericUtils.isEmpty(securityTool)){
					option.selected = true;
					break;
				}
				if (option.value.equals(securityTool)) {
					option.selected = true;
				}
			}
			return options;
		}
	}
}
