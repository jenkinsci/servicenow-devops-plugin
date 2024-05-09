package io.jenkins.plugins.pipeline.steps.executions;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.model.Result;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.model.Run;
import hudson.model.Job;
import hudson.model.TaskListener;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.config.DevOpsConfigurationEntry;
import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsConfigRegisterPipelineStep;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import io.jenkins.plugins.utils.DevOpsConstants;

public class DevOpsConfigRegisterPipelineStepExecution extends SynchronousStepExecution<Boolean> {

	private static final long serialVersionUID = 1L;

	private DevOpsConfigRegisterPipelineStep step;

	public DevOpsConfigRegisterPipelineStepExecution(StepContext context, DevOpsConfigRegisterPipelineStep step) {
		super(context);
		this.step = step;
	}

	@Override
	protected Boolean run() throws Exception {

		TaskListener listener = getContext().get(TaskListener.class);
		Run<?, ?> run = getContext().get(Run.class);
		DevOpsConfigurationEntry devopsConfig = GenericUtils.getDevOpsConfigurationEntry();
		EnvVars envVars = getContext().get(EnvVars.class);
		DevOpsModel model = new DevOpsModel();
		Job<?, ?> job = run.getParent();

		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_REGISTER_PIPELINE_STEP_FUNCTION_NAME.toString()
				+ " - Config register pipeline step execution starts");
		String changesetNumber = "";
		String snapshotName = "";
		String applicationName = "";

		boolean isMultiBranch = GenericUtils.isMultiBranch(job);
		changesetNumber = this.step.getChangesetNumber();
		snapshotName = this.step.getSnapshotName();
		applicationName = this.step.getApplicationName();

		if(GenericUtils.isEmpty(applicationName))
			return handleException("Application name should be provided for registering");

		if(!GenericUtils.isEmpty(changesetNumber) && !GenericUtils.isEmpty(snapshotName))
			return handleException("Either snapshot or changeset detail should only be provided for registering");

		String pipelineName = envVars.get(DevOpsConstants.PIPELINE_JOB_NAME.toString());
		String branchName = envVars.get(DevOpsConstants.PIPELINE_BRANCH_NAME.toString());
		String toolId = devopsConfig.getToolId();
		String buildNumber = envVars.get(DevOpsConstants.PIPELINE_BUILD_NUMBER.toString());
		String type = "jenkins";

		JSONObject registerResponse = null;
		try {
			registerResponse = model.registerChangeset(pipelineName, branchName, toolId, buildNumber, type, isMultiBranch, changesetNumber,
					snapshotName, applicationName, listener);
		} catch (Exception e) {
			return handleException("Failed to register pipeline with given changeset / snapshot : " + e.getMessage());
		}

		if (this.step.getShowResults())
			GenericUtils.printConsoleLog(listener,
					DevOpsConstants.CONFIG_REGISTER_PIPELINE_STEP_FUNCTION_NAME.toString()
							+ " - Response from register pipeline api : " + registerResponse);

		String status = "";
		try {
			status = registerResponse.getString(DevOpsConstants.COMMON_RESPONSE_STATUS.toString());
		} catch (JSONException j) {
			return handleException("Register step sailed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
		}

		String errorMessage = "";
		if (status.equalsIgnoreCase("Failure")) {
			try {
				errorMessage = registerResponse.getString(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
			} catch (JSONException j) {
				return handleException(
						"Register step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
			}
			return handleException(errorMessage);
		}

		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_REGISTER_PIPELINE_STEP_FUNCTION_NAME.toString()
				+ " - Successfully registered pipeline with given changeset / snapshot");

		return Boolean.valueOf(true);
	}

	private boolean handleException(String exceptionMessage) throws Exception {
		Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);
		DevOpsModel model = new DevOpsModel();
		DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());

		if (!jobProperties.isIgnoreSNErrors() || this.step.getMarkFailed()) {
			run.setResult(Result.FAILURE);
			throw new AbortException(
					DevOpsConstants.CONFIG_REGISTER_PIPELINE_STEP_FUNCTION_NAME.toString() + " - " + exceptionMessage);
		}
		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_REGISTER_PIPELINE_STEP_FUNCTION_NAME.toString()
				+ " - " + exceptionMessage + " - Ignoring SN Errors");
		return false;
	}
}