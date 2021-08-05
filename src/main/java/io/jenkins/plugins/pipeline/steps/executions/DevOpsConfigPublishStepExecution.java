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

		Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);

		DevOpsModel model = new DevOpsModel();
		DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());

		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME.toString()
				+ " - Config Publish Step Exceution starts");

		if (GenericUtils.isEmpty(this.step.getApplicationName()) || GenericUtils.isEmpty(this.step.getDeployableName())
				|| GenericUtils.isEmpty(this.step.getSnapshotName())) {
			if (jobProperties.isIgnoreSNErrors()) {
				GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME
						+ " - Parameters Cannot Be Empty : Publish Step Failed - Ignoring SN Errors");
				return Boolean.valueOf(false);
			}
			run.setResult(Result.FAILURE);
			throw new AbortException(DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME
					+ " - Parameters Cannot Be Empty : Publish Step Failed");
		}

		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " - Fetching Snapshot To Publish");

		JSONObject response = null;
		try {
			response = model.fetchSnapshotRecord(this.step.getApplicationName().trim(),
					this.step.getDeployableName().trim(), this.step.getSnapshotName().trim());
		} catch (Exception e) {
			if (jobProperties.isIgnoreSNErrors()) {
				GenericUtils.printConsoleLog(listener,
						DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " - Exception occured while Publish - "
								+ e.getMessage() + " : Publish Step Failed - Ignoring SN Errors");
				return Boolean.valueOf(false);
			}
			run.setResult(Result.FAILURE);
			throw new AbortException(DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME
					+ " - Exception occured while Publish - " + e.getMessage() + " : Publish Step Failed");
		}

		if (response == null) {
			if (jobProperties.isIgnoreSNErrors()) {
				GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME
						+ " - No Snapshot for given inputs : Publish Step Failed - Ignoring SN Errors");
				return Boolean.valueOf(false);
			}
			run.setResult(Result.FAILURE);
			throw new AbortException(DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME
					+ " - No Snapshot for given inputs : Publish Step Failed");
		}

		JSONArray result = null;
		try {
			result = response.getJSONArray(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
		} catch (JSONException j) {
			if (jobProperties.isIgnoreSNErrors()) {
				GenericUtils.printConsoleLog(listener,
						DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " -  Publish Step Failed :"
								+ DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString() + " - Ignoring SN Errors");
				return Boolean.valueOf(false);
			}
			run.setResult(Result.FAILURE);
			throw new AbortException(DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " -  Publish Step Failed :"
					+ DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
		}

		if (result.isEmpty()) {
			if (jobProperties.isIgnoreSNErrors()) {
				GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME
						+ " - No Snapshot for given inputs : Publish Step Failed - Ignoring SN Errors");
				return Boolean.valueOf(false);
			}
			run.setResult(Result.FAILURE);
			throw new AbortException(DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME
					+ " - No Snapshot for given inputs : Publish Step Failed");
		}

		String snapshotId = "";
		try {
			JSONObject responseBody = result.getJSONObject(0);
			snapshotId = responseBody.getString(DevOpsConstants.CONFIG_SNAPSHOT_SYS_ID.toString());
		} catch (JSONException j) {
			if (jobProperties.isIgnoreSNErrors()) {
				GenericUtils.printConsoleLog(listener,
						DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " -  Publish Step Failed :"
								+ DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString() + " - Ignoring SN Errors");
				return Boolean.valueOf(false);
			}
			run.setResult(Result.FAILURE);
			throw new AbortException(DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " -  Publish Step Failed :"
					+ DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
		}

		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " - Sending Snapshot for Publishing");

		JSONObject publishResponse = null;
		try {
			publishResponse = model.publishSnapshot(snapshotId, listener);
		} catch (Exception e) {
			if (jobProperties.isIgnoreSNErrors()) {
				GenericUtils.printConsoleLog(listener,
						DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " - Exception occured while Publish - "
								+ e.getMessage() + " : Publish Step Failed - Ignoring SN Errors");
				return Boolean.valueOf(false);
			}
			run.setResult(Result.FAILURE);
			throw new AbortException(DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME
					+ " - Exception occured while Publish - " + e.getMessage() + " : Publish Step Failed");
		}

		String message = "";
		if (publishResponse != null) {

			if (publishResponse.containsKey("failureCause")) {
				if (jobProperties.isIgnoreSNErrors()) {
					GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME
							+ " - Publish Step Failed : Failed To Fetch Response From Server");
					return Boolean.valueOf(false);
				}

				run.setResult(Result.FAILURE);
				throw new AbortException(DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " - Publishing Failed");
			}

			if (publishResponse.containsKey(DevOpsConstants.COMMON_RESULT_ERROR.toString())) {
				try {
					JSONObject error = publishResponse.getJSONObject((DevOpsConstants.COMMON_RESULT_ERROR.toString()));
					message = error.getString(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
				} catch (JSONException j) {
					if (jobProperties.isIgnoreSNErrors()) {
						GenericUtils.printConsoleLog(listener,
								DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " -  Publish Step Failed :"
										+ DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString()
										+ " - Ignoring SN Errors");
						return Boolean.valueOf(false);
					}
					run.setResult(Result.FAILURE);
					throw new AbortException(DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME
							+ " -  Publish Step Failed :" + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
				}

				if (jobProperties.isIgnoreSNErrors()) {
					GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME
							+ " - Publish Step Failed - Ignoring SN Errors : " + message);
					return Boolean.valueOf(false);
				}
				run.setResult(Result.FAILURE);
				throw new AbortException(DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME
						+ " - Publish Step Failed - Ignoring SN Errors : " + message);
			}
		}
		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " - Snapshot Published");

		return Boolean.valueOf(true);
	}
}