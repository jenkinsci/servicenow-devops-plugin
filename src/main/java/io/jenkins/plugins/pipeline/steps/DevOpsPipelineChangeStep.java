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
import io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineChangeStepExecution;
import io.jenkins.plugins.utils.DevOpsConstants;

public class DevOpsPipelineChangeStep extends Step implements Serializable {
	private static final long serialVersionUID = 1L;
	private boolean m_enabled;
	private boolean m_ignoreErrors;
	private String m_changeRequestDetails;
	
	@DataBoundConstructor
    public DevOpsPipelineChangeStep() {
		m_enabled = true;
		m_ignoreErrors = false;
		m_changeRequestDetails = null;
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

	public String getChangeRequestDetails() {
		return m_changeRequestDetails;
	}

	@DataBoundSetter
	public void setChangeRequestDetails(String changeRequestDetails) {
		this.m_changeRequestDetails = changeRequestDetails;
	}
	
	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new DevOpsPipelineChangeStepExecution(context, this);
	}
	
	@Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }
	
	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return DevOpsConstants.CHANGE_FUNCTION_NAME.toString();
		}

		@Override
		public String getDisplayName() {
			return DevOpsConstants.CHANGE_DISPLAY_NAME.toString();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
		}
		
	}
	
}