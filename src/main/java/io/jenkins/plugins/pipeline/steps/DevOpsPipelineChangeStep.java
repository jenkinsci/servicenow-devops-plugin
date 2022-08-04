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
import io.jenkins.plugins.utils.GenericUtils;

public class DevOpsPipelineChangeStep extends Step implements Serializable {
	private static final long serialVersionUID = 1L;
	private boolean enabled;
	private boolean ignoreErrors;
	private String changeRequestDetails;
	private String applicationName;
	private String snapshotName;
	
	@DataBoundConstructor
    public DevOpsPipelineChangeStep() {
		enabled = true;
		ignoreErrors = false;
		changeRequestDetails = null;
    }

	public boolean isEnabled() {
		return enabled;
	}

	@DataBoundSetter
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isIgnoreErrors() {
		return ignoreErrors;
	}

	@DataBoundSetter
	public void setIgnoreErrors(boolean ignore) {
		this.ignoreErrors = ignore;
	}

	public String getChangeRequestDetails() {
		return changeRequestDetails;
	}

	@DataBoundSetter
	public void setChangeRequestDetails(String changeRequestDetails) {
		if(!GenericUtils.isEmpty(changeRequestDetails))
			this.changeRequestDetails = changeRequestDetails;
	}

	public String getApplicationName() {
		return applicationName;
	}

	@DataBoundSetter
	public void setApplicationName(String applicationName) {
		if(applicationName == null || applicationName.isEmpty())
			this.applicationName = null;
		else
			this.applicationName = applicationName;
	}

	public String getSnapshotName() {
		return snapshotName;
	}

	@DataBoundSetter
	public void setSnapshotName(String snapshotName) {
		if(snapshotName == null || snapshotName.isEmpty())
			this.snapshotName = null;
		else
			this.snapshotName = snapshotName;
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