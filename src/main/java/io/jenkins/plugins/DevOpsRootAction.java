package io.jenkins.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.RootAction;
import hudson.security.csrf.CrumbExclusion;
import io.jenkins.plugins.config.DevOpsConfigurationEntry;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.model.DevOpsRunStatusJobModel;
import io.jenkins.plugins.model.DevOpsRunStatusModel;
import io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineChangeStepExecution;
import io.jenkins.plugins.utils.CommUtils;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONObject;


@Extension
public class DevOpsRootAction extends CrumbExclusion implements RootAction {

	private static final HashMap<String, String> webhooks = new HashMap<>(); // token->jobId (Dispatcher)
	private static final HashMap<String, String> jobs = new HashMap<>();     // jobId->token (Dispatcher)
	private static final HashMap<String, String> callbackContent = new HashMap<>();// jobId->callbackResponse (Dispatcher/FreestyleStep)
	private static final HashMap<String, String> callbackToken = new HashMap<>(); // jobId->token (FreestyleStep)
	private static final HashMap<String, DevOpsPipelineChangeStepExecution> pipelineWebhooks = new HashMap<>(); // token->asyncStepExecution (PipelineChangeStep)
	private static final HashMap<String, String> changeRequestContent = new HashMap<>(); // jobId->callbackResponse (Dispatcher/FreestyleStep)

	private static final HashMap<String, Boolean> trackedJobs = new HashMap<>(); // runId->True/False
	private static final HashMap<String, DevOpsModel.DevOpsPipelineInfo> snPipelineInfo = new HashMap<>(); // runId
	// ->JSONObject

	@Override
	public String getIconFileName() {
		return null;
	}

	@Override
	public String getDisplayName() {
		return null;
	}

	@Override
	public String getUrlName() {
		return DevOpsConstants.CALLBACK_URL_IDENTIFIER.toString();
	}

	private boolean _handleFreestyleCallback(String token, StringBuffer content) {
		// cross validation to make sure the token received matches the one we had mapped to this jobId
		String jobId;
		synchronized (webhooks) {
			jobId = webhooks.remove(token);
		}
		String originalToken;
		synchronized (jobs) {
			originalToken = jobs.remove(jobId);
		}
		if (jobId != null && originalToken.equals(token)) {
			synchronized (callbackContent) {
				callbackContent.put(jobId, content.toString().trim());
			}
			synchronized (callbackToken) {
				callbackToken.put(jobId, token);
			}
			return true;
		}
		return false;
	}

	private boolean _handlePipelineCallback(String token, StringBuffer content) {
		DevOpsPipelineChangeStepExecution exec;
		synchronized (pipelineWebhooks) {
			exec = pipelineWebhooks.remove(token);
		}
		if (exec != null) {
			exec.onTriggered(token, content.toString().trim());
			return true;
		}
		return false;
	}

	private boolean _displayFreestyleChangeRequestInfo(String token, StringBuffer content) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_displayFreestyleChangeRequestInfo", new String[]{"token"}, new String[]{token}, Level.INFO);
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_displayFreestyleChangeRequestInfo", new String[]{"content"}, new String[]{content.toString()}, Level.INFO);
		String jobId;
		synchronized (webhooks) {
			jobId = webhooks.get(token);
		}
		String originalToken;
		synchronized (jobs) {
			originalToken = jobs.get(jobId);
		}
		if (jobId != null && originalToken.equals(token)) {
			synchronized (changeRequestContent) {
				changeRequestContent.put(jobId, content.toString().trim());
			}
			return true;
		}
		return false;
	}

	private JSONObject _sendDummyNotification(String reqestedToolId, DevOpsConfigurationEntry devopsConfig) {
		try {
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_sendDummyNotification", new String[]{"reqestedToolId"}, new String[]{reqestedToolId}, Level.FINE);
			JSONObject params = new JSONObject();
			params.put(DevOpsConstants.TOOL_TYPE_ATTR.toString(), DevOpsConstants.TOOL_TYPE.toString());
			String toolId = devopsConfig.getToolId();
			params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), toolId);
			String user = DevOpsConfigurationEntry.getUser(devopsConfig.getCredentialsId());
			String pwd = DevOpsConfigurationEntry.getPwd(devopsConfig.getCredentialsId());

			DevOpsRunStatusModel model = new DevOpsRunStatusModel();
			DevOpsRunStatusJobModel jobModel = new DevOpsRunStatusJobModel();

			UUID uuid = UUID.randomUUID();
			jobModel.setName(uuid.toString() + "_dummyWebhookPipeline");
			model.setJobModel(jobModel);
			model.setNumber(0);
			model.setUrl(uuid.toString());
			model.setPronoun(DevOpsConstants.JENKINS_DUMMY_EVENT_PRONOUN.toString());

			Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.IDENTITY).setPrettyPrinting().create();
			String data = gson.toJson(model);
			JSONObject jsonResult = null;


			if (!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {
				Map<String, String> tokenDetails = new HashMap<String, String>();
				tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
						DevOpsConfigurationEntry.getTokenText(devopsConfig.getSecretCredentialId()));
				jsonResult = CommUtils.callV2Support("POST", devopsConfig.getNotificationUrl(), params, data, user, pwd,
						null, null, tokenDetails);
			} else {
				jsonResult = CommUtils.call("POST", devopsConfig.getNotificationUrl(), params, data, user, pwd, null, null);
			}
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_sendDummyNotification", new String[]{"reqestedToolId", "jsonResult"}, new String[]{reqestedToolId, jsonResult.toString()}, Level.FINE);
			return jsonResult;
		} catch (Exception e) {
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_sendDummyNotification", "Sending dummy notification failed for toolId: " + reqestedToolId + " Exception:" + e.getMessage(), Level.SEVERE);
			e.printStackTrace();
			return null;
		}
	}

	private boolean _displayPipelineChangeRequestInfo(String token, StringBuffer content) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_displayPipelineChangeRequestInfo", new String[]{"token"}, new String[]{token}, Level.INFO);
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_displayPipelineChangeRequestInfo", new String[]{"content"}, new String[]{content.toString()}, Level.INFO);
		DevOpsPipelineChangeStepExecution exec;
		synchronized (pipelineWebhooks) {
			exec = pipelineWebhooks.get(token);
		}
		if (exec != null) {
			exec.displayPipelineChangeRequestInfo(token, content.toString().trim());
			return true;
		}
		return false;
	}

	//This method returns /{JENKINS_HOME}/jobs/{jobName}/snPipelineInfo.json
	//This method returns /{JENKINS_HOME}/jobs/{folderName}/jobs/{jobName}/snPipelineInfo.json
	public static String getRootDirFilePath(String jobName) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "getRootDirFilePath", new String[]{"jobName"}, new String[]{jobName}, Level.FINE);
		DevOpsModel devopsModel = new DevOpsModel();
		String jenkinsDirFilePath = devopsModel.getJenkinsRootDirPath();
		if (jobName.contains(DevOpsConstants.PATH_SEPARATOR.toString()))
			jobName = jobName.replace(DevOpsConstants.PATH_SEPARATOR.toString(), DevOpsConstants.JOBS_PATH.toString());
		String finalPath = jenkinsDirFilePath + DevOpsConstants.JOBS_PATH.toString() + jobName + DevOpsConstants.PATH_SEPARATOR.toString() + DevOpsConstants.SERVICENOW_PIPELINE_INFO_FILE_NAME.toString();
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "getRootDirFilePath", new String[]{"jobName", "finalPath"}, new String[]{jobName, finalPath}, Level.INFO);
		return finalPath;
	}

	/*
	 *method called when api path : /sn-devops/snupdate_{jobName}
	 *returns 200 is success else 410
	 *file path will be {JENKINS_HOME}/snPipelineInfo.json
	 *Request data format:
	 *{jobName: pipelineName, track:true, "testInfo": {"stages": {"Build": "/Users/StepBuild","Deploy": "/Users/Deploy"}}, "pipeline": "/Users/TestSummary")
	 *Note: This api doesn't make new entries in the file
	 *for multibranch pipeline using ONLY jobName as the key
	 */
	private boolean _updatePipelineInfoFile(String token, StringBuffer content) {
		if (content != null) {
			JSONObject apiResponse = JSONObject.fromObject(content.toString());
			if (apiResponse != null) {
				String jobName = apiResponse.get(DevOpsConstants.JOBNAME_ATTR.toString()).toString();
				Object instanceUrlObj = apiResponse.get(DevOpsConstants.INSTANCE_URL_ATTR.toString());
				Object toolIdObj = apiResponse.get(DevOpsConstants.TOOL_ID_ATTR.toString());
				if (toolIdObj == null || instanceUrlObj == null || jobName == null)
					return false;
				String instanceUrl = instanceUrlObj.toString();
				String toolId = toolIdObj.toString();
				String rootDirFilePath = getRootDirFilePath(jobName);
				if (updateResponseInFile(jobName, apiResponse, rootDirFilePath, toolId, instanceUrl)) {
					return true;
				}
			}

		}
		return false;
	}

	public boolean updateResponseInFile(String jobName, JSONObject apiResponse, String rootDirFilePath, String toolId, String instanceUrl) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "updateResponseInFile", new String[]{"jobName"}, new String[]{jobName}, Level.FINE);
		try {
			FilePath pipelineInfoFile = new FilePath(new File(rootDirFilePath));
			if (pipelineInfoFile.exists()) {
				String fileContents = pipelineInfoFile.readToString();
				JSONObject pipelineInfo = JSONObject.fromObject(fileContents);
				if (pipelineInfo != null) {
					String configKey = GenericUtils.getConfigEntryTrackKey(instanceUrl, toolId);
					JSONObject trackObj = GenericUtils.getTrackInfoForConfigKey(pipelineInfo, configKey);
					if (trackObj != null) {
						JSONObject updatedTrackObj = getUpdatedResponse(apiResponse, trackObj);
						pipelineInfo.put(configKey, updatedTrackObj);
						pipelineInfoFile.write(pipelineInfo.toString(), "UTF-8");
						String logMessage = "jobName : " + jobName + " pipelineInfoFile: " + rootDirFilePath + " is updated with content: " + pipelineInfo.toString();
						GenericUtils.printDebug(DevOpsRootAction.class.getName(), "updateResponseInFile", logMessage, Level.FINE);
						return true;
					}
				}
			}
			String logMessage = "jobName : " + jobName + " pipelineInfoFile: " + rootDirFilePath + " is missing";
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "updateResponseInFile", logMessage, Level.WARNING);
			return false;
		} catch (Exception e) {
			String logMessage = "Failed to Update PipelineInfoFile for jobName : " + jobName + " pipelineInfoFile: " + rootDirFilePath + " Exception: " + e.getMessage();
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "updateResponseInFile", logMessage, Level.SEVERE);
			e.printStackTrace();
			return false;
		}
	}

	/*
	 *edits the request data to match the format of file data
	 */
	public JSONObject getUpdatedResponse(JSONObject apiResponse, JSONObject fileData) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "getUpdatedResponse", new String[]{"apiResponse", "fileData"}, new String[]{apiResponse.toString(), fileData.toString()}, Level.FINE);
		JSONObject pipelineInfo = fileData.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
		if (pipelineInfo != null) {
			if (apiResponse.containsKey(DevOpsConstants.TRACKING_RESPONSE_ATTR.toString())) {
				pipelineInfo.put(DevOpsConstants.TRACKING_RESPONSE_ATTR.toString(), apiResponse.get(DevOpsConstants.TRACKING_RESPONSE_ATTR.toString()));
			}
			if (apiResponse.containsKey(DevOpsConstants.TEST_INFO_RESPONSE.toString())) {
				pipelineInfo.put(DevOpsConstants.TEST_INFO_RESPONSE.toString(), apiResponse.get(DevOpsConstants.TEST_INFO_RESPONSE.toString()));
			}
		}
		fileData.put(DevOpsConstants.COMMON_RESPONSE_RESULT.toString(), pipelineInfo);
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "getUpdatedResponse", new String[]{"apiResponse", "fileData"}, new String[]{apiResponse.toString(), fileData.toString()}, Level.FINE);
		return fileData;
	}

	public void doDynamic(StaplerRequest request, StaplerResponse response) {
		StringBuffer content = new StringBuffer();
		try {
			String requestType = "Call back api";
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", new String[]{"request"}, new String[]{GenericUtils.getRequestInfo(request, null)}, Level.FINE);
			// read request content
			String token = request.getOriginalRestOfPath().substring(1).trim(); //Strip leading slash

			CharBuffer dest = CharBuffer.allocate(1024);
			try {
				BufferedReader reader = request.getReader();
				while (reader.read(dest) > 0) {
					dest.rewind();
					content.append(dest.toString());
				}
			} catch (Exception e) {
				response.setStatus(400);
				GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", "Failed to read request content, request: " + GenericUtils.getRequestInfo(request, null) + ", response: " + GenericUtils.getResponseInfo(response) + "\n Exception: " + GenericUtils.getStackTraceAsString(e), Level.SEVERE);
				return;
			}

			if (token.startsWith(DevOpsConstants.FREESTYLE_CALLBACK_URL_IDENTIFIER.toString()) && content != null && content.length() > 0 && content.toString().trim().contains("changeRequestId")) {
				try {
					requestType = "Display FreeStyle Change Request Info api call";
					boolean result = _displayFreestyleChangeRequestInfo(token, content);
					if (result) {
						response.setStatus(200);
						GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", requestType + " successfully completed for request: " + GenericUtils.getRequestInfo(request, content.toString()) + ", response: " + GenericUtils.getResponseInfo(response), Level.FINE);
						return;
					}
					response.setStatus(400);
					GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", requestType + " failed for request: " + GenericUtils.getRequestInfo(request, content.toString()) + ", response: " + GenericUtils.getResponseInfo(response), Level.SEVERE);
					return;
				} catch (Exception e) {
					response.setStatus(400);
					GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", requestType + " failed for request: " + GenericUtils.getRequestInfo(request, content.toString()) + ", response: " + GenericUtils.getResponseInfo(response) + "\n Exception: " + GenericUtils.getStackTraceAsString(e), Level.SEVERE);
					return;
				}

			} else if (token.startsWith(DevOpsConstants.PIPELINE_CALLBACK_URL_IDENTIFIER.toString()) && content != null && content.length() > 0 && content.toString().trim().contains("changeRequestId")) {
				try {
					requestType = "Display Pipeline Change Request Info api call";
					boolean result = _displayPipelineChangeRequestInfo(token, content);
					if (result) {
						response.setStatus(200);
						GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", requestType + " successfully completed for request: " + GenericUtils.getRequestInfo(request, content.toString()) + ", response: " + GenericUtils.getResponseInfo(response), Level.FINE);
						return;
					}
					response.setStatus(400);
					GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", requestType + " failed for request: " + GenericUtils.getRequestInfo(request, content.toString()) + ", response: " + GenericUtils.getResponseInfo(response), Level.SEVERE);
					return;
				} catch (Exception e) {
					response.setStatus(400);
					GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", requestType + " failed for request: " + GenericUtils.getRequestInfo(request, content.toString()) + ", response: " + GenericUtils.getResponseInfo(response) + "\n Exception: " + GenericUtils.getStackTraceAsString(e), Level.SEVERE);
					return;
				}
			} else if (token.startsWith(DevOpsConstants.CHECK_CONFIGURATION.toString())) {
				requestType = "Config Check";
				String toolIdValue = request.getParameter("toolId");
				String instanceUrl = request.getParameter("instanceUrl");
				if (GenericUtils.isEmpty(toolIdValue) || GenericUtils.isEmpty(instanceUrl)) {
					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
					response.setContentType("text/plain");
					try {
						response.getWriter().print("Query parameters (toolId, instanceUrl) are required");
					} catch (IOException e) {
						e.printStackTrace();
					}
					GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", requestType + " Failed, reason: Query parameters (toolId, instanceUrl) are required for request: " + GenericUtils.getRequestInfo(request, content.toString()) + ", response: " + GenericUtils.getResponseInfo(response), Level.SEVERE);
					return;
				}
				instanceUrl = GenericUtils.removeTrailingSlashes(instanceUrl);
				DevOpsConfigurationEntry devopsConfig = GenericUtils.getDevOpsConfigurationEntryByInstanceUrlAndToolId(instanceUrl, toolIdValue);
				if (devopsConfig == null) {
					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
					response.setContentType("text/plain");
					try {
						response.getWriter().print("Could not find an active DevOps configuration for toolId " + toolIdValue + " and instanceUrl " + instanceUrl);
					} catch (IOException e) {
						e.printStackTrace();
					}
					GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", requestType + " Failed, reason: Could not find an active DevOps configuration for toolId " + toolIdValue + " and instanceUrl " + instanceUrl + " for request: " + GenericUtils.getRequestInfo(request, content.toString()) + ", response: " + GenericUtils.getResponseInfo(response), Level.SEVERE);
					return;
				}
				JSONObject jsonResult = _sendDummyNotification(toolIdValue, devopsConfig);
				if (null == jsonResult) {
					response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
					response.setContentType("text/plain");
					try {
						response.getWriter().print("Internal server error");
					} catch (IOException e) {
						e.printStackTrace();
					}
					GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", requestType + " Failed, reason: failed to send dummy notification, request: " + GenericUtils.getRequestInfo(request, content.toString()) + ", response: " + GenericUtils.getResponseInfo(response), Level.SEVERE);
					return;
				} else {
					response.setStatus(HttpServletResponse.SC_OK);
					response.setContentType("text/plain");
					try {
						response.getWriter().print("Success");
					} catch (IOException e) {
						e.printStackTrace();
					}
					GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", requestType + " completed successfully, request: " + GenericUtils.getRequestInfo(request, content.toString()) + ", response: " + GenericUtils.getResponseInfo(response), Level.FINE);
					return;
				}
			}

			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", new String[]{"message"}, new String[]{"Callback handler called with token: " + token + " / content: " + content.toString()}, Level.INFO);

			boolean result = false;
			requestType = "call back";
			if (token.startsWith(DevOpsConstants.FREESTYLE_CALLBACK_URL_IDENTIFIER.toString()) && content != null && content.length() > 0) {
				requestType = "freestyle call back";
				result = _handleFreestyleCallback(token, content);
			} else if (token.startsWith(DevOpsConstants.PIPELINE_CALLBACK_URL_IDENTIFIER.toString()) && content != null && content.length() > 0) {
				requestType = "pipeline call back";
				result = _handlePipelineCallback(token, content);
			} else if (token.startsWith(DevOpsConstants.PIPELINE_INFO_UPDATE_IDENTIFIER.toString()) && content != null && content.length() > 0) {
				requestType = "pipeline info update api call";
				result = _updatePipelineInfoFile(token, content);
			} else if (token.startsWith(DevOpsConstants.PIPELINE_INFO_DELETE_IDENTIFIER.toString())) {
				requestType = "pipeline info update api call";
				result = _deleteConfigEntryInInfoFiles(content);
			}

			if (result) {
				response.setHeader("Result", "Jenkins webhook triggered successfully");
				response.setStatus(200);
				GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", requestType + " completed successfully, request: " + GenericUtils.getRequestInfo(request, content.toString()) + ", response: " + GenericUtils.getResponseInfo(response), Level.FINE);
				return;
			} else {
				response.setStatus(410); // 410 Gone
				GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", requestType + " Failed, request: " + GenericUtils.getRequestInfo(request, content.toString()) + ", response: " + GenericUtils.getResponseInfo(response), Level.SEVERE);
			}

		} catch (Exception e) {
			response.setStatus(500);
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", "callback api failed ,Exception" + e.getMessage() + " for request: " + GenericUtils.getRequestInfo(request, content.toString()) + ", response: " + GenericUtils.getResponseInfo(response) + "\n Exception: " + GenericUtils.getStackTraceAsString(e), Level.SEVERE);
		}
	}

	public static JSONObject getTrackInfoForConfigKey(String jobName, String path, String key) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "getTrackInfoForConfigKey", new String[]{"jobName", "path"}, new String[]{jobName, path}, Level.INFO);
		try {
			FilePath pipelineInfoFile = new FilePath(new File(path));
			if (!pipelineInfoFile.exists())
				return null;
			String fileContents = pipelineInfoFile.readToString();
			JSONObject pipelineInfo = JSONObject.fromObject(fileContents);
			if (pipelineInfo != null) {
				return GenericUtils.getTrackInfoForConfigKey(pipelineInfo, key);
			}
			return null;
		} catch (Exception e) {
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "getTrackInfoForConfigKey", "reading pipelineInfoFile file: " + path + " failed" + "\n Exception: " + GenericUtils.getStackTraceAsString(e), Level.SEVERE);
			return null;
		}
	}

	public static JSONObject getTrackingObjectFromFile(String path) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "getTrackingObjectFromFile", new String[]{"path"}, new String[]{path}, Level.INFO);
		try {
			FilePath pipelineInfoFile = new FilePath(new File(path));
			if (!pipelineInfoFile.exists())
				return null;
			String fileContents = pipelineInfoFile.readToString();
			JSONObject pipelineInfo = JSONObject.fromObject(fileContents);
			return pipelineInfo;
		} catch (Exception e) {
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "getTrackingObjectFromFile", "reading pipelineInfoFile file: " + path + " failed" + "\n Exception: " + GenericUtils.getStackTraceAsString(e), Level.SEVERE);
			return null;
		}
	}

	public static Boolean updateInfoInFile(String jobName, /*JSONObject infoAPIResponse*/ JSONObject configsTrackInfo, String path) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "updateInfoInFile", new String[]{"jobName", "path"}, new String[]{jobName, path}, Level.INFO);
		try {
			FilePath pipelineInfoFile = new FilePath(new File(path));
			pipelineInfoFile.write(configsTrackInfo.toString(), "UTF-8");
			return true;
		} catch (Exception e) {
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "updateInfoInFile", "update pipelineInfoFile file: " + path + " failed" + "\n Exception: " + GenericUtils.getStackTraceAsString(e), Level.SEVERE);
			return false;
		}
	}

	public static Boolean _deleteConfigEntryInInfoFiles(StringBuffer content) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_deleteConfigEntryInInfoFiles", new String[]{}, new String[]{}, Level.INFO);
		try {
			if (content != null) {
				JSONObject apiResponse = JSONObject.fromObject(content.toString());
				if (apiResponse != null) {
					Object instanceUrlObj = apiResponse.get(DevOpsConstants.INSTANCE_URL_ATTR.toString());
					Object toolIdObj = apiResponse.get(DevOpsConstants.TOOL_ID_ATTR.toString());
					if (toolIdObj == null || instanceUrlObj == null)
						return false;
					String instanceUrl = instanceUrlObj.toString();
					String toolId = toolIdObj.toString();
					DevOpsModel devopsModel = new DevOpsModel();
					String jenkinsDirFilePath = devopsModel.getJenkinsRootDirPath() + DevOpsConstants.JOBS_PATH.toString();
					GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_deleteConfigEntryInInfoFiles", new String[]{"jenkinsDirFilePath"}, new String[]{jenkinsDirFilePath}, Level.INFO);
					FilePath jenkinsRootDir = new FilePath(new File(jenkinsDirFilePath));
					if (jenkinsRootDir.exists() && !GenericUtils.isEmpty(instanceUrl) && !GenericUtils.isEmpty(toolId)) {
						_checkAndDeleteConfigEntryInInfoFiles(jenkinsRootDir, instanceUrl, toolId);
						return true;
					}
				}
			}
			return false;
		} catch (Exception e) {
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_deleteConfigEntryInInfoFiles", "_deleteConfigEntryInInfoFiles failed" + "\n Exception: " + GenericUtils.getStackTraceAsString(e), Level.SEVERE);
			return false;
		}
	}

	public static void _checkAndDeleteConfigEntryInInfoFiles(FilePath jenkinsRootDir, String instanceUrl, String toolId) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_checkAndDeleteConfigEntryInInfoFiles", new String[]{"instanceUrl", "toolId"}, new String[]{instanceUrl, toolId}, Level.INFO);
		try {
			String pipelineInfoFile = null;
			String folderPipelineInfoFile = null;
			List<FilePath> contents = new ArrayList<FilePath>(jenkinsRootDir.list());
			for (FilePath jobPath : contents) {
				pipelineInfoFile = jobPath + DevOpsConstants.PATH_SEPARATOR.toString() + DevOpsConstants.SERVICENOW_PIPELINE_INFO_FILE_NAME.toString();
				FilePath pipelineInfoPath = new FilePath(new File(pipelineInfoFile));
				if (pipelineInfoPath.exists()) {
					String fileContents = pipelineInfoPath.readToString();
					JSONObject pipelineInfo = JSONObject.fromObject(fileContents);
					if (pipelineInfo != null) {
						String configKey = GenericUtils.getConfigEntryTrackKey(instanceUrl, toolId);
						if (pipelineInfo.containsKey(configKey))
							pipelineInfoPath.write(pipelineInfo.discard(configKey).toString(), "UTF-8");
					}
				} else {
					folderPipelineInfoFile = jobPath + DevOpsConstants.JOBS_PATH.toString();
					FilePath folderPipelineDir = new FilePath(new File(folderPipelineInfoFile));
					if (folderPipelineDir.exists()) {
						_checkAndDeleteConfigEntryInInfoFiles(folderPipelineDir, instanceUrl, toolId);
					}
				}
			}
			return;
		} catch (Exception e) {
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_checkAndDeleteConfigEntryInInfoFiles", "_checkAndDeleteConfigEntryInInfoFiles failed" + "\n Exception: " + GenericUtils.getStackTraceAsString(e), Level.SEVERE);
			return;
		}
	}

	public static Boolean deletePipelineInfoFiles() {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "deletePipelineInfoFiles", new String[]{}, new String[]{}, Level.INFO);
		try {
			DevOpsModel devopsModel = new DevOpsModel();
			String jenkinsDirFilePath = devopsModel.getJenkinsRootDirPath() + DevOpsConstants.JOBS_PATH.toString();
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "deletePipelineInfoFiles", new String[]{"jenkinsDirFilePath"}, new String[]{jenkinsDirFilePath}, Level.INFO);
			FilePath jenkinsRootDir = new FilePath(new File(jenkinsDirFilePath));
			if (jenkinsRootDir.exists()) {
				checkAndDeletePipelineInfoFiles(jenkinsRootDir);
			}
			return true;
		} catch (Exception e) {
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "deletePipelineInfoFiles", "delete pipelineInfoFiles failed" + "\n Exception: " + GenericUtils.getStackTraceAsString(e), Level.SEVERE);
			return false;
		}
	}

	public static void checkAndDeletePipelineInfoFiles(FilePath jenkinsRootDir) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "checkAndDeletePipelineInfoFiles", new String[]{}, new String[]{}, Level.INFO);
		try {
			String pipelineInfoFile = null;
			String folderPipelineInfoFile = null;
			List<FilePath> contents = new ArrayList<FilePath>(jenkinsRootDir.list());
			for (FilePath jobPath : contents) {
				pipelineInfoFile = null;
				pipelineInfoFile = jobPath + DevOpsConstants.PATH_SEPARATOR.toString() + DevOpsConstants.SERVICENOW_PIPELINE_INFO_FILE_NAME.toString();
				FilePath pipelineInfoPath = new FilePath(new File(pipelineInfoFile));
				if (pipelineInfoPath.exists()) {
					pipelineInfoPath.delete();
				} else {
					folderPipelineInfoFile = null;
					folderPipelineInfoFile = jobPath + DevOpsConstants.JOBS_PATH.toString();
					FilePath folderPipelineDir = new FilePath(new File(folderPipelineInfoFile));
					if (folderPipelineDir.exists()) {
						checkAndDeletePipelineInfoFiles(folderPipelineDir);
					}
				}
			}
			return;
		} catch (Exception e) {
			GenericUtils.printDebug(DevOpsRootAction.class.getName(), "checkAndDeletePipelineInfoFiles", "checkAndDeletePipelineInfoFiles  failed" + "\n Exception: " + GenericUtils.getStackTraceAsString(e), Level.SEVERE);
			return;
		}
	}

	public static Boolean getTrackedJob(String key) {
		Boolean tracking;
		synchronized (trackedJobs) {
			tracking = trackedJobs.get(key);
		}
		return tracking;
	}

	public static void setTrackedJob(String key) {
		Boolean tracking = Boolean.valueOf(true);
		synchronized (trackedJobs) {
			trackedJobs.put(key, tracking);
		}
	}

	public static Boolean removeTrackedJob(String key) {
		Boolean tracking = false;
		synchronized (trackedJobs) {
			if (trackedJobs.containsKey(key)) {
				tracking = trackedJobs.remove(key);
			}
		}
		return tracking;
	}

	public static void setSnPipelineInfo(String key, DevOpsModel.DevOpsPipelineInfo pipelineInfo) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "setSnPipelineInfo", new String[]{"key"}, new String[]{key}, Level.FINE);
		synchronized (snPipelineInfo) {
			snPipelineInfo.put(key, pipelineInfo);
		}
	}

	public static DevOpsModel.DevOpsPipelineInfo getSnPipelineInfo(String key) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "getSnPipelineInfo", new String[]{"key"}, new String[]{key}, Level.FINE);
		DevOpsModel.DevOpsPipelineInfo pipelineInfo;
		synchronized (snPipelineInfo) {
			pipelineInfo = snPipelineInfo.get(key);
		}
		return pipelineInfo;
	}

	public static void removeSnPipelineInfo(String key) {
		synchronized (snPipelineInfo) {
			if (snPipelineInfo.containsKey(key)) {
				snPipelineInfo.remove(key);
			}
		}
	}

	public static String getChangeRequestContent(String jobId) {
		String content;
		synchronized (changeRequestContent) {
			content = changeRequestContent.get(jobId);
		}
		return content;
	}

	public static String removeChangeRequestContent(String jobId) {
		String content;
		synchronized (changeRequestContent) {
			content = changeRequestContent.remove(jobId);
		}
		return content;
	}

	// called from dispatcher
	public static String getCallbackContent(String jobId) {
		String content;
		synchronized (callbackContent) {
			content = callbackContent.get(jobId);
		}
		return content;
	}

	public static String removeCallbackContent(String jobId) {
		String content;
		synchronized (callbackContent) {
			content = callbackContent.remove(jobId);
		}
		return content;
	}

	// called from dispatcher
	public static void setCallbackContent(String jobId, String content) {
		if (jobId != null && content != null) {
			synchronized (callbackContent) {
				callbackContent.put(jobId, content.trim());
			}
		}
	}

	public static String removeCallbackToken(String jobId) {
		String token;
		synchronized (callbackToken) {
			token = callbackToken.remove(jobId);
		}
		return token;
	}

	public static String getToken(String jobId) {
		String token;
		synchronized (jobs) {
			token = jobs.get(jobId);
		}
		return token;
	}

	public static String getJobId(String token) {
		String jobId;
		synchronized (webhooks) {
			jobId = webhooks.get(token);
		}
		return jobId;
	}

	// called from task dispatcher
	public static void registerWebhook(String token, String jobId) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "registerWebhook", new String[]{"message"}, new String[]{"Registering freestyle webhook with token: " + token}, Level.INFO);
		synchronized (webhooks) {
			webhooks.put(token, jobId);
		}
	}

	// called from task dispatcher
	public static void registerJob(String jobId, String token) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "registerJob", new String[]{"message"}, new String[]{"Registering freestyle job with id: " + jobId}, Level.INFO);
		synchronized (jobs) {
			jobs.put(jobId, token);
		}
	}

	// not used
	public static void deregisterWebhook(String token) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "deregisterWebhook", new String[]{"message"}, new String[]{"Deregistering freestyle webhook with token: " + token}, Level.INFO);
		synchronized (webhooks) {
			webhooks.remove(token);
		}
	}

	// not used
	public static void deregisterJob(String jobId) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "deregisterJob", new String[]{"message"}, new String[]{"Deregistering freestyle job with id: " + jobId}, Level.INFO);
		synchronized (jobs) {
			jobs.remove(jobId);
		}
	}

	public static void registerPipelineWebhook(DevOpsPipelineChangeStepExecution exec) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "registerPipelineWebhook", new String[]{"message"}, new String[]{"Registering pipeline webhook with token: " + exec.getToken()}, Level.INFO);
		synchronized (pipelineWebhooks) {
			pipelineWebhooks.put(exec.getToken(), exec);
		}
	}

	public static void deregisterPipelineWebhook(DevOpsPipelineChangeStepExecution exec) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "deregisterPipelineWebhook", new String[]{"message"}, new String[]{"Deregistering pipeline webhook with token: " + exec.getToken()}, Level.INFO);
		synchronized (pipelineWebhooks) {
			pipelineWebhooks.remove(exec.getToken());
		}
	}

	// Intercepts the incoming HTTP requests, looking for the /sn-devops/ URL identifier. If one is found,
	// forward it to the next filter in the chain (doDynamic) which will verify if the token is valid
	@Override
	public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		String pathInfo = request.getPathInfo();
		if (pathInfo != null && pathInfo.startsWith("/" + DevOpsConstants.CALLBACK_URL_IDENTIFIER.toString() + "/")) {
			chain.doFilter(request, response);
			return true;
		}
		return false;
	}

}
