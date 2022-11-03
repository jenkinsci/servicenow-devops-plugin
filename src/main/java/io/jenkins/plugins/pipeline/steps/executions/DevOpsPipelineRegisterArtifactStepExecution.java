package io.jenkins.plugins.pipeline.steps.executions;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.freestyle.steps.DevOpsRegisterArtifactBuildStep;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsPipelineRegisterArtifactStep;
import io.jenkins.plugins.utils.DevOpsConstants;

/**
 *
 */
public class DevOpsPipelineRegisterArtifactStepExecution extends SynchronousStepExecution<Boolean> {

	private static final long serialVersionUID = 1L;

	private DevOpsPipelineRegisterArtifactStep step;

	public DevOpsPipelineRegisterArtifactStepExecution(StepContext context,
	                                                   DevOpsPipelineRegisterArtifactStep step) {
		super(context);
		this.step = step;
	}

	@Override
	protected Boolean run() throws Exception {

		Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);
		FilePath workspace = getContext().get(FilePath.class);
		Launcher launcher = getContext().get(Launcher.class);
		EnvVars envVars = getContext().get(EnvVars.class);
		DevOpsModel model = new DevOpsModel();
		String pronoun = run.getParent().getPronoun();
		boolean pipelineTrack = model.checkIsTrackingCache(run.getParent(), run.getId());
		boolean isPullRequestPipeline = pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString());
		DevOpsConfiguration devopsConfig = DevOpsConfiguration.get();
		if (step.isEnabled() && pipelineTrack &&
				((isPullRequestPipeline && devopsConfig.isTrackPullRequestPipelinesCheck()) ||
						(!isPullRequestPipeline))) {
			DevOpsRegisterArtifactBuildStep registerArtifactBuildStep = new DevOpsRegisterArtifactBuildStep();
			registerArtifactBuildStep.setArtifactsPayload(this.step.getArtifactsPayload());

			registerArtifactBuildStep.perform(getContext(), run, workspace, launcher, listener, envVars, step.isIgnoreErrors());
		}

		return Boolean.valueOf(true);
	}

}
