package io.jenkins.plugins.pipeline.steps.executions;

import java.util.logging.Level;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.config.DevOpsConfigurationEntry;
import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.freestyle.steps.DevOpsRegisterArtifactBuildStep;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.model.DevOpsPipelineInfoConfig;
import io.jenkins.plugins.pipeline.steps.DevOpsPipelineRegisterArtifactStep;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;

/**
 *
 */
public class DevOpsPipelineRegisterArtifactStepExecution extends SynchronousStepExecution<Boolean> {

	private static final long serialVersionUID = 1L;

	private DevOpsPipelineRegisterArtifactStep step;

	public DevOpsPipelineRegisterArtifactStepExecution(StepContext context, DevOpsPipelineRegisterArtifactStep step) {
		super(context);
		this.step = step;
	}

	@Override
	protected Boolean run() throws Exception {
		try {

			Run<?, ?> run = getContext().get(Run.class);
			TaskListener listener = getContext().get(TaskListener.class);
			FilePath workspace = getContext().get(FilePath.class);
			Launcher launcher = getContext().get(Launcher.class);
			EnvVars envVars = getContext().get(EnvVars.class);
			DevOpsModel model = new DevOpsModel();
			DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());
			String pronoun = run.getParent().getPronoun();
			boolean pipelineTrack = model.checkIsTrackingCache(run.getParent(), run.getId());
			boolean isPullRequestPipeline = pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString());

			DevOpsConfigurationEntry devopsConfig = GenericUtils.getDevOpsConfigurationEntryOrDefault(this.step.getConfigurationName());
			if (devopsConfig == null)
				return GenericUtils.handleConfigurationNotFound(this.step, jobProperties, listener, getContext(), false, this.step.isIgnoreErrors());
			String devopsConfigMessage = String.format("[ServiceNow DevOps] Using DevOps configuration %s", devopsConfig.getName());
			listener.getLogger().println(devopsConfigMessage);
			GenericUtils.printDebug(DevOpsPipelineRegisterArtifactStepExecution.class.getName(), "run", new String[] { "configurationName" }, new String[] { devopsConfig.getName() }, Level.FINE);

			DevOpsModel.DevOpsPipelineInfo pipelineInfo = model.checkIsTracking(run.getParent(), run.getId(), envVars.get("BRANCH_NAME"));
			DevOpsPipelineInfoConfig pipelineInfoConfig = GenericUtils.getPipelineInfoConfigFromConfigEntry(pipelineInfo, devopsConfig);

			if (step.isEnabled() && pipelineTrack && pipelineInfoConfig != null && pipelineInfoConfig.isTrack() && ((isPullRequestPipeline && devopsConfig.getTrackPullRequestPipelinesCheck()) || (!isPullRequestPipeline))) {
				DevOpsRegisterArtifactBuildStep registerArtifactBuildStep = new DevOpsRegisterArtifactBuildStep();
				registerArtifactBuildStep.setArtifactsPayload(this.step.getArtifactsPayload());
				registerArtifactBuildStep.setConfigurationName(this.step.getConfigurationName());
				registerArtifactBuildStep.perform(getContext(), run, workspace, launcher, listener, envVars, step.isIgnoreErrors());
			} else if (pipelineInfoConfig != null && !pipelineInfoConfig.isTrack())
				listener.getLogger().println("[ServiceNow DevOps] Pipeline is not tracked");

			return Boolean.valueOf(true);
		} catch (Exception e) {
			TaskListener listener = getContext().get(TaskListener.class);
			listener.getLogger().println("[ServiceNow DevOps] Error occurred while registering the artifact, Exception: " + e.getMessage());
			throw e;
		}
	}

}
