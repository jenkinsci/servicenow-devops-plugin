package io.jenkins.plugins.pipeline.steps.executions;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.freestyle.steps.DevOpsRegisterArtifactBuildStep;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsPipelineRegisterArtifactStep;

/**
 * 
 * @author prashanth.pedduri
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
		if (model.checkIsTrackingCache(run.getParent(), run.getId())) {
			DevOpsRegisterArtifactBuildStep registerArtifactBuildStep = new DevOpsRegisterArtifactBuildStep();
			registerArtifactBuildStep.setArtifactsPayload(this.step.getArtifactsPayload());
			
			registerArtifactBuildStep.perform(run, workspace, launcher, listener, envVars);
		}
		
		return Boolean.valueOf(true);
	}

}
