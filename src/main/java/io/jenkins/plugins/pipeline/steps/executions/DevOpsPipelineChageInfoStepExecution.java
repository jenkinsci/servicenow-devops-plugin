package io.jenkins.plugins.pipeline.steps.executions;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.model.Run;
import io.jenkins.plugins.DevOpsRunListener;
import io.jenkins.plugins.DevOpsRunStatusAction;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.model.DevOpsPipelineGraph;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;

public class DevOpsPipelineChageInfoStepExecution extends SynchronousStepExecution<String> {

	private static final long serialVersionUID = 1L;

	public DevOpsPipelineChageInfoStepExecution(StepContext context) {
		super(context);
	}


	@Override
	protected String run() throws Exception {
		Run<?, ?> run = getContext().get(Run.class);
		String pronoun = run.getParent().getPronoun();
		boolean isPullRequestPipeline = pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString());
		DevOpsModel model = new DevOpsModel();
		boolean pipelineTrack = model.checkIsTrackingCache(run.getParent(), run.getId());
		DevOpsConfiguration devopsConfig = DevOpsConfiguration.get();
		if (pipelineTrack && ((isPullRequestPipeline && devopsConfig.isTrackPullRequestPipelinesCheck()) || (!isPullRequestPipeline))) {
			DevOpsRunStatusAction runStatusAction = run.getAction(DevOpsRunStatusAction.class);
			if (runStatusAction != null) {
				DevOpsPipelineGraph pipelineGraph = runStatusAction.getPipelineGraph();
				if (pipelineGraph != null) {
					String currentStageName = DevOpsRunListener.DevOpsStageListener.getCurrentStageName(getContext(), pipelineGraph);
					if (currentStageName != null)
						return runStatusAction.changeRequestInfo.get(currentStageName);
				}
			}
		}
		return null;

	}
}
