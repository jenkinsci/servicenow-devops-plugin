package io.jenkins.plugins.pipeline.steps.executions;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.freestyle.steps.DevOpsCreateArtifactPackageBuildStep;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsPipelineCreateArtifactPackageStep;
import io.jenkins.plugins.utils.DevOpsConstants;

/**
 *
 */
public class DevOpsPipelineCreateArtifactPackageStepExecution extends SynchronousStepExecution<Boolean> {

	private static final long serialVersionUID = 1L;

	private DevOpsPipelineCreateArtifactPackageStep step;

	public DevOpsPipelineCreateArtifactPackageStepExecution(StepContext context, DevOpsPipelineCreateArtifactPackageStep step) {
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
			String pronoun = run.getParent().getPronoun();
			boolean pipelineTrack = model.checkIsTrackingCache(run.getParent(), run.getId());
			boolean isPullRequestPipeline = pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString());
			DevOpsConfiguration devopsConfig = DevOpsConfiguration.get();
			if (pipelineTrack && ((isPullRequestPipeline && devopsConfig.isTrackPullRequestPipelinesCheck()) || (!isPullRequestPipeline))) {
				DevOpsCreateArtifactPackageBuildStep artifactPackageStep = new DevOpsCreateArtifactPackageBuildStep();
				artifactPackageStep.setArtifactsPayload(this.step.getArtifactsPayload());
				artifactPackageStep.setName(this.step.getName());

				artifactPackageStep.perform(getContext(), run, workspace, launcher, listener, envVars);
			}
			return Boolean.valueOf(true);
		} catch (Exception e) {
			TaskListener listener = getContext().get(TaskListener.class);
			listener.getLogger().println("[ServiceNow DevOps] Error occured while registering the artifact package,Exception: " + e.getMessage());
			throw e;
		}
	}

}
