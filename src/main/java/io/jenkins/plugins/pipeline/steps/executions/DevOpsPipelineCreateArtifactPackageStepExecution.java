package io.jenkins.plugins.pipeline.steps.executions;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.freestyle.steps.DevOpsCreateArtifactPackageBuildStep;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsPipelineCreateArtifactPackageStep;

/**
 * 
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
		
		Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);
		FilePath workspace = getContext().get(FilePath.class);
		Launcher launcher = getContext().get(Launcher.class);
		EnvVars envVars = getContext().get(EnvVars.class);
		DevOpsModel model = new DevOpsModel();
		if (model.checkIsTrackingCache(run.getParent(), run.getId())) {
			DevOpsCreateArtifactPackageBuildStep artifactPackageStep = new DevOpsCreateArtifactPackageBuildStep();
			artifactPackageStep.setArtifactsPayload(this.step.getArtifactsPayload());
			artifactPackageStep.setName(this.step.getName());
			
			artifactPackageStep.perform(getContext(),run, workspace, launcher, listener, envVars);
		}
		return Boolean.valueOf(true);
	}

}
