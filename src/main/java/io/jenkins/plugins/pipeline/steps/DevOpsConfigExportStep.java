package io.jenkins.plugins.pipeline.steps;

import java.io.Serializable;
import net.sf.json.JSONObject;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pipeline.steps.executions.DevOpsConfigExportStepExecution;
import io.jenkins.plugins.utils.DevOpsConstants;

public class DevOpsConfigExportStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;
    private boolean m_enabled;
	private boolean m_ignoreErrors;
	private String applicationName;
	private String deployableName;
	private String exporterName;
	private String exporterFormat;
	private JSONObject exporterArgs;
	private String fileName;
	private String snapshotName;

	@DataBoundConstructor
	public DevOpsConfigExportStep(String applicationName, String deployableName, String exporterName, String exporterFormat, String fileName) {
		m_enabled  =true;
		m_ignoreErrors = false;
		
		this.applicationName = applicationName;
		this.deployableName = deployableName;
		this.exporterName = exporterName;
		this.exporterFormat = exporterFormat;
		this.fileName = fileName;
	}

	@Override
    public StepExecution start(StepContext context) throws Exception {
        return new DevOpsConfigExportStepExecution(context, this);
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
	public void setExporterArgs(String exporterArgs) {
		if(exporterArgs == null || exporterArgs.isEmpty())
			this.exporterArgs = null;
		else {
			try {
				JSONObject arguments = null;
				arguments = JSONObject.fromObject(exporterArgs);
				this.exporterArgs = arguments;
			} catch(Exception e) {
				Logger.getLogger(e.getMessage());
			}
		}
	}

	public JSONObject getExporterArgs() {
		return exporterArgs;
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

	@DataBoundSetter
	public void setExporterName(String exporterName) {
		this.exporterName = exporterName;
	}

	public String getExporterName() {
		return exporterName;
	}

	@DataBoundSetter
	public void setExporterFormat(String exporterFormat) {
		this.exporterFormat = exporterFormat;
	}

	public String getExporterFormat() {
		return exporterFormat;
	}

	@DataBoundSetter
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFileName() {
		return fileName;
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return DevOpsConstants.CONFIG_EXPORT_STEP_FUNCTION_NAME.toString();
		}

		@Override
		public String getDisplayName() {
			return DevOpsConstants.CONFIG_EXPORT_STEP_DISPLAY_NAME.toString();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
		}

	}
}
