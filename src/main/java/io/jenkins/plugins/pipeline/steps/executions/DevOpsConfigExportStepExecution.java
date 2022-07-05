package io.jenkins.plugins.pipeline.steps.executions;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.model.Result;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.FilePath;
import hudson.model.Run;
import io.jenkins.plugins.config.DevOpsJobProperty;
import hudson.model.TaskListener;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsConfigExportStep;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import io.jenkins.plugins.utils.DevOpsConstants;

public class DevOpsConfigExportStepExecution extends SynchronousStepExecution<Boolean> {
	private static final long serialVersionUID = 1L;

	private DevOpsConfigExportStep step;
	private int retryFrequency = 200;
	private int maxRetryCount = 20;

	public DevOpsConfigExportStepExecution(StepContext context, DevOpsConfigExportStep step) {
		super(context);
		this.step = step;
	}

	@Override
	protected Boolean run() throws Exception {

		TaskListener listener = getContext().get(TaskListener.class);
		EnvVars envVars = getContext().get(EnvVars.class);
		DevOpsModel model = new DevOpsModel();
		FilePath workspace = getContext().get(FilePath.class);

		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_EXPORT_STEP_FUNCTION_NAME.toString() + " - Config export step execution starts");

		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_EXPORT_STEP_FUNCTION_NAME.toString() + " - Sending export request");

		JSONObject request = null;
		try {
			request = model.insertExportRequest(this.step.getApplicationName(), this.step.getDeployableName(),
					this.step.getExporterName(), this.step.getExporterFormat(), this.step.getExporterArgs(),
					this.step.getSnapshotName());
		} catch (Exception e) {
			return handleException(e.getMessage());
		}

		String exportId = "";
		if (null == request)
			return handleException("Failed to create export request");

		if (this.step.getShowResults())
			GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_EXPORT_STEP_FUNCTION_NAME.toString()
					+ " - Response from export request api : " + request);

		String errorMessage = "";
		if (request.containsKey(DevOpsConstants.COMMON_RESULT_ERROR.toString())) {
			try {
				JSONObject error = request.getJSONObject(DevOpsConstants.COMMON_RESULT_ERROR.toString());
				errorMessage = error.getString(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
			} catch (JSONException j) {
				return handleException("Export step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
			}
			return handleException(errorMessage);
		}

		try {
			JSONObject result = request.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
			exportId = result.getString(DevOpsConstants.COMMON_RESPONSE_EXPORT_ID.toString());
		} catch (JSONException j) {
			return handleException("Export step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
		}

		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_EXPORT_STEP_FUNCTION_NAME.toString() + " - Polling for export status");

		JSONObject response = null;
		JSONObject exportStatus = null;
		String state = "";
		int retryCount = 0;

		while (retryCount <= maxRetryCount) {
			retryCount++;
			try {
				exportStatus = model.fetchExportStatus(exportId);
			} catch (Exception e) {
				return handleException(e.getMessage());
			}

			try {
				response = exportStatus.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
				state = response.getString(DevOpsConstants.COMMON_RESPONSE_STATE.toString());
			} catch (JSONException j) {
				return handleException("Export step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
			}

			if (state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_NEW.toString())
					|| state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_IN_PROGRESS.toString())
					|| state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_READY.toString())
					|| state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_INITIALIZING.toString())) {
				if (retryCount % 2 == 0) {
					GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_EXPORT_STEP_FUNCTION_NAME.toString()
							+ " - Waiting for response - Retried  " + retryCount + " times");
					retryFrequency *= 2;
				}
				try {
					Thread.sleep(retryFrequency);
				} catch (InterruptedException i) {
					GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_EXPORT_STEP_FUNCTION_NAME.toString()
							+ " - Exception while fetching export status");
					continue;
				}
			} else
				break;
		}

		if (this.step.getShowResults())
			GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_EXPORT_STEP_FUNCTION_NAME.toString()
					+ " - Response from export status api : " + exportStatus);

		String message = "";
		try {
			if (response != null && !(state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_COMPLETED.toString()))) {
				message = response.getString(DevOpsConstants.COMMON_RESPONSE_OUTPUT.toString());
				return handleException(message);
			}
		} catch (JSONException j) {
			return handleException("Export step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
		}

		JSONObject output = null;
		String outputState = "";

		try {
			if (response != null) {
				output = response.getJSONObject(DevOpsConstants.COMMON_RESPONSE_EXPORTER_RESULT.toString());
				outputState = output.getString(DevOpsConstants.COMMON_RESPONSE_STATE.toString());
			}
		} catch (JSONException j) {
			return handleException("Export step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
		}
		// Here output cannot be null as for success cases we have exported data and for
		// failure cases we have failure reason within output key
		if (output != null) {
			if (outputState.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_FAILURE.toString())) {
				JSONArray errors = null;
				try {
					errors = output.getJSONArray(DevOpsConstants.COMMON_RESPONSE_ERRORS.toString());
				} catch (JSONException j) {
					return handleException(
							"Export step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
				}
				return handleException("Export failed due to : " + errors);
			}
		}

		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_EXPORT_STEP_FUNCTION_NAME.toString() + " - Fetching exported data");

		JSONObject exportResponse = null;
		String exportData = "";
		try {
			exportResponse = model.fetchExportData(exportId);
		} catch (Exception e) {
			return handleException(e.getMessage());
		}

		if (this.step.getShowResults())
			GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_EXPORT_STEP_FUNCTION_NAME.toString()
					+ " - Response from export data api : " + exportResponse);

		try {
			JSONObject body = exportResponse.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
			if (body.get(DevOpsConstants.COMMON_RESPONSE_EXPORTER_RESULT.toString()) instanceof JSONObject)
				exportData = (body.getJSONObject(DevOpsConstants.COMMON_RESPONSE_EXPORTER_RESULT.toString()))
						.toString();
			else {
				exportData = body.getString(DevOpsConstants.COMMON_RESPONSE_EXPORTER_RESULT.toString());
				if (this.step.getExporterFormat().equalsIgnoreCase(DevOpsConstants.CONFIG_RAW_FORMAT.toString()))
					exportData = exportData.replace("\"", "\\\"");
			}
		} catch (JSONException j) {
			return handleException("Export step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
		}

		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_EXPORT_STEP_FUNCTION_NAME.toString() + " - Writing exported data to file");

		String exportDataFileName = "";
		if (GenericUtils.isEmpty(this.step.getFileName())) {
			if (this.step.getExporterFormat().equalsIgnoreCase(DevOpsConstants.CONFIG_JSON_FORMAT.toString())
					|| this.step.getExporterFormat().equalsIgnoreCase(""))
				exportDataFileName = "EXPORT_DATA_" + envVars.get(DevOpsConstants.PIPELINE_JOB_NAME.toString()) + "_"
						+ envVars.get(DevOpsConstants.PIPELINE_BUILD_NUMBER.toString()) + "."
						+ DevOpsConstants.CONFIG_JSON_FORMAT.toString();
			else if (this.step.getExporterFormat().equalsIgnoreCase(DevOpsConstants.CONFIG_RAW_FORMAT.toString()))
				exportDataFileName = "EXPORT_DATA_" + envVars.get(DevOpsConstants.PIPELINE_JOB_NAME.toString()) + "_"
						+ envVars.get(DevOpsConstants.PIPELINE_BUILD_NUMBER.toString()) + "."
						+ DevOpsConstants.CONFIG_TEXT_FORMAT.toString();
			else
				exportDataFileName = "EXPORT_DATA_" + envVars.get(DevOpsConstants.PIPELINE_JOB_NAME.toString()) + "_"
						+ envVars.get(DevOpsConstants.PIPELINE_BUILD_NUMBER.toString()) + "."
						+ this.step.getExporterFormat();
		} else
			exportDataFileName = this.step.getFileName();

		FilePath filePath = new FilePath(workspace,exportDataFileName);
		try {
			filePath.write(exportData, "utf-8");
		} catch (Exception e) {
			return handleException(" Exception while writing file : " + e.getMessage());
		}

		return Boolean.valueOf(true);
	}

	private Boolean handleException(String exceptionMessage) throws Exception {
		Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);
		DevOpsModel model = new DevOpsModel();
		DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());

		if (!jobProperties.isIgnoreSNErrors() || this.step.getMarkFailed()) {
			run.setResult(Result.FAILURE);
			throw new AbortException(
					DevOpsConstants.CONFIG_EXPORT_STEP_FUNCTION_NAME.toString() + " - " + exceptionMessage);
		}
		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_EXPORT_STEP_FUNCTION_NAME.toString() + " - "
				+ exceptionMessage + " - Ignoring SN Errors");
		return false;
	}
}