package io.jenkins.plugins.pipeline.steps.executions;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.model.Result;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsConfigRegisterChangeSetStep;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import io.jenkins.plugins.utils.DevOpsConstants;

public class DevOpsConfigRegisterChangeSetStepExecution extends SynchronousStepExecution<Boolean> {

	private static final long serialVersionUID = 1L;

	private DevOpsConfigRegisterChangeSetStep step;

	public DevOpsConfigRegisterChangeSetStepExecution(StepContext context, DevOpsConfigRegisterChangeSetStep step) {
		super(context);
		this.step = step;
	}

	@Override
	protected Boolean run() throws Exception {

		Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);
		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
		EnvVars envVars = getContext().get(EnvVars.class);
		DevOpsModel model = new DevOpsModel();
		DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());

		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_REGISTER_CHANGESET_STEP_FUNCTION_NAME.toString()
				+ " - Config Register Changeset Step Exceution starts");

		String changesetId = this.step.getChangesetId().trim();
		if (GenericUtils.isEmpty(changesetId)) {
			if (jobProperties.isIgnoreSNErrors()) {
				GenericUtils.printConsoleLog(listener,
						DevOpsConstants.CONFIG_REGISTER_CHANGESET_STEP_FUNCTION_NAME.toString() + " - "
								+ "Changeset Id Cannot be Empty or Null - Ignoring SN Errors");
				return Boolean.valueOf(false);
			}
			run.setResult(Result.FAILURE);
			throw new AbortException(DevOpsConstants.CONFIG_REGISTER_CHANGESET_STEP_FUNCTION_NAME.toString() + " - "
					+ "Changeset Id Cannot be Empty or Null");
		}

		String pipelineName = envVars.get(DevOpsConstants.PIPELINE_JOB_NAME.toString());
		String toolId = devopsConfig.getToolId();
		String buildNumber = envVars.get(DevOpsConstants.PIPELINE_BUILD_NUMBER.toString());
		String type = "jenkins";

		JSONObject registerResponse = null;
		try {
			registerResponse = model.registerChangeset(pipelineName, toolId, buildNumber, type, changesetId, listener);
		} catch (Exception e) {
			if (jobProperties.isIgnoreSNErrors()) {
				GenericUtils.printConsoleLog(listener,
						DevOpsConstants.CONFIG_REGISTER_CHANGESET_STEP_FUNCTION_NAME.toString() + " - "
								+ "Failed To Register Changeset - Ignoring SN Errors");
				return Boolean.valueOf(false);
			}
			run.setResult(Result.FAILURE);
			throw new AbortException(DevOpsConstants.CONFIG_REGISTER_CHANGESET_STEP_FUNCTION_NAME.toString() + " - "
					+ "Failed To Register Changeset");
		}
		String status = "";
		try {
			status = registerResponse.getString(DevOpsConstants.COMMON_RESPONSE_STATUS.toString());
		} catch (JSONException j) {
			if (jobProperties.isIgnoreSNErrors()) {
				GenericUtils.printConsoleLog(listener,
						DevOpsConstants.CONFIG_REGISTER_CHANGESET_STEP_FUNCTION_NAME.toString() + " - "
								+ "Register Step Failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString()
								+ " - Ignoring SN Errors");
				return Boolean.valueOf(false);
			}
			run.setResult(Result.FAILURE);
			throw new AbortException(DevOpsConstants.CONFIG_REGISTER_CHANGESET_STEP_FUNCTION_NAME.toString() + " - "
					+ "Register Step Failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
		}

		String errorMessage = "";
		if (status.equalsIgnoreCase("Failure")) {
			try {
				errorMessage = registerResponse.getString(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
			} catch (JSONException j) {
				if (jobProperties.isIgnoreSNErrors()) {
					GenericUtils.printConsoleLog(listener,
							DevOpsConstants.CONFIG_REGISTER_CHANGESET_STEP_FUNCTION_NAME.toString() + " - "
									+ "Register Step Failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString()
									+ " - Ignoring SN Errors");
					return Boolean.valueOf(false);
				}
				run.setResult(Result.FAILURE);
				throw new AbortException(DevOpsConstants.CONFIG_REGISTER_CHANGESET_STEP_FUNCTION_NAME.toString() + " - "
						+ "Register Step Failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
			}

			if (jobProperties.isIgnoreSNErrors()) {
				GenericUtils.printConsoleLog(listener,
						DevOpsConstants.CONFIG_REGISTER_CHANGESET_STEP_FUNCTION_NAME.toString() + " - " + errorMessage
								+ " - Ignoring SN Errors");
				return Boolean.valueOf(false);
			}
			run.setResult(Result.FAILURE);
			throw new AbortException(
					DevOpsConstants.CONFIG_REGISTER_CHANGESET_STEP_FUNCTION_NAME.toString() + " - " + errorMessage);
		}

		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_REGISTER_CHANGESET_STEP_FUNCTION_NAME.toString()
				+ " - Successfully Registered Changeset");

		return Boolean.valueOf(true);
	}
}