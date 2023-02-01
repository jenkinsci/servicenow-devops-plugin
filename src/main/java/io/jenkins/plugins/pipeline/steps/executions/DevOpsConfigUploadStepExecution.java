package io.jenkins.plugins.pipeline.steps.executions;

import hudson.AbortException;
import hudson.model.Result;

import java.io.File;
import hudson.model.Run;

import java.io.IOException;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.jenkins.plugins.config.DevOpsJobProperty;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import hudson.FilePath;
import hudson.model.TaskListener;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsConfigUploadStep;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import io.jenkins.plugins.utils.DevOpsConstants;

public class DevOpsConfigUploadStepExecution extends SynchronousNonBlockingStepExecution<String> {

	private static final long serialVersionUID = 1L;
	private static final int MAX_DEPTH_COUNT = 10;

	private DevOpsConfigUploadStep step;

	private int retryFrequency = 220;
	private int maxRetryCount = 20;

	public DevOpsConfigUploadStepExecution(StepContext context, DevOpsConfigUploadStep step) {
		super(context);
		this.step = step;
	}
//Jenkins step for uploading configuration data
	@Override
	protected String run() throws Exception {

		TaskListener listener = getContext().get(TaskListener.class);
		FilePath workspace = getContext().get(FilePath.class);
		DevOpsModel model = new DevOpsModel();

		GenericUtils.printConsoleLog(listener,
				DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString() + " - Config upload step execution starts");

//Checking if mandatory params are missing.
		if (this.step.getConfigFile() == null || this.step.getConfigFile().isEmpty()) {
			return handleException("File regex cannot be empty. Upload failed");
		}

		if (this.step.getTarget() == null || this.step.getTarget().isEmpty()) {
			return handleException("Target cannot be empty. Upload failed");
		}

		if (this.step.getDataFormat() == null || this.step.getDataFormat().isEmpty()) {
			return handleException("Data format is empty. Upload failed");
		}

//Fetching files from regex pattern

		try {
			listener.getLogger().println("Looking for file : " + step.getConfigFile());
			List<FilePath> allFilesInWorkspace = null;
			try {
				allFilesInWorkspace = this.walkFilePaths(workspace, MAX_DEPTH_COUNT);
			} catch (IOException | InterruptedException e) {
				return handleException("Error occured while fetching the files");
			}

			if (allFilesInWorkspace.isEmpty())
				return handleException("No files found inside workspace");

			final PathMatcher maskMatcher = FileSystems.getDefault()
					.getPathMatcher("glob:" + this.step.getConfigFile());
			List<FilePath> filteredList = allFilesInWorkspace.stream().filter(c -> {

				Path fileRelativePath = Paths.get(c.getRemote().replace(workspace.getRemote() + "/", ""));
				if (maskMatcher.matches(fileRelativePath)) {
					return true;
				}
				return false;

			}).collect(Collectors.toList());

			if (filteredList.size() < 1) {
				return handleException("No files found for given regex");
			}

//Checks for Changeset Number,if not provided, create a new one.
			JSONObject changesetResponse = null;
			String changesetNumber = "";
			if (this.step.getChangesetNumber() == null) {
				try {
					changesetResponse = model.createChangeset(this.step.getApplicationName().trim(), listener);
				} catch (Exception e) {
					return handleException(
							"Creation of changeset failed due to : " + e.getMessage() + " - Upload failed");
				}

				if (changesetResponse == null) {
					return handleException("Failed to create changeset. Upload failed");
				}

				if (this.step.getShowResults())
					GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString()
							+ " - Response from create changeset api : " + changesetResponse);

				try {
					if (changesetResponse.containsKey(DevOpsConstants.COMMON_RESPONSE_RESULT.toString())) {
						JSONObject result = changesetResponse
								.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
						if (result.containsKey(DevOpsConstants.COMMON_RESPONSE_NUMBER.toString()))
							changesetNumber = result.getString(DevOpsConstants.COMMON_RESPONSE_NUMBER.toString());
					} else {
						JSONObject error = changesetResponse
								.getJSONObject(DevOpsConstants.COMMON_RESULT_ERROR.toString());
						String errorMessage = error.getString(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
						return handleException(errorMessage);
					}
				} catch (JSONException j) {
					return handleException(
							"Upload step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
				}

				GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString()
						+ " - New changeset created, Since it is not provided with inputs");
			} else
				changesetNumber = this.step.getChangesetNumber();

			GenericUtils.printConsoleLog(listener,
					DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString() + " - Initiating the upload");

//Reading File content.
			boolean commitFlag = false;
			String changesetId = "";
			JSONObject responseStatus = null;
			String transactionSource = "system_information=jenkins,interface_type="+step.getTarget()+",interface="+step.getAutoValidate()+",interface_version="+step.getDataFormat()+",session_type="+step.getAutoCommit();
			
			for (FilePath fileToUpload : filteredList) {

				String fileContent = fileToUpload.readToString();

				// Setting namePath
				Path relativePath = Paths.get(fileToUpload.getRemote().replace(workspace.getRemote() + "/", ""));
				String modifiedNamePath = "";
				if (this.step.getConvertPath()) {
					modifiedNamePath = this.step.getNamePath() + File.separator + relativePath.toString();
				} else {
					File f = new File(relativePath.toString());
					String absoulteName = f.getName();
					modifiedNamePath = this.step.getNamePath() + File.separator + absoulteName;
				}

				if (filteredList.indexOf(fileToUpload) == filteredList.size() - 1) {
					commitFlag = this.step.getAutoCommit();
				}

				JSONObject uploadRequest = null;
				try {
					uploadRequest = model.uploadData(this.step.getApplicationName().trim(), changesetNumber,
							this.step.getDataFormat().toLowerCase(), modifiedNamePath, commitFlag,
							this.step.getAutoValidate(), fileContent, this.step.getTarget(),
							this.step.getDeployableName(), this.step.getCollectionName(), transactionSource);
				} catch (Exception e) {
					return handleException("Failed to upload file due to : " + e.getMessage() + " - Upload failed");
				}

				if (uploadRequest == null) {
					return handleException("Invalid target Type : Upload failed");
				}

				if (this.step.getShowResults())
					GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString()
							+ " - Response from upload request api for file " + relativePath + " : " + uploadRequest);

				String errorMessage = "";
				if (uploadRequest.containsKey(DevOpsConstants.COMMON_RESULT_ERROR.toString())) {
					try {
						JSONObject error = uploadRequest.getJSONObject(DevOpsConstants.COMMON_RESULT_ERROR.toString());
						errorMessage = error.getString(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
					} catch (JSONException j) {
						return handleException(
								"Upload step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
					}
					return handleException("Upload failed : " + errorMessage);
				}

				String uploadId = "";
				try {
					JSONObject result = uploadRequest.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
					uploadId = result.getString(DevOpsConstants.CONFIG_UPLOAD_ID.toString());
				} catch (JSONException j) {
					return handleException(
							"Upload step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
				}

				GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString()
						+ " - Polling for upload status of the file - " + relativePath);

				int retryCount = 0;
				String state = "";
				JSONObject response = null;

				// Checking for upload status
				while (retryCount <= maxRetryCount) {
					retryCount++;
					try {
						response = model.checkStatusForUpload(uploadId);
					} catch (Exception e) {
						return handleException(
								"Failed to fetch upload status due to : " + e.getMessage() + " - Upload failed");
					}

					try {
						responseStatus = response.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
						state = responseStatus.getString(DevOpsConstants.COMMON_RESPONSE_STATE.toString());
					} catch (JSONException j) {
						return handleException(
								"Upload step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
					}

					if (state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_NEW.toString())
							|| state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_IN_PROGRESS.toString())
							|| state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_READY.toString())
							|| state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_INITIALIZING.toString())) {
						if (retryCount % 2 == 0) {
							GenericUtils.printConsoleLog(listener,
									DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString()
											+ " - Waiting for response - Retried  " + retryCount + " times");
							retryFrequency *= 2;
						}
						try {
							Thread.sleep(retryFrequency);
						} catch (InterruptedException i) {
							GenericUtils.printConsoleLog(listener,
									DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString()
											+ " - Exception while fetching upload status");
							continue;
						}
					} else
						break;
				}
				if (this.step.getShowResults())
					GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString()
							+ " - Response from upload status api for file " + relativePath + " : " + response);
				try {
					String output = "";
					if (responseStatus != null
							&& !(state.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_COMPLETED.toString()))) {
						output = responseStatus.getString(DevOpsConstants.COMMON_RESPONSE_OUTPUT.toString());
						return handleException("Upload failed due to : " + output);
					}
				} catch (JSONException j) {
					return handleException(
							"Upload step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
				}
				//resetting retryFrequency for the next file upload
                this.retryFrequency = 220;
			}
			try {
				if (responseStatus != null)
					changesetId = (responseStatus.getJSONObject(DevOpsConstants.COMMON_RESPONSE_OUTPUT.toString()))
							.getString(DevOpsConstants.COMMON_RESPONSE_NUMBER.toString());
			} catch (JSONException j) {
				return handleException("Upload step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
			}

			GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString()
					+ " - Files got uploaded under the changeset : " + changesetId);
			return changesetId;
		} catch (IllegalArgumentException | UnsupportedOperationException e) {
			return handleException("Failed to read regex pattern - Upload failed");
		}
	}

	private List<FilePath> walkFilePaths(FilePath filePath, int nMaxDepth) throws IOException, InterruptedException {
		List<FilePath> fileList = new ArrayList<>();
		filePath.list().stream().forEach(c -> {
			try {
				if (c.isDirectory() == true && nMaxDepth > 0) {
					List<FilePath> paths = this.walkFilePaths(c, nMaxDepth - 1);
					fileList.addAll(paths);
				} else {
					fileList.add(c);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
		return fileList;
	}

	private String handleException(String exceptionMessage) throws Exception {
		Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);
		DevOpsModel model = new DevOpsModel();
		DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());

		if (!jobProperties.isIgnoreSNErrors() || this.step.getMarkFailed()) {
			run.setResult(Result.FAILURE);
			throw new AbortException(
					DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString() + " - " + exceptionMessage);
		}
		GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString() + " - "
				+ exceptionMessage + " - Ignoring SN Errors");
		return null;
	}
}
