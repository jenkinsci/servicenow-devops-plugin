package io.jenkins.plugins.pipeline.steps.executions;

import hudson.AbortException;
import hudson.model.Result;

import java.io.BufferedReader;
import java.io.File;
import hudson.model.Run;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;

import io.jenkins.plugins.config.DevOpsJobProperty;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.FilePath;
import hudson.model.TaskListener;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsConfigUploadStep;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import io.jenkins.plugins.utils.DevOpsConstants;

public class DevOpsConfigUploadStepExecution extends SynchronousStepExecution<String> {

	private static final long serialVersionUID = 1L;

	private DevOpsConfigUploadStep step;

	private int retryFrequency = 200;
	private int maxRetryCount = 20;

	public DevOpsConfigUploadStepExecution(StepContext context, DevOpsConfigUploadStep step) {
		super(context);
		this.step = step;
	}

	@Override
	protected String run() throws Exception {

		TaskListener listener = getContext().get(TaskListener.class);
		FilePath workspace = getContext().get(FilePath.class);
		DevOpsModel model = new DevOpsModel();

		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString() + " - Config Upload Step Exceution starts");

//Checking if mandatory params are missing.
		if (this.step.getFileName() == null || this.step.getFileName().isEmpty()) {
			return handleException("File Name Cannot be Empty. Upload Failed");
		}

		if (this.step.getTarget() == null || this.step.getTarget().isEmpty()) {
			return handleException("Target cannot be Empty. Upload Failed");
		}

		if (this.step.getDataFormat() == null || this.step.getDataFormat().isEmpty()) {
			return handleException("DataFormat is Empty. Upload Failed");
		}

//Checks for Changeset Number,if not provided, create a new one.
		String changesetNumber = "";
		if (this.step.getChangesetNumber() == null) {
			try {
				changesetNumber = model.createChangeset(this.step.getApplicationName().trim(), listener);
			} catch (Exception e) {
				return handleException("Creation of Changeset Failed due to : " + e.getMessage() + " - Upload Failed");
			}

			if (changesetNumber == null || changesetNumber.trim().length() == 0) {
				return handleException("Failed To Create Changeset. Upload Failed");
			}

			GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString()
					+ " - New Changeset Created, Since it is not Provided with Inputs");
		} else
			changesetNumber = this.step.getChangesetNumber();

		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString() + " - Intiating the Upload");

//Reading File content.
		String fileName = this.step.getFileName().trim();
		String path = workspace.getRemote();

		StringBuilder filePath = new StringBuilder();
		filePath.append(path);
		filePath.append(File.separator);
		filePath.append(fileName);

		File configFile = new File(filePath.toString());
		InputStream inputStream = null;
		try {
			inputStream = new FileInputStream(configFile);
		} catch (FileNotFoundException e) {
			return handleException("Failed to Read from File : " + e.getMessage());
		}

		Reader isReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
		StringBuffer sb = null;
		try (BufferedReader reader = new BufferedReader(isReader)) {
			sb = new StringBuffer();
			String str;
			while ((str = reader.readLine()) != null) {
				sb.append(str);
				sb.append(System.lineSeparator());
			}
		} catch (FileNotFoundException e) {
			return handleException("Failed to Read from File : " + e.getMessage());
		}

//Sending Upload Request
		JSONObject uploadRequest = null;

		try {
			uploadRequest = model.uploadData(this.step.getApplicationName().trim(), changesetNumber, this.step.getDataFormat().toLowerCase(),
					this.step.getNamePath(), this.step.getAutoCommit(), this.step.getAutoValidate(), sb.toString(),
					this.step.getTarget(), this.step.getDeployableName());
		} catch (Exception e) {
			return handleException("Failed to upload File due to : " + e.getMessage() + " - Upload Failed");
		}

		if (uploadRequest == null) {
			return handleException("Invalid Target Type : Upload Failed");
		}

		String errorMessage = "";
		if (uploadRequest.containsKey(DevOpsConstants.COMMON_RESULT_ERROR.toString())) {
			try {
				JSONObject error = uploadRequest.getJSONObject(DevOpsConstants.COMMON_RESULT_ERROR.toString());
				errorMessage = error.getString(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
			} catch (JSONException j) {
				return handleException("Upload Step Failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
			}

			return handleException("Upload Failed : " + errorMessage);
		}

		String uploadId = "";
		try {
			JSONObject result = uploadRequest.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
			uploadId = result.getString(DevOpsConstants.CONFIG_UPLOAD_ID.toString());
		} catch (JSONException j) {
			return handleException("Upload Step Failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
		}

		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString() + " - Polling for Upload status");

		int retryCount = 0;
		String state = "";
		String changesetId = "";
		JSONObject responseStatus = null;

//Checking for upload status
		while (retryCount <= maxRetryCount) {
			retryCount++;
			JSONObject response = null;
			try {
				response = model.checkStatusForUpload(uploadId);
			} catch (Exception e) {
				return handleException("Failed to Fetch Upload Status due to : " + e.getMessage() + " - Upload Failed");
			}

			try {
				responseStatus = response.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
				state = responseStatus.getString(DevOpsConstants.COMMON_RESPONSE_STATE.toString());
			} catch (JSONException j) {
				return handleException("Upload Step Failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
			}

			if (state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_NEW.toString())
					|| state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_IN_PROGRESS.toString())
					|| state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_READY.toString())
					|| state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_INITIALIZING.toString())) {
				if (retryCount % 2 == 0) {
					GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString()
							+ " - Waiting for Response - Retried  " + retryCount + " times");
					retryFrequency *= 2;
				}
				try {
					Thread.sleep(retryFrequency);
				} catch (InterruptedException i) {
					GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString()
							+ " - Exception While Fetching Upload Status");
					continue;
				}
			} else
				break;
		}

		try {
			String output = "";
			if (responseStatus != null && !(state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_COMPLETED.toString()))) {
				output = responseStatus.getString(DevOpsConstants.COMMON_RESPONSE_OUTPUT.toString());
				return handleException("Upload Failed due to : " + output);
			} 
		} catch (JSONException j) {
			return handleException("Upload Step Failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
		}
		//Returning ChangesetId 
		try {
			if(responseStatus != null)
				changesetId = (responseStatus.getJSONObject(DevOpsConstants.COMMON_RESPONSE_OUTPUT.toString()))
					.getString(DevOpsConstants.COMMON_RESPONSE_NUMBER.toString());
		} catch (JSONException j) {
			return handleException("Upload Step Failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
		}

		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString()
				+ " - File got Uploaded under the Changeset : " + changesetId);
		return changesetId;
	}

	private String handleException(String exceptionMessage) throws Exception {
		Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);
		DevOpsModel model = new DevOpsModel();
		DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());

		if (jobProperties.isIgnoreSNErrors()) {
			GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString() + " - "
					+ exceptionMessage + " - Ignoring SN Errors");
			return null;
		}
		run.setResult(Result.FAILURE);
		throw new AbortException(
				DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString() + " - " + exceptionMessage);
	}
}