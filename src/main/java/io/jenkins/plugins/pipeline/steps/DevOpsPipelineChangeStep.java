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
import io.jenkins.plugins.config.DevOpsConfigurationEntry;
import io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineChangeStepExecution;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;

public class DevOpsPipelineChangeStep extends Step implements Serializable, DevOpsStep {
	private static final long serialVersionUID = 1L;
	private String configurationName;
	private boolean enabled;
	private boolean ignoreErrors;
	private String changeRequestDetails;
	private String applicationName;
	private String snapshotName;
	private int pollingInterval;
	private int changeCreationTimeOut;
	private boolean abortOnChangeCreationFailure;
	private int changeStepTimeOut;
	private boolean abortOnChangeStepTimeOut;
	@DataBoundConstructor
	public DevOpsPipelineChangeStep() {
		enabled = true;
		ignoreErrors = false;
		changeRequestDetails = null;
		abortOnChangeCreationFailure = true;
		abortOnChangeStepTimeOut = true;
		configurationName = null;
	}
	public int getPollingInterval() {
		return pollingInterval;
	}
	@DataBoundSetter
	public void setPollingInterval(Object pollingInterval) {
		if(pollingInterval != null)
			this.pollingInterval = pollingInterval.toString().matches("\\d+(\\.\\d+)?") ? (int)Float.parseFloat(pollingInterval.toString()) : 0;
	}
	public int getChangeCreationTimeOut() {
		return changeCreationTimeOut;
	}
	@DataBoundSetter
	public void setChangeCreationTimeOut(Object changeCreationTimeOut) {
		if(changeCreationTimeOut != null)
			this.changeCreationTimeOut = changeCreationTimeOut.toString().matches("\\d+(\\.\\d+)?") ? (int)Float.parseFloat(changeCreationTimeOut.toString()) : 0;
	}
	public boolean isAbortOnChangeCreationFailure() {
		return abortOnChangeCreationFailure;
	}
	@DataBoundSetter
	public void setAbortOnChangeCreationFailure(boolean abortOnChangeCreationFailure) {
		this.abortOnChangeCreationFailure = abortOnChangeCreationFailure;
	}
	public int getChangeStepTimeOut() {
		return changeStepTimeOut;
	}
	@DataBoundSetter
	public void setChangeStepTimeOut(Object changeStepTimeOut) {
		if(changeStepTimeOut != null)
			this.changeStepTimeOut = changeStepTimeOut.toString().matches("\\d+(\\.\\d+)?") ? (int)Float.parseFloat(changeStepTimeOut.toString()) : 0;
	}
	public boolean isAbortOnChangeStepTimeOut() {
		return abortOnChangeStepTimeOut;
	}
	@DataBoundSetter
	public void setAbortOnChangeStepTimeOut(boolean abortOnChangeStepTimeOut) {
		this.abortOnChangeStepTimeOut = abortOnChangeStepTimeOut;
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

	@Override
	public String getConfigurationName() {
		return configurationName;
	}

	@DataBoundSetter
	public void setConfigurationName(String configurationName) {
		if(!GenericUtils.isEmpty(configurationName))
			this.configurationName = configurationName;
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