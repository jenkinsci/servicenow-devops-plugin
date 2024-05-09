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
import io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineMapStepExecution;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;

public class DevOpsPipelineMapStep extends Step implements Serializable, DevOpsStep {
	private static final long serialVersionUID = 1L;
	private final String m_stepSysId;
	private boolean m_enabled;
	private boolean m_ignoreErrors;
	private String configurationName;

	@DataBoundConstructor
	public DevOpsPipelineMapStep() {

		m_stepSysId = null;
		m_enabled = true;
		m_ignoreErrors = false;
		configurationName = null;

	}

	public boolean isEnabled() {
		return m_enabled;
	}

	@DataBoundSetter
	public void setEnabled(boolean enabled) {
		this.m_enabled = enabled;
	}

	public boolean isIgnoreErrors() {
		return m_ignoreErrors;
	}

	@DataBoundSetter
	public void setIgnoreErrors(boolean ignore) {
		this.m_ignoreErrors = ignore;
	}

	public String getStepSysId() {
		return m_stepSysId;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new DevOpsPipelineMapStepExecution(context, this);
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
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

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return DevOpsConstants.MAP_FUNCTION_NAME.toString();
		}

		@Override
		public String getDisplayName() {
			return DevOpsConstants.MAP_DISPLAY_NAME.toString();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
		}

	}

}