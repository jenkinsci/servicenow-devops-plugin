package io.jenkins.plugins.pipeline.steps;

import java.io.Serializable;
import java.util.Set;

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

public class DevOpsPipelineRegisterSecurityStep extends Step implements Serializable {
	private String securityResultAttributes;


	public DevOpsPipelineRegisterSecurityStep() {
	}

	@DataBoundConstructor
	public DevOpsPipelineRegisterSecurityStep(String securityResultAttributes) {
		this.securityResultAttributes = securityResultAttributes;
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
	}
}
