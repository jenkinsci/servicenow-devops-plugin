package io.jenkins.plugins.pipeline.steps;

import java.io.Serializable;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineChageInfoStepExecution;
import io.jenkins.plugins.utils.DevOpsConstants;

public class DevOpsPipelineChangeInfoStep extends Step implements Serializable {


	@DataBoundConstructor
	public  DevOpsPipelineChangeInfoStep() {

	}

	@Override
	public StepExecution start(StepContext stepContext) throws Exception {
		return new DevOpsPipelineChageInfoStepExecution(stepContext);
	}


	@Override
	public DevOpsPipelineChangeInfoStep.DescriptorImpl getDescriptor() {
		return (DevOpsPipelineChangeInfoStep.DescriptorImpl) super.getDescriptor();
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return DevOpsConstants.CHANGE_REQUEST_INFO_FUNCTION_NAME.toString();
		}

		@Override
		public String getDisplayName() {
			return DevOpsConstants.CHANGE_REQUEST_INFO_DISPLAY_NAME.toString();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
		}

	}

}
