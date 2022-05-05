package io.jenkins.plugins.pipeline.steps.executions;

import hudson.AbortException;
import hudson.model.Result;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsConfigPublishStep;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import io.jenkins.plugins.utils.DevOpsConstants;

public class DevOpsConfigPublishStepExecution extends SynchronousStepExecution<Boolean> {

	private static final long serialVersionUID = 1L;

	private DevOpsConfigPublishStep step;

	public DevOpsConfigPublishStepExecution(StepContext context, DevOpsConfigPublishStep step) {
		super(context);
		this.step = step;
	}

	@Override
	protected Boolean run() throws Exception {

		TaskListener listener = getContext().get(TaskListener.class);

		DevOpsModel model = new DevOpsModel();

		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME.toString()
				+ " - Config publish step execution starts");

		if (GenericUtils.isEmpty(this.step.getApplicationName()) || GenericUtils.isEmpty(this.step.getDeployableName())
				|| GenericUtils.isEmpty(this.step.getSnapshotName()))
			return handleException("Parameters cannot be empty : Publish step failed");

		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " - Fetching snapshot to publish");

		JSONObject response = null;
		try {
			response = model.fetchSnapshotRecord(this.step.getApplicationName().trim(),
					this.step.getDeployableName().trim(), this.step.getSnapshotName().trim());
		} catch (Exception e) {
			return handleException("Exception occured while publish - " + e.getMessage() + " : Publish step failed");
		}

		if (response == null)
			return handleException("Unable to find snapshot with given inputs : Publish step failed");

		JSONArray result = null;
		try {
			result = response.getJSONArray(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
		} catch (JSONException j) {
			return handleException("Publish step failed :" + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
		}

		if (result.isEmpty())
			return handleException("Unable to find snapshot with given inputs : Publish step failed");

		String snapshotId = "";
		try {
			JSONObject responseBody = result.getJSONObject(0);
			snapshotId = responseBody.getString(DevOpsConstants.CONFIG_SNAPSHOT_SYS_ID.toString());
		} catch (JSONException j) {
			return handleException("Publish step failed :" + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
		}

		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " - Sending snapshot for publishing");

		JSONObject publishResponse = null;
		try {
			publishResponse = model.publishSnapshot(snapshotId, listener);
		} catch (Exception e) {
			return handleException("Exception occured while publish - " + e.getMessage() + " : Publish step failed");
		}

		String message = "";
		if (publishResponse != null) {
			if (this.step.getShowResults())
				GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME
						+ " - Response from snapshot publish api is : " + publishResponse);
			if (publishResponse.containsKey("failureCause"))
				return handleException("Publishing of snapshot failed");

			if (publishResponse.containsKey(DevOpsConstants.COMMON_RESULT_ERROR.toString())) {
				try {
					JSONObject error = publishResponse.getJSONObject((DevOpsConstants.COMMON_RESULT_ERROR.toString()));
					message = error.getString(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
				} catch (JSONException j) {
					return handleException(
							"Publish step failed :" + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
				}
				return handleException("Publish step failed - " + message);
			}
		}
		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " - Snapshot published");
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
					DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME.toString() + " - " + exceptionMessage);
		}
		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME.toString() + " - "
				+ exceptionMessage + " - Ignoring SN Errors");
		return false;
	}
}