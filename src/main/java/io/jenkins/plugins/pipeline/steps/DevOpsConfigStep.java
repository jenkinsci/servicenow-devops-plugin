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

import io.jenkins.plugins.pipeline.steps.executions.DevOpsConfigStepExecution;
import io.jenkins.plugins.utils.DevOpsConstants;

public class DevOpsConfigStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;
    private boolean m_enabled;
    private boolean m_ignoreErrors;

    private String applicationName;
    private String changesetNumber;
    private String target;
    private String configFile;
    private String namePath;
    private String deployableName;
    private String collectionName;
    private String dataFormat;
    private boolean convertPath;
    private boolean markFailed;
    private boolean showResults;
    private String testResultFormat;
    private boolean isValidated;
    private String autoPublish;
    private String autoValidate;
    private String autoCommit;

    @DataBoundConstructor
    public DevOpsConfigStep(String applicationName, String target, String namePath, String configFile, String dataFormat) {
        m_enabled  =true;
        m_ignoreErrors = false;
        this.applicationName = applicationName;
        this.target = target;
        this.namePath = namePath;
        this.configFile = configFile;
        this.dataFormat = dataFormat;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new DevOpsConfigStepExecution(context, this);
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
            this.deployableName = null;
        else
            this.deployableName = deployableName;
   }

    public String getDeployableName() {
        return deployableName;
    }

    @DataBoundSetter
   public void setCollectionName(String collectionName) {
        if(collectionName == null || collectionName.isEmpty())
            this.collectionName = null;
        else
            this.collectionName = collectionName;
   }

    public String getCollectionName() {
        return collectionName;
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

   public void setApplicationName(String applicationName) {
       this.applicationName = applicationName;
   }

   public String getApplicationName() {
       return applicationName;
   }

   public void setTarget(String target) {
       this.target = target;
   }

   public String getTarget() {
       return target;
   }

   public void setConfigFile(String configFile) {
       this.configFile = configFile;
   }

   public String getConfigFile() {
       return configFile;
   }

    public void setNamePath(String namePath) {
        this.namePath = namePath;
    }

    public String getNamePath() {
        return namePath;
    }

    public void setDataFormat(String dataFormat) {
        this.dataFormat = dataFormat;
    }

    public String getDataFormat() {
        return dataFormat;
    }

    @DataBoundSetter
    public void setTestResultFormat(String testResultFormat) {
        if(testResultFormat == null || testResultFormat.isEmpty())
            this.testResultFormat = null;
        else
            this.testResultFormat = testResultFormat;
    }

    public String getTestResultFormat() {
        return testResultFormat;
    }

    @DataBoundSetter
    public void setIsValidated(boolean isValidated) {
          this.isValidated = isValidated;
    }

    public boolean getIsValidated() {
        return isValidated;
    }

    public String getAutoPublish() {
        return autoPublish;
    }

    @DataBoundSetter
    public void setAutoPublish(String autoPublish) {
        if(autoPublish == null || autoPublish.isEmpty())
            this.autoPublish = "true";
        else
            this.autoPublish = autoPublish;
   }

    public String getAutoValidate() {
        return autoValidate;
    }

    @DataBoundSetter
    public void setAutoValidate(String autoValidate) {
        if(autoValidate == null || autoValidate.isEmpty())
            this.autoValidate = "true";
        else
            this.autoValidate = autoValidate;
    }

    public String getAutoCommit() {
        return autoCommit;
    }

    @DataBoundSetter
    public void setAutoCommit(String autoCommit) {
        if(autoCommit == null || autoCommit.isEmpty())
            this.autoCommit = "true";
        else
            this.autoCommit = autoCommit;
    }

    @Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return DevOpsConstants.CONFIG_PIPELINE_STEP_FUNCTION_NAME.toString();
		}

		@Override
		public String getDisplayName() {
			return DevOpsConstants.CONFIG_PIPELINE_STEP_DISPLAY_NAME.toString();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
		}

	}
    
}
