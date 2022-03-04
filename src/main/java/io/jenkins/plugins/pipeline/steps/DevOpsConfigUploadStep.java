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
    private String configFile;
    private String namePath;
    private boolean autoCommit;
    private boolean autoValidate;
    private String deployableName;
    private String dataFormat;
    private boolean convertPath;
    private boolean markFailed;
    private boolean showResults;
   

    @DataBoundConstructor
    public DevOpsConfigUploadStep(String applicationName, String target, String namePath, String configFile, boolean autoCommit, boolean autoValidate, String dataFormat) {
        m_enabled  =true;
        m_ignoreErrors = false;
        this.applicationName = applicationName;
        this.target = target;
        this.namePath = namePath;
        this.configFile = configFile;
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
   public void setConvertPath(boolean convertPath) {
        if(!convertPath)
            this.convertPath = false;
        else
            this.convertPath = convertPath;
   }

    public boolean getConvertPath() {
        return convertPath;
    }

    @DataBoundSetter
    public void setMarkFailed(boolean markFailed) {
        this.markFailed = markFailed;
    }
 
    public boolean getMarkFailed() {
        return markFailed;
    }

    @DataBoundSetter
    public void setShowResults(boolean showResults) {
        this.showResults = showResults;
    }
 
    public boolean getShowResults() {
        return showResults;
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
   public void setConfigFile(String configFile) {
       this.configFile = configFile;
   }

   public String getConfigFile() {
       return configFile;
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
    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public boolean getAutoCommit() {
        return autoCommit;
    }

    public boolean getAutoValidate() {
        return autoValidate;
    }

    @DataBoundSetter
    public void setAutoValidate(boolean autoValidate) {
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