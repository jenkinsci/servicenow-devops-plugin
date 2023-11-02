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
    private Boolean autoDelete;
    private boolean autoValidate;
    private String deployableName;
    private String collectionName;
    private String dataFormat;
    private boolean convertPath;
    private boolean markFailed;
    private boolean showResults;
    private boolean autoPublish;
   

    @DataBoundConstructor
    public DevOpsConfigUploadStep(String applicationName, String target, String namePath, String configFile, boolean autoCommit, Boolean autoDelete, boolean autoValidate, boolean autoPublish) {
        m_enabled  =true;
        m_ignoreErrors = false;
        this.applicationName = applicationName;
        this.target = target;
        this.namePath = namePath;
        this.configFile = configFile;
        this.autoCommit = autoCommit;
        this.autoDelete = autoDelete;
        this.autoValidate = autoValidate;
        this.autoPublish = autoPublish;
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

    @DataBoundSetter
    public void setDataFormat(String dataFormat) {
        this.dataFormat = dataFormat;
        if(dataFormat == null || dataFormat.isEmpty())
            this.dataFormat = null;
        else
            this.dataFormat = dataFormat;
    }

    public String getDataFormat() {
        return dataFormat;
    }

    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public boolean getAutoCommit() {
        return autoCommit;
    }

    public void setAutoDelete(Boolean autoDelete) {
        this.autoDelete = autoDelete;
    }

    public Boolean getAutoDelete() {
        return autoDelete;
    }

    public boolean getAutoValidate() {
        return autoValidate;
    }

    public void setAutoValidate(boolean autoValidate) {
        this.autoValidate = autoValidate;
    }

    public void setAutoPublish(boolean autoPublish) {
        this.autoPublish = autoPublish;
    }

    public boolean getAutoPublish() {
        return autoPublish;
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