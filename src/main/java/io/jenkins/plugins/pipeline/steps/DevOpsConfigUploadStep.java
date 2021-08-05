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
import io.jenkins.plugins.pipeline.steps.executions.DevOpsConfigUploadStepExecution;
import io.jenkins.plugins.utils.DevOpsConstants;


public class DevOpsConfigUploadStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;
    private boolean m_enabled;
    private boolean m_ignoreErrors;

    private String applicationName;
    private String changesetNumber;
    private String target;
    private String fileName;
    private String namePath;
    private String autoCommit;
    private String autoValidate;
    private String deployableName;
    private String dataFormat;
   

    @DataBoundConstructor
    public DevOpsConfigUploadStep(String applicationName, String target, String namePath, String fileName, String autoCommit, String autoValidate, String dataFormat) {
        m_enabled  =true;
        m_ignoreErrors = false;
        this.applicationName = applicationName;
        this.target = target;
        this.namePath = namePath;
        this.fileName = fileName;
        this.autoCommit = autoCommit;
        this.autoValidate = autoValidate;
        this.dataFormat = dataFormat;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new DevOpsConfigUploadStepExecution(context, this);
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
   public void setChangesetNumber(String changesetNumber) {
        if(changesetNumber == null || changesetNumber.isEmpty())
            this.changesetNumber = null;
        else
            this.changesetNumber = changesetNumber;
   }

   public String getChangesetNumber() {
       return changesetNumber;
   }

   @DataBoundSetter
   public void setDeployableName(String deployableName) {
        if(deployableName == null || deployableName.isEmpty())
            this.changesetNumber = null;
        else
            this.deployableName = deployableName;
   }

    public String getDeployableName() {
        return deployableName;
    }

   @DataBoundSetter
   public void setAppliactionName(String applicationName) {
       this.applicationName = applicationName;
   }

   public String getApplicationName() {
       return applicationName;
   }
   @DataBoundSetter
   public void setTarget(String target) {
       this.target = target;
   }

   public String getTarget() {
       return target;
   }

   @DataBoundSetter
   public void setFileName(String fileName) {
       this.fileName = fileName;
   }

   public String getFileName() {
       return fileName;
   }

    @DataBoundSetter
    public void setNamePath(String namePath) {
        this.namePath = namePath;
    }

    public String getNamePath() {
        return namePath;
    }

    @DataBoundSetter
    public void setDataFormat(String dataFormat) {
        this.dataFormat = dataFormat;
    }

    public String getDataFormat() {
        return dataFormat;
    }

    @DataBoundSetter
    public void setAutoCommit(String autoCommit) {
        this.autoCommit = autoCommit;
    }

    public String getAutoCommit() {
        return autoCommit;
    }

    public String getAutoValidate() {
        return autoValidate;
    }

    @DataBoundSetter
    public void setAutoValidate(String autoValidate) {
        this.autoValidate = autoValidate;
    }

    @Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString();
		}

		@Override
		public String getDisplayName() {
			return DevOpsConstants.CONFIG_UPLOAD_STEP_DISPLAY_NAME.toString();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
		}

	}
}