package io.jenkins.plugins.pipeline.steps.executions;

import java.util.logging.Level;

import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.DevOpsRunListener;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.model.DevOpsPipelineGraph;
import io.jenkins.plugins.pipeline.steps.DevOpsPipelineMapStep;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;

public class DevOpsPipelineMapStepExecution extends SynchronousStepExecution<Boolean> {

	private static final long serialVersionUID = 1L;

	private DevOpsPipelineMapStep step;

	public DevOpsPipelineMapStepExecution(StepContext context,
	                                      DevOpsPipelineMapStep step) {
		super(context);
		this.step = step;
	}

	@Override
	protected Boolean run() throws Exception {
		try {
			printDebug("run", null, null, Level.FINE);
			DevOpsModel model = new DevOpsModel();
			Run<?, ?> run = getContext().get(Run.class);
			TaskListener listener = getContext().get(TaskListener.class);
			Boolean result = Boolean.valueOf(false);

			if (!this.step.isEnabled()) {
				String message = "[ServiceNow DevOps] Step association is disabled.";
				listener.getLogger().println(message);
				printDebug("run", new String[]{"step mapping disabled"},
						new String[]{message}, Level.FINE);
				return true;
			}

			String pronoun = run.getParent().getPronoun();
			boolean pipelineTrack = model.checkIsTrackingCache(run.getParent(), run.getId());
			boolean isPullRequestPipeline = pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString());
			DevOpsConfiguration devopsConfig = DevOpsConfiguration.get();
			if (pipelineTrack && ((isPullRequestPipeline && devopsConfig.isTrackPullRequestPipelinesCheck()) || (!isPullRequestPipeline))) {
				DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());

				StepContext ctx = this.getContext();
				EnvVars vars = null;
				try {
					vars = ctx.get(EnvVars.class);
				} catch (Exception e) {
					e.printStackTrace();
				}
				boolean _result = model.handleStepMapping(run, run.getParent(), this, vars);

				printDebug("run", new String[]{"_result"},
						new String[]{String.valueOf(_result)}, Level.FINE);
				result = Boolean.valueOf(_result);

				if (_result) {
					listener.getLogger()
							.println("[ServiceNow DevOps] Step associated successfully");
					printDebug("run", new String[]{"message"},
							new String[]{"Step associated successfully"}, Level.FINE);
				} else {
					printDebug("run", new String[]{"message"},
							new String[]{"Step could not be associated"}, Level.FINE);
					String message = "[ServiceNow DevOps] Step could not be associated, perhaps you need to "
							+ "set the Orchestration pipeline on the Pipeline and Orchestration stage on the Pipeline Steps";
					listener.getLogger().println(message);
					if (jobProperties.isIgnoreSNErrors() || this.step.isIgnoreErrors()) {
						listener.getLogger()
								.println("[ServiceNow DevOps] Step association error ignored.");
					} else {
						run.setResult(Result.FAILURE);
						throw new AbortException(message);
					}
				}
			}
			return result;
		} catch (Exception e) {
			TaskListener listener = getContext().get(TaskListener.class);
			listener.getLogger().println("[ServiceNow DevOps] Error occured while step mapping,Exception: " + e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	public DevOpsPipelineMapStep getStep() {
		return step;
	}

	private void printDebug(String methodName, String[] variables, String[] values,
	                        Level logLevel) {
		GenericUtils
				.printDebug(DevOpsPipelineMapStepExecution.class.getName(), methodName,
						variables, values, logLevel);
	}
}
