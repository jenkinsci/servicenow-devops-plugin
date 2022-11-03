package io.jenkins.plugins.pipeline.steps.executions;

import hudson.AbortException;
import hudson.model.Result;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsConfigValidateStep;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import io.jenkins.plugins.utils.DevOpsConstants;

public class DevOpsConfigValidateStepExecution extends SynchronousStepExecution<Boolean> {

	private static final long serialVersionUID = 1L;
	private DevOpsConfigValidateStep step;

	public DevOpsConfigValidateStepExecution(StepContext context, DevOpsConfigValidateStep step) {
		super(context);
		this.step = step;
	}

	@Override
	protected Boolean run() throws Exception {

		TaskListener listener = getContext().get(TaskListener.class);
		DevOpsModel model = new DevOpsModel();

		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_VALIDATE_STEP_FUNCTION_NAME.toString()
				+ " - Config validate step execution starts");

		// Check for Mandatory Parameters
		if (GenericUtils.isEmpty(this.step.getApplicationName()) || GenericUtils.isEmpty(this.step.getDeployableName()))
			return handleException("Missing input parameters : applicationName and deployableName");

		// Fetch Snapshot Record
		JSONObject snapshotDetails = null;
		try {
			snapshotDetails = model.fetchSnapshotRecord(this.step.getApplicationName().trim(),
					this.step.getDeployableName().trim(), this.step.getSnapshotName());
		} catch (Exception e) {
			return handleException("Failed to fetch snapshot - Exception " + e.getMessage());
		}

		if (snapshotDetails == null) {
			if(this.step.getSnapshotName() != null) {
				return handleException("Failed to fetch details for snapshot " + this.step.getSnapshotName());
			}		
			return handleException("Failed to fetch latest created snapshot for " + this.step.getApplicationName()
					+ " and " + this.step.getDeployableName());
		}

		JSONArray result = null;
		try {
			result = snapshotDetails.getJSONArray(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
		} catch (JSONException j) {
			return handleException("Unable to parse JSON : " + j.getMessage());
		}

		if (result.isEmpty()) {
			if(this.step.getSnapshotName() != null) {
				return handleException("No snapshot found for " + this.step.getApplicationName() + ","
						+ this.step.getDeployableName() + "," + this.step.getSnapshotName());
			}
			return handleException(
						"No snapshot found for " + this.step.getApplicationName() + "," + this.step.getDeployableName());
		}

		String snapshotId = "";
		try {
			JSONObject responseBody = result.getJSONObject(0);
			snapshotId = responseBody.getString(DevOpsConstants.CONFIG_SNAPSHOT_SYS_ID.toString());
		} catch (JSONException j) {
			return handleException("Unable to parse JSON : " + j.getMessage());
		}

		// Sending Snapshot for Validation
		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_VALIDATE_STEP_FUNCTION_NAME + " - Calling validate api");

		JSONObject validateResponse = null;
		String transactionSource = "system_information=jenkins,interface_type="+step.getShowResults()+",interface_version="+step.getMarkFailed();
		
		try {
			validateResponse = model.validateSnapshot(snapshotId, listener, transactionSource);
		} catch (Exception e) {
			return handleException("Failed to validate snapshot - Exception " + e.getMessage());
		}

		String message = "";
		if (validateResponse != null) {
			if (this.step.getShowResults())
				GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_VALIDATE_STEP_FUNCTION_NAME.toString()
						+ " - " + "Response from validate api : " + validateResponse);

			if (validateResponse.containsKey(DevOpsConstants.COMMON_RESULT_FAILURE.toString()))
				return handleException("Validate step failed :" + validateResponse);

			if (validateResponse.containsKey(DevOpsConstants.COMMON_RESULT_ERROR.toString())) {

				try {
					JSONObject error = validateResponse.getJSONObject((DevOpsConstants.COMMON_RESULT_ERROR.toString()));
					message = error.getString(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
				} catch (JSONException j) {
					return handleException("Unable to parse JSON : " + j.getMessage());
				}
				return handleException("Validate step failed :" + message);
			}
		}

		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_VALIDATE_STEP_FUNCTION_NAME.toString() + " - "
				+ "Succesfully triggered validation ");
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
					DevOpsConstants.CONFIG_VALIDATE_STEP_FUNCTION_NAME.toString() + " - " + exceptionMessage);
		}
		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_VALIDATE_STEP_FUNCTION_NAME.toString() + " - "
				+ exceptionMessage + " - Ignoring SN Errors");
		return false;
	}
}
