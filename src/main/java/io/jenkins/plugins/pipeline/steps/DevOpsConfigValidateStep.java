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
import io.jenkins.plugins.pipeline.steps.executions.DevOpsConfigValidateStepExecution;
import io.jenkins.plugins.utils.DevOpsConstants;

public class DevOpsConfigValidateStep extends Step implements Serializable{
    
    private static final long serialVersionUID = 1L;
    private boolean m_enabled;
    private boolean m_ignoreErrors;
    private String applicationName;
    private String deployableName;
    private String snapshotName;
    private boolean markFailed;
    private boolean showResults;

    @DataBoundConstructor
    public DevOpsConfigValidateStep(String applicationName, String deployableName) {
        m_enabled = true;
        m_ignoreErrors = false;
        this.applicationName = applicationName;
        this.deployableName = deployableName;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new DevOpsConfigValidateStepExecution(context, this);
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

    @DataBoundSetter
    public void setSnapshotName(String snapshotName) {
         if(snapshotName == null || snapshotName.isEmpty())
             this.snapshotName = null;
         else
             this.snapshotName = snapshotName;
    }
 
    public String getSnapshotName() {
        return snapshotName;
    }

    @DataBoundSetter
    public void setMarkFailed(boolean markFailed) {
         if(!markFailed)
             this.markFailed = false;
         else
             this.markFailed = markFailed;
    }
 
    public boolean getMarkFailed() {
        return markFailed;
    }

    @DataBoundSetter
    public void setShowResults(boolean showResults) {
         if(!showResults)
             this.showResults = false;
         else
             this.showResults = showResults;
    }
 
    public boolean getShowResults() {
        return showResults;
    }

    @DataBoundSetter
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    @DataBoundSetter
    public void setDeployableName(String deployableName) {
        this.deployableName = deployableName;
    }

    public String getDeployableName() {
        return deployableName;
    }

    @Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return DevOpsConstants.CONFIG_VALIDATE_STEP_FUNCTION_NAME.toString();
		}

		@Override
		public String getDisplayName() {
			return DevOpsConstants.CONFIG_VALIDATE_STEP_DISPLAY_NAME.toString();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
		}

	}
}
