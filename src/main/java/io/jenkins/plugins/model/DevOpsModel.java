package io.jenkins.plugins.model;

import static io.jenkins.plugins.DevOpsRunListener.DevOpsStageListener.getCurrentStageId;
import static io.jenkins.plugins.DevOpsRunListener.DevOpsStageListener.getCurrentStageName;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.EnvVars;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.ScheduleResult;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;
import io.jenkins.plugins.DevOpsRootAction;
import io.jenkins.plugins.DevOpsRunListener;
import io.jenkins.plugins.DevOpsRunStatusAction;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.pipeline.steps.DevOpsPipelineMapStep;
import io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineChangeStepExecution;
import io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineMapStepExecution;
import io.jenkins.plugins.utils.CommUtils;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import jenkins.model.Jenkins;
import jenkins.triggers.SCMTriggerItem;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

public class DevOpsModel {

	public final Pattern urlPatt;
	private boolean queueJobs;

	public DevOpsModel() {
		this.urlPatt = Pattern.compile(
				"^(https?):\\/\\/[-a-zA-Z0-9+&@#\\/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#\\/%=~_|]");
	}

	private void printDebug(String methodName, String[] variables, String[] values, Level logLevel) {
		GenericUtils
				.printDebug(DevOpsModel.class.getName(), methodName, variables, values,
						logLevel);
	}

	public boolean isQueueJobs() {
		return queueJobs;
	}

	public void setQueueJobs(boolean queue) {
		this.queueJobs = queue;
	}

	public boolean isApproved(String result) {
		boolean b = false;
		printDebug("isApproved", new String[]{"result"}, new String[]{result}, Level.INFO);
		try {
			JSONObject jsonObject = JSONObject.fromObject(result);
			if (jsonObject
					.containsKey(DevOpsConstants.CALLBACK_RESULT_ATTR.toString())) {
				result = jsonObject
						.getString(DevOpsConstants.CALLBACK_RESULT_ATTR.toString());

				if (result.equals(DevOpsConstants.CALLBACK_RESULT_SUCCESS.toString())) {
					b = true;
				} else {
					b = false;
				}
			}
		} catch (Exception e) {
			printDebug("isApproved", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.SEVERE);
		}
		return b;
	}

	public boolean isCanceled(String result) {
		boolean b = false;
		printDebug("isCanceled", new String[]{"result"}, new String[]{result}, Level.INFO);
		try {
			JSONObject jsonObject = JSONObject.fromObject(result);
			if (jsonObject
					.containsKey(DevOpsConstants.CALLBACK_RESULT_ATTR.toString())) {
				result = jsonObject
						.getString(DevOpsConstants.CALLBACK_RESULT_ATTR.toString());
				if (result.equals(DevOpsConstants.CALLBACK_RESULT_CANCELED.toString())) {
					b = true;
				} else {
					b = false;
				}
			}
		} catch (Exception e) {
			printDebug("isCanceled", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.SEVERE);
		}
		return b;
	}

	public String getChangeComments(String result) {
		String changeComments = "";
		printDebug("getChangeComments", new String[]{"result"}, new String[]{result}, Level.INFO);
		try {
			JSONObject jsonObject = JSONObject.fromObject(result);
			if (jsonObject.containsKey(DevOpsConstants.CALLBACK_RESULT_COMMENTS.toString())) {
				return jsonObject.getString(DevOpsConstants.CALLBACK_RESULT_COMMENTS.toString());
			}
		} catch (Exception e) {
			printDebug("getChangeComments", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.INFO);
		}
		return changeComments;
	}

	public String getChangeRequestInfo(String result) {
		String changeRequestId = "";
		printDebug("getChangeRequestInfo", null, null, Level.INFO);
		try {
			JSONObject jsonObject = JSONObject.fromObject(result);
			if (jsonObject.containsKey(DevOpsConstants.CHANGE_REQUEST_ID.toString())) {
				return jsonObject.getString(DevOpsConstants.CHANGE_REQUEST_ID.toString());
			}
		} catch (Exception e) {
			printDebug("getChangeRequestInfo", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.INFO);
		}
		return changeRequestId;
	}

	public boolean isCommFailure(String result) {
		boolean b = false;
		printDebug("isCommFailure", new String[]{"result"}, new String[]{result}, Level.INFO);
		try {
			JSONObject jsonObject = JSONObject.fromObject(result);
			if (jsonObject
					.containsKey(DevOpsConstants.CALLBACK_RESULT_ATTR.toString())) {
				result = jsonObject
						.getString(DevOpsConstants.CALLBACK_RESULT_ATTR.toString());
				if (result.equals(DevOpsConstants.CALLBACK_RESULT_COMM_FAILURE.toString())) {
					b = true;
				} else {
					b = false;
				}
			}
		} catch (Exception e) {
			printDebug("isCommFailure", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.SEVERE);
		}
		return b;
	}

	// from handleFreestyle (or anywhere that doesn't have a runId associated)
	public DevOpsPipelineInfo checkIsTracking(Queue.Item item) {
		printDebug("checkIsTracking(item)", null, null, Level.FINE);
		if (item == null)
			return new DevOpsPipelineInfo(false);
		if (!(item.task instanceof Job<?, ?>))
			return new DevOpsPipelineInfo(false);
		Job<?, ?> job = (Job<?, ?>) item.task;
		// check if global prop and supported job type
		if (!checkIsValid(job))
			return new DevOpsPipelineInfo(false);

		if (GenericUtils.isDevOpsConfigurationEnabled() && !GenericUtils.isDevOpsConfigurationValid())
			return new DevOpsPipelineInfo(false, true, DevOpsConstants.FAILURE_REASON_INVALID_CONFIGURATION_UI.toString());

		String jobUrl = job.getAbsoluteUrl();
		String jobName = job.getFullName();
		// check on endpoint
		printDebug("checkIsTracking(item)", new String[]{"tracking"}, new String[]{"true"}, Level.FINE);
		return isTrackingEndpoint(job, jobUrl, jobName, job.getPronoun(), null, false);
	}

	// from onStarted (or anywhere that has a runId associated)
	public DevOpsPipelineInfo checkIsTracking(Job<?, ?> job, String runId, String branchName) {
		printDebug("checkIsTracking(run)", new String[]{"runId", "branchName"}, new String[]{runId, branchName}, Level.FINE);
		if (job == null)
			return new DevOpsPipelineInfo(false);
		// check if global prop and supported job type
		if (!checkIsValid(job))
			return new DevOpsPipelineInfo(false);

		if (GenericUtils.isDevOpsConfigurationEnabled() && !GenericUtils.isDevOpsConfigurationValid())
			return new DevOpsPipelineInfo(false, true, DevOpsConstants.FAILURE_REASON_INVALID_CONFIGURATION_UI.toString());


		String jobUrl = job.getAbsoluteUrl();
		String jobName = job.getFullName();
		// check on cache
		if (isTrackingCache(jobName, runId))
			return new DevOpsPipelineInfo(true);
		// check on endpoint
		return isTrackingEndpoint(job, jobUrl, jobName, job.getPronoun(), branchName,
				GenericUtils.isMultiBranch(job));
	}

	public DevOpsPipelineInfo getPipelineInfo(Job<?, ?> job, String runId) {
		if (job == null)
			return new DevOpsPipelineInfo(false);
		// check if global prop and supported job type
		if (!checkIsValid(job))
			return new DevOpsPipelineInfo(false);

		if (GenericUtils.isDevOpsConfigurationEnabled() && !GenericUtils.isDevOpsConfigurationValid())
			return new DevOpsPipelineInfo(false, true, DevOpsConstants.FAILURE_REASON_INVALID_CONFIGURATION_UI.toString());

		String jobName = job.getFullName();
		String key = getTrackingKey(jobName, runId);
		printDebug("getPipelineInfo", new String[]{"jobName", "key"}, new String[]{jobName, key}, Level.FINE);
		return DevOpsRootAction.getSnPipelineInfo(key);
	}

	public boolean checkIsTrackingCache(Job<?, ?> job, String runId) {
		printDebug("checkIsTrackingCache", new String[]{"runId"}, new String[]{runId}, Level.FINE);
		if (job == null)
			return false;
		// check if global prop and supported job type
		if (!checkIsValid(job))
			return false;
		String jobName = job.getFullName();
		// check on cache
		return isTrackingCache(jobName, runId);
	}

	public boolean checkIsValid(Job<?, ?> job) {
		printDebug("checkIsValid", null, null, Level.FINE);
		if (job == null ||  job.getPronoun() == null)
			return false;
		// check if the job pronoun is the one we support
		String pronoun = job.getPronoun();
		if (!(pronoun.equalsIgnoreCase(DevOpsConstants.PIPELINE_PRONOUN.toString()) ||
				pronoun.equalsIgnoreCase(DevOpsConstants.BITBUCKET_MULTI_BRANCH_PIPELINE_PRONOUN.toString()) ||
				pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString()) ||
				pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) ||
				pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString())))
			return false;
		if (!GenericUtils.isDevOpsConfigurationEnabled())
			return false;
		printDebug("checkIsValid", new String[]{"valid"}, new String[]{"true"}, Level.FINE);
		return true;
	}

	public String getTrackingKey(String jobName, String runId) {
		printDebug("getTrackingKey", new String[]{"runId", "jobName"}, new String[]{runId, jobName}, Level.FINE);
		if (jobName == null)
			return null;
		return jobName + DevOpsConstants.TRACKING_KEY_SEPARATOR.toString() + runId;
	}

	public void addToPipelineInfoCache(String jobName, String runId, DevOpsPipelineInfo pipelineInfo) {
		printDebug("addToPipelineInfoCache", new String[]{"runId", "jobName"}, new String[]{runId, jobName}, Level.FINE);
		String key = getTrackingKey(jobName, runId);
		if (key == null)
			return;
		if (pipelineInfo == null)
			return;
		DevOpsRootAction.setSnPipelineInfo(key, pipelineInfo);
	}

	public void removeFromPipelineInfoCache(String jobName, String runId) {
		printDebug("removeFromPipelineInfoCache", new String[]{"runId", "jobName"}, new String[]{runId, jobName}, Level.FINE);
		String key = getTrackingKey(jobName, runId);
		if (key == null)
			return;
		DevOpsRootAction.removeSnPipelineInfo(key);
	}

	public void addToTrackingCache(String jobName, String runId, DevOpsPipelineInfo pipelineInfo) {
		printDebug("addToTrackingCache", new String[]{"runId", "jobName"}, new String[]{runId, jobName}, Level.FINE);
		String key = getTrackingKey(jobName, runId);
		printDebug("addToTrackingCache", new String[]{"key"}, new String[]{key}, Level.FINE);
		if (key == null)
			return;
		DevOpsRootAction.setTrackedJob(key);
	}

	public void removeFromTrackingCache(String jobName, String runId) {
		printDebug("removeFromTrackingCache", new String[]{"runId", "jobName"}, new String[]{runId, jobName}, Level.FINE);
		String key = getTrackingKey(jobName, runId);
		if (key == null)
			return;
		DevOpsRootAction.removeTrackedJob(key);
	}

	public boolean isTrackingCache(String jobName, String runId) {
		printDebug("isTrackingCache", new String[]{"runId", "jobName"}, new String[]{runId, jobName}, Level.FINE);
		String key = getTrackingKey(jobName, runId);
		if (key == null)
			return false;
		Boolean tracking = DevOpsRootAction.getTrackedJob(key);
		if (tracking == null)
			return false;
		printDebug("isTrackingCache", new String[]{"tracking"}, new String[]{String.valueOf(tracking.booleanValue())}, Level.FINE);
		return tracking.booleanValue();
	}

	public JSONObject checkPipelineInfoInFile(String jobName, String path) {
		printDebug("checkPipelineInfoInFile", new String[]{"jobName"}, new String[]{jobName}, Level.FINE);
		if (jobName == null)
			return null;
		printDebug("checkPipelineInfoInFile", new String[]{"path"}, new String[]{path}, Level.FINE);
		return DevOpsRootAction.checkInfoInFile(jobName, path);
	}

	public Boolean updatePipelineInfoInFile(String jobName, JSONObject infoAPIResponse, String path) {
		printDebug("updatePipelineInfoInFile", new String[]{"jobName"}, new String[]{jobName}, Level.FINE);
		if (jobName == null)
			return false;
		printDebug("checkPipelineInfoInFile", new String[]{"path"}, new String[]{path}, Level.FINE);
		return DevOpsRootAction.updateInfoInFile(jobName, infoAPIResponse, path);
	}

	/*
	 *Checking /{JENKINS_HOME}/snPipelineInfo.json file each time before making a call to /sn-devops/pipelineInfo api
	 *if api response available in file -> retrieve the response and use it, else -> make call to /sn-devops/pipelineInfo
	 *api response for multibranch pipeline uses ONLY jobName as the key
	 */
	public DevOpsPipelineInfo isTrackingEndpoint(Job<?, ?> job, String jobUrl, String jobName, String pronoun, String branchName, boolean isMultiBranch) {
		printDebug("isTrackingEndpoint", new String[]{"jobUrl", "jobName", "pronoun", "branchName", "isMultiBranch"}, new String[]{jobUrl, jobName, pronoun, branchName, String.valueOf(isMultiBranch)}, Level.FINE);
		String result = null;
		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
		String jobDir = job.getRootDir().getAbsolutePath();
		if (isMultiBranch)
			jobDir = jobDir.split(DevOpsConstants.MULTIBRANCH_PATH_SEPARATOR.toString())[0];
		String infoFilePath = jobDir + DevOpsConstants.PATH_SEPARATOR.toString() + DevOpsConstants.SERVICENOW_PIPELINE_INFO_FILE_NAME.toString();
		JSONObject infoAPIResponse = checkPipelineInfoInFile(jobName, infoFilePath);
		if (!(GenericUtils.checkIfAttributeExist(infoAPIResponse, DevOpsConstants.TRACKING_RESPONSE_ATTR.toString())) || devopsConfig.isTrackCheck()) {
			JSONObject params = new JSONObject();
			params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), devopsConfig.getToolId());
			params.put("url", jobUrl);
			params.put("name", jobName);
			params.put("pronoun", pronoun);
			if (branchName != null)
				params.put("branchName", branchName);
			params.put("isMultiBranch", isMultiBranch);
			if(!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {
				Map<String, String> tokenDetails = new HashMap<String, String>();
				tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
						devopsConfig.getTokenText(devopsConfig.getSecretCredentialId()));
				infoAPIResponse = CommUtils.callV2Support(DevOpsConstants.REST_GET_METHOD.toString(),
						devopsConfig.getTrackingUrl(), params, null,
						devopsConfig.getUser(), devopsConfig.getPwd(), null, null,tokenDetails);
			}else {
				infoAPIResponse = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
						devopsConfig.getTrackingUrl(), params, null,
						devopsConfig.getUser(), devopsConfig.getPwd(), null, null);
			}
			
			if (GenericUtils.checkIfAttributeExist(infoAPIResponse, DevOpsConstants.TRACKING_RESPONSE_ATTR.toString())) {
				updatePipelineInfoInFile(jobName, infoAPIResponse, infoFilePath);
			}
		}
		result = GenericUtils.parseResponseResult(infoAPIResponse,
				DevOpsConstants.TRACKING_RESPONSE_ATTR.toString());
		printDebug("isTrackingEndpoint", new String[]{DevOpsConstants.TRACKING_RESPONSE_ATTR.toString()}, new String[]{result}, Level.FINE);
		if (result != null) {
			if (result.equalsIgnoreCase("true")) {
				DevOpsPipelineInfo pipelineInfo = new DevOpsPipelineInfo(true);
				JSONObject resultObj = infoAPIResponse.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
				if (resultObj.containsKey(DevOpsConstants.TEST_INFO_RESPONSE.toString())) {
					pipelineInfo.setTestInfo(resultObj.getJSONObject(DevOpsConstants.TEST_INFO_RESPONSE.toString()));
				}
				return pipelineInfo;
			} else if (result.equalsIgnoreCase("false")) {
				return new DevOpsPipelineInfo(false);
			} else if (result.contains(DevOpsConstants.COMMON_RESULT_FAILURE.toString())) {
				if (result.contains(DevOpsConstants.FAILURE_REASON_CONN_REFUSED.toString()))
					return new DevOpsPipelineInfo(false, true, DevOpsConstants.FAILURE_REASON_CONN_REFUSED_UI.toString());
				else if (result.contains(DevOpsConstants.FAILURE_REASON_USER_NOAUTH.toString()))
					return new DevOpsPipelineInfo(false, true, DevOpsConstants.FAILURE_REASON_USER_NOAUTH_UI.toString());
				else if (pronoun != null &&
						result.contains(DevOpsConstants.FAILURE_REASON_PIPELINE_DETAILS_NOT_FOUND.toString()) &&
						(pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) || pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString())))
					return new DevOpsPipelineInfo(false);
			}
		}
		return new DevOpsPipelineInfo(false, true, DevOpsConstants.FAILURE_REASON_GENERIC_UI.toString());
	}

	public static class DevOpsPipelineInfo {
		private boolean track;
		private JSONObject testInfo;
		private boolean unreacheable;
		private String errorMessage;

		public DevOpsPipelineInfo(boolean track) {
			this.track = track;
		}

		public DevOpsPipelineInfo(boolean track, JSONObject testInfo) {
			this.track = track;
			this.testInfo = testInfo;
		}

		public DevOpsPipelineInfo(boolean track, boolean unreacheable, String errorMessage) {
			this.track = track;
			this.unreacheable = unreacheable;
			this.errorMessage = errorMessage;
		}

		public boolean isUnreacheable() {
			return unreacheable;
		}

		public void setUnreacheable(boolean unreacheable) {
			this.unreacheable = unreacheable;
		}

		public boolean isTrack() {
			return track;
		}

		public void setTrack(boolean track) {
			this.track = track;
		}

		public JSONObject getTestInfo() {
			return testInfo;
		}

		public void setTestInfo(JSONObject testInfo) {
			this.testInfo = testInfo;
		}

		public void setErrorMessage(String message) {
			this.errorMessage = message;
		}

		public String getErrorMessage() {
			return errorMessage;
		}

		public String toString() {
			return "track: " + this.track + ", unreacheable: " + this.unreacheable;
		}
	}

	// When parallel pipelines are triggered and Job run #1 is waiting for approval,
	// Jenkins will not automatically schedule Job run #2 once #1 finishes.
	// This method is called from the build step, and does the manual scheduling.
	public boolean scheduleNextJob(Run<?, ?> run, Job<?, ?> job, int quietPeriod) {
		printDebug("scheduleNextJob", null, null, Level.FINE);
		List<Cause> causes = run.getCauses();
		if (causes.size() > 1) {
			List<Cause> _causes = new ArrayList<>();
			for (int i = 1; i < causes.size(); i++) {
				Cause c = causes.get(i);
				if (c instanceof Cause.UpstreamCause)
					_causes.add(c);
			}
			CauseAction cAction = new CauseAction(_causes);
			Jenkins jenkins = Jenkins.getInstanceOrNull();
			if (jenkins != null) {
				Queue queue = jenkins.getQueue();
				Queue.Task project = jenkins.getItemByFullName(job.getFullName(),
						AbstractProject.class);
				if (queue != null && project != null && cAction != null) {
					ScheduleResult sResult =
							queue.schedule2(project, quietPeriod, cAction);
					return sResult.isCreated();
				}
			}
		}
		return false;
	}

	// called from dispatcher
	public String getJobId(Queue.Item item, Job<?, ?> job) {
		printDebug("getJobId", null, null, Level.FINE);
		String queueId = String.valueOf(item.getId());
		String jobName = job.getUrl();
		return queueId + "/" + jobName;
	}

	// (freestyle only) called from runListener.setupEnvironment, after runListener.onStarted
	public String getJobId(Run<?, ?> run, Job<?, ?> job) {
		printDebug("getJobId", null, null, Level.FINE);
		// queueId is used in case of change control for freestyle
		String queueId = String.valueOf(run.getQueueId());
		String jobName = job.getUrl();
		return queueId + "/" + jobName;
	}

	// get from 'changeRequestContent' map
	public String getChangeRequestContent(String jobId) {
		printDebug("getChangeRequestContent", new String[]{"jobId"}, new String[]{jobId}, Level.FINE);
		return DevOpsRootAction.getChangeRequestContent(jobId);
	}

	// remove from 'changeRequestContent' map
	public String removeChangeRequestContent(String jobId) {
		printDebug("removeChangeRequestContent", new String[]{"jobId"}, new String[]{jobId}, Level.FINE);
		return DevOpsRootAction.removeChangeRequestContent(jobId);
	}

	// get from 'callbackContent' map
	public String getCallbackResult(String jobId) {
		printDebug("getCallbackResult", new String[]{"jobId"}, new String[]{jobId}, Level.FINE);
		return DevOpsRootAction.getCallbackContent(jobId);
	}

	// called from build step
	public String removeCallbackResult(String jobId) {
		printDebug("removeCallbackResult", new String[]{"jobId"}, new String[]{jobId}, Level.FINE);
		return DevOpsRootAction.removeCallbackContent(jobId);
	}

	// called from build step
	public String removeCallbackToken(String jobId) {
		printDebug("removeCallbackToken", new String[]{"jobId"}, new String[]{jobId}, Level.FINE);
		return DevOpsRootAction.removeCallbackToken(jobId);
	}

	// get from 'jobs' map
	public String getToken(String jobId) {
		printDebug("getToken", new String[]{"jobId"}, new String[]{jobId}, Level.FINE);
		return DevOpsRootAction.getToken(jobId);
	}

	// new token
	public String getNewToken(String pronoun) {
		printDebug("getNewToken", new String[]{"pronoun"}, new String[]{pronoun}, Level.FINE);
		String token = null;
		if (pronoun != null) {
			if (pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) ||
					pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString()))
				token = DevOpsConstants.FREESTYLE_CALLBACK_URL_IDENTIFIER.toString() +
						DevOpsConstants.CALLBACK_TOKEN_SEPARATOR.toString() +
						java.util.UUID.randomUUID().toString();
			else if (pronoun
					.equalsIgnoreCase(DevOpsConstants.PIPELINE_PRONOUN.toString()) ||
					pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString()) ||
					pronoun.equalsIgnoreCase(
							DevOpsConstants.BITBUCKET_MULTI_BRANCH_PIPELINE_PRONOUN
									.toString()))
				token = DevOpsConstants.PIPELINE_CALLBACK_URL_IDENTIFIER.toString() +
						DevOpsConstants.CALLBACK_TOKEN_SEPARATOR.toString() +
						java.util.UUID.randomUUID().toString();
		}
		return token;
	}

	// Jenkins singleton
	public String getJenkinsUrl() {
		printDebug("getJenkinsUrl", null, null, Level.FINE);
		String url = null;
		try {
			Jenkins jenkins = Jenkins.getInstanceOrNull();
			if (jenkins != null)
				url = jenkins.getRootUrl();
		} catch (IllegalStateException e) {
			printDebug("getJenkinsUrl", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.SEVERE);
		}
		return url;
	}

	//returns JENKINS_HOME path
	public String getJenkinsRootDirPath() {
		printDebug("getJenkinsRootDirPath", null, null, Level.FINE);
		String path = null;
		try {
			Jenkins jenkins = Jenkins.getInstanceOrNull();
			if (jenkins != null)
				path = jenkins.getRootDir().getAbsolutePath();
		} catch (IllegalStateException e) {
			printDebug("getJenkinsRootDirPath", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.FINE);
		}
		return path;
	}

	// get from 'webhooks' map
	public boolean isWaiting(String token) {
		printDebug("isWaiting", null, null, Level.FINE);
		String _jobId = DevOpsRootAction.getJobId(token);
		if (_jobId != null)
			return true;
		return false;
	}

	public boolean checkUrlValid(String url) {
		printDebug("checkUrlValid", new String[]{"url"}, new String[]{url}, Level.FINE);
		Matcher m = urlPatt.matcher(url);
		return m.matches();
	}

	private String getCallbackUrl(String token, String jenkinsUrl)
			throws URISyntaxException {
		printDebug("getCallbackUrl", new String[]{"token", "jenkinsUrl"},
				new String[]{token, jenkinsUrl}, Level.FINE);
		java.net.URI baseUri = new java.net.URI(jenkinsUrl);
		java.net.URI relative = new java.net.URI(
				DevOpsConstants.CALLBACK_URL_IDENTIFIER.toString() + "/" + token);
		java.net.URI path = baseUri.resolve(relative);
		return path.toString();
	}

	public String replaceLast(String string, String substring, String replacement) {
		int index = string.lastIndexOf(substring);
		if (index == -1)
			return string;
		return string.substring(0, index) + replacement +
				string.substring(index + substring.length());
	}

	public String sendBuildAndToken(String token, String jenkinsUrl, String buildUrl,
									String jobUrl, String jobName, String stageName,
									DevOpsPipelineNode stageNode, Boolean isMultiBranch, String branchName, Boolean isChangeClose) {
		printDebug("sendBuildAndToken", null, null, Level.FINE);
		JSONObject params = new JSONObject();
		JSONObject data = new JSONObject();
		String result = null;
		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), devopsConfig.getToolId());
		params.put(DevOpsConstants.TOOL_TYPE_ATTR.toString(),
				DevOpsConstants.TOOL_TYPE.toString());
		try {
			String callbackUrl = getCallbackUrl(token, jenkinsUrl);
			data.put(DevOpsConstants.CALLBACK_URL_ATTR.toString(), callbackUrl);
			data.put(DevOpsConstants.JOB_URL_ATTR.toString(), jobUrl);
			data.put(DevOpsConstants.BUILD_URL_ATTR.toString(), buildUrl);
			data.put(DevOpsConstants.IS_MULTI_BRANCH_ATTR.toString(),
					Boolean.toString(isMultiBranch));
			data.put(DevOpsConstants.SCM_BRANCH_NAME.toString(), branchName);

			if (stageNode != null) {
				data.put(DevOpsConstants.JOB_NAME_ATTR.toString(),
						jobName + DevOpsConstants.JOB_STAGE_SEPARATOR.toString() +
								stageName);
				data.put(DevOpsConstants.JOB_URL_ATTR.toString(), replaceLast(jobUrl, "/",
						DevOpsConstants.JOB_STAGE_SEPARATOR.toString() + stageNode.getName() +
								"/"));//jobUrl+DevOpsConstants.JOB_STAGE_SEPARATOR.toString()+stageName);
			} else
				data.put(DevOpsConstants.JOB_NAME_ATTR.toString(), jobName);

			if (isChangeClose && null != stageNode)
				data.put(DevOpsConstants.PARENT_BUILD_URL_ATTR.toString(),
						jenkinsUrl + stageNode.getExecutionUrl() + "wfapi/describe");

			addStageNodeParams(stageNode, data, jobName, jobUrl, DevOpsConstants.REST_PUT_METHOD);

			if (!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {
				
				Map<String, String> tokenDetails = new HashMap<String, String>();
				tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
						devopsConfig.getTokenText(devopsConfig.getSecretCredentialId()));
				result = GenericUtils.parseResponseResult(
						CommUtils.callV2Support(DevOpsConstants.REST_PUT_METHOD.toString(),
								devopsConfig.getChangeControlUrl() + "/" + token, params, data.toString(),
								devopsConfig.getUser(), devopsConfig.getPwd(), null, null,tokenDetails),
						DevOpsConstants.COMMON_RESPONSE_CHANGE_CTRL.toString());
				
			}else {
				result = GenericUtils.parseResponseResult(
						CommUtils.call(DevOpsConstants.REST_PUT_METHOD.toString(),
								devopsConfig.getChangeControlUrl() + "/" + token, params, data.toString(),
								devopsConfig.getUser(), devopsConfig.getPwd(), null, null),
						DevOpsConstants.COMMON_RESPONSE_CHANGE_CTRL.toString());
				
			}

		} catch (Exception e) {
			printDebug("sendBuildAndToken", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.SEVERE);
		}
		return result;
	}

	public String sendIsUnderChgControl(String jobUrl, String jobName,
										String stageName, DevOpsPipelineNode stageNode, Boolean isMultiBranch,
										String branchName) {
		printDebug("sendIsUnderChgControl", null, null, Level.FINE);
		JSONObject params = new JSONObject();
		String result = null;
		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), devopsConfig.getToolId());
		params.put(DevOpsConstants.TOOL_TYPE_ATTR.toString(),
				DevOpsConstants.TOOL_TYPE.toString());
		params.put(DevOpsConstants.JOB_URL_ATTR.toString(), jobUrl);
		params.put(DevOpsConstants.IS_MULTI_BRANCH_ATTR.toString(),
				Boolean.toString(isMultiBranch));
		params.put(DevOpsConstants.SCM_BRANCH_NAME.toString(), branchName);

		if (stageNode != null) {

			params.put(DevOpsConstants.JOB_NAME_ATTR.toString(),
					jobName + DevOpsConstants.JOB_STAGE_SEPARATOR.toString() + stageNode.getName());
			params.put(DevOpsConstants.JOB_URL_ATTR.toString(), replaceLast(jobUrl, "/",
					DevOpsConstants.JOB_STAGE_SEPARATOR.toString() + stageNode.getName() + "/"));
		} else
			params.put(DevOpsConstants.JOB_NAME_ATTR.toString(), jobName);

		addStageNodeParams(stageNode, params, jobName, jobUrl, DevOpsConstants.REST_GET_METHOD);

		try {
			if(!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {
				
				Map<String, String> tokenDetails = new HashMap<String, String>();
				tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
						devopsConfig.getTokenText(devopsConfig.getSecretCredentialId()));
				result = GenericUtils
						.parseResponseResult(
								CommUtils.callV2Support(DevOpsConstants.REST_GET_METHOD.toString(), devopsConfig.getChangeControlUrl(), params, null,
										devopsConfig.getUser(), devopsConfig.getPwd(), null, null,tokenDetails),
								DevOpsConstants.COMMON_RESPONSE_CHANGE_CTRL.toString());
				
			}else {
				result = GenericUtils
						.parseResponseResult(
								CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(), devopsConfig.getChangeControlUrl(), params, null,
										devopsConfig.getUser(), devopsConfig.getPwd(), null, null),
								DevOpsConstants.COMMON_RESPONSE_CHANGE_CTRL.toString());
				
			}

		} catch (Exception e) {
			printDebug("sendIsUnderChgControl", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.SEVERE);
		}
		return result;
	}

	public String sendJobAndCallbackUrl(String token, String jobUrl, String jobName,
										String stageName, DevOpsPipelineNode stageNode, String jenkinsUrl,
										String endpointUrl, String user, String pwd,
										String tool, JSONObject jobDetails,
										Boolean isMultiBranch, String branchName, String changeRequestDetails, PipelineChangeResponse changeResponse,
										String applicationName, String snapshotName) {
		printDebug("sendJobAndCallbackUrl", null, null, Level.FINE);
		JSONObject params = new JSONObject();
		JSONObject data = new JSONObject();
		String result = null;
		params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), tool);
		params.put(DevOpsConstants.TOOL_TYPE_ATTR.toString(),
				DevOpsConstants.TOOL_TYPE.toString());
		try {
			String callbackUrl = getCallbackUrl(token, jenkinsUrl);
			data.put(DevOpsConstants.CALLBACK_URL_ATTR.toString(), callbackUrl);
			data.put(DevOpsConstants.JOB_URL_ATTR.toString(), jobUrl);
			data.put(DevOpsConstants.IS_MULTI_BRANCH_ATTR.toString(),
					Boolean.toString(isMultiBranch));
			data.put(DevOpsConstants.SCM_BRANCH_NAME.toString(), branchName);

			if (stageNode != null && null != stageNode.getPipelineExecutionUrl())
				data.put(DevOpsConstants.PIPLINE_EXECUTION_URL.toString(), stageNode.getPipelineExecutionUrl());

			if (stageName != null && !stageName.isEmpty()) {
				data.put(DevOpsConstants.JOB_NAME_ATTR.toString(),
						jobName + DevOpsConstants.JOB_STAGE_SEPARATOR.toString() +
								stageName);
				data.put(DevOpsConstants.JOB_NAME_ATTR.toString(), jobName);
				data.put(DevOpsConstants.STAGENAME_ATTR.toString(), stageName);
				data.put(DevOpsConstants.JOB_URL_ATTR.toString(), replaceLast(jobUrl, "/",
						DevOpsConstants.JOB_STAGE_SEPARATOR.toString() + stageName +
								"/"));
			} else
				data.put(DevOpsConstants.JOB_NAME_ATTR.toString(), jobName);

			addStageNodeParams(stageNode, data, jobName, jobUrl, DevOpsConstants.REST_POST_METHOD);
			if (data.get(DevOpsConstants.JOB_PARENT_STAGE_EXECUTION_URL.toString()) != null) {
				String parentStageExecutionURL = data.get(DevOpsConstants.JOB_PARENT_STAGE_EXECUTION_URL.toString()).toString();
				if (parentStageExecutionURL != null)
					jobDetails.put(DevOpsConstants.BUILD_URL_ATTR.toString(), data.get(DevOpsConstants.JOB_PARENT_STAGE_EXECUTION_URL.toString()));
			}
			data.put(DevOpsConstants.JOB_DETAILS_ATTR.toString(), jobDetails);

			if (GenericUtils.isNotEmpty(changeRequestDetails)) {
				JSONObject crAttrJSON = JSONObject.fromObject(changeRequestDetails);
				data.put(DevOpsConstants.CR_ATTRS.toString(), crAttrJSON);
			}

			//Adding config parameters to request body
			if(GenericUtils.isNotEmpty(applicationName) && GenericUtils.isNotEmpty(snapshotName)) {
				data.put(DevOpsConstants.CONFIG_APP_NAME.toString(), applicationName);
				data.put(DevOpsConstants.CONFIG_SNAPSHOT_NAME.toString(), snapshotName);
			}

			JSONObject response = CommUtils.call(DevOpsConstants.REST_POST_METHOD.toString(), endpointUrl, params,
					data.toString(), user, pwd, null, null);
			result = GenericUtils
					.parseResponseResult(response, DevOpsConstants.COMMON_RESPONSE_CHANGE_CTRL.toString());

			if(changeResponse != null) { // fill in message details if present
				String message = GenericUtils.parseResponseResult(response, DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
				if(!GenericUtils.isEmpty(message)) {
					changeResponse.setMessage(message);
				}
			}
		} catch(JSONException e){
			printDebug("sendJobAndCallbackUrl", new String[]{"JSONException"},
					new String[]{e.getMessage()}, Level.SEVERE);
			JSONObject errorObj = new JSONObject();
			String errorMessage = "Failed to parse changeRequestDetails json." + e.getMessage();
			errorObj.put(DevOpsConstants.COMMON_RESULT_FAILURE.toString(), errorMessage);
			result = errorObj.toString();
		} catch (Exception e) {
			printDebug("sendJobAndCallbackUrl", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.SEVERE);
		}
		return result;
	}
	
	
	public String sendJobAndCallbackUrlV2(String token, String jobUrl, String jobName, String stageName,
			DevOpsPipelineNode stageNode, String jenkinsUrl, String endpointUrl, String user, String pwd, String tool,
			JSONObject jobDetails, Boolean isMultiBranch, String branchName, String changeRequestDetails,
			PipelineChangeResponse changeResponse, String applicationName, String snapshotName, Map<String,String> tokenDetails) {
		printDebug("sendJobAndCallbackUrl", null, null, Level.FINE);
		JSONObject params = new JSONObject();
		JSONObject data = new JSONObject();
		String result = null;
		params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), tool);
		params.put(DevOpsConstants.TOOL_TYPE_ATTR.toString(), DevOpsConstants.TOOL_TYPE.toString());
		try {
			String callbackUrl = getCallbackUrl(token, jenkinsUrl);
			data.put(DevOpsConstants.CALLBACK_URL_ATTR.toString(), callbackUrl);
			data.put(DevOpsConstants.JOB_URL_ATTR.toString(), jobUrl);
			data.put(DevOpsConstants.IS_MULTI_BRANCH_ATTR.toString(), Boolean.toString(isMultiBranch));
			data.put(DevOpsConstants.SCM_BRANCH_NAME.toString(), branchName);

			if (stageNode != null && null != stageNode.getPipelineExecutionUrl())
				data.put(DevOpsConstants.PIPLINE_EXECUTION_URL.toString(), stageNode.getPipelineExecutionUrl());

			if (stageName != null && !stageName.isEmpty()) {
				data.put(DevOpsConstants.JOB_NAME_ATTR.toString(),
						jobName + DevOpsConstants.JOB_STAGE_SEPARATOR.toString() + stageName);
				data.put(DevOpsConstants.JOB_NAME_ATTR.toString(), jobName);
				data.put(DevOpsConstants.STAGENAME_ATTR.toString(), stageName);
				data.put(DevOpsConstants.JOB_URL_ATTR.toString(),
						replaceLast(jobUrl, "/", DevOpsConstants.JOB_STAGE_SEPARATOR.toString() + stageName + "/"));
			} else
				data.put(DevOpsConstants.JOB_NAME_ATTR.toString(), jobName);

			addStageNodeParams(stageNode, data, jobName, jobUrl, DevOpsConstants.REST_POST_METHOD);
			if (data.get(DevOpsConstants.JOB_PARENT_STAGE_EXECUTION_URL.toString()) != null) {
				String parentStageExecutionURL = data.get(DevOpsConstants.JOB_PARENT_STAGE_EXECUTION_URL.toString())
						.toString();
				if (parentStageExecutionURL != null)
					jobDetails.put(DevOpsConstants.BUILD_URL_ATTR.toString(),
							data.get(DevOpsConstants.JOB_PARENT_STAGE_EXECUTION_URL.toString()));
			}
			data.put(DevOpsConstants.JOB_DETAILS_ATTR.toString(), jobDetails);

			if (GenericUtils.isNotEmpty(changeRequestDetails)) {
				JSONObject crAttrJSON = JSONObject.fromObject(changeRequestDetails);
				data.put(DevOpsConstants.CR_ATTRS.toString(), crAttrJSON);
			}

//Adding config parameters to request body
			if (GenericUtils.isNotEmpty(applicationName) && GenericUtils.isNotEmpty(snapshotName)) {
				data.put(DevOpsConstants.CONFIG_APP_NAME.toString(), applicationName);
				data.put(DevOpsConstants.CONFIG_SNAPSHOT_NAME.toString(), snapshotName);
			}

			JSONObject response = CommUtils.callV2Support(DevOpsConstants.REST_POST_METHOD.toString(), endpointUrl, params,
					data.toString(), user, pwd, null, null,tokenDetails);
			result = GenericUtils.parseResponseResult(response, DevOpsConstants.COMMON_RESPONSE_CHANGE_CTRL.toString());

			if (changeResponse != null) { // fill in message details if present
				String message = GenericUtils.parseResponseResult(response,
						DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
				if (!GenericUtils.isEmpty(message)) {
					changeResponse.setMessage(message);
				}
			}
		} catch (JSONException e) {
			printDebug("sendJobAndCallbackUrl", new String[] { "JSONException" }, new String[] { e.getMessage() },
					Level.SEVERE);
			JSONObject errorObj = new JSONObject();
			String errorMessage = "Failed to parse changeRequestDetails json." + e.getMessage();
			errorObj.put(DevOpsConstants.COMMON_RESULT_FAILURE.toString(), errorMessage);
			result = errorObj.toString();
		} catch (Exception e) {
			printDebug("sendJobAndCallbackUrl", new String[] { "exception" }, new String[] { e.getMessage() },
					Level.SEVERE);
		}
		return result;
	}

	public DevOpsJobProperty getJobProperty(Job<?, ?> job) {
		printDebug("getJobProperty", null, null, Level.FINE);
		DevOpsJobProperty jobProperty = job.getProperty(DevOpsJobProperty.class);
		if (jobProperty == null)
			jobProperty = new DevOpsJobProperty();
		return jobProperty;
	}

	public String sendUpdateMapping(String jobUrl, String jobName, String stageName, DevOpsPipelineNode stageNode,
									String stepSysId, Boolean isMultiBranch, String branchName) {
		printDebug("sendUpdateMapping", null, null, Level.FINE);
		JSONObject params = new JSONObject();
		JSONObject data = new JSONObject();
		String result = null;
		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), devopsConfig.getToolId());
		params.put(DevOpsConstants.TOOL_TYPE_ATTR.toString(),
				DevOpsConstants.TOOL_TYPE.toString());
		data.put(DevOpsConstants.JOB_URL_ATTR.toString(), jobUrl);
		data.put(DevOpsConstants.IS_MULTI_BRANCH_ATTR.toString(), Boolean.toString(isMultiBranch));
		data.put(DevOpsConstants.SCM_BRANCH_NAME.toString(), branchName);
		if (stageName != null && !stageName.isEmpty()) {
			data.put(DevOpsConstants.JOB_NAME_ATTR.toString(),
					jobName + DevOpsConstants.JOB_STAGE_SEPARATOR.toString() + stageName);
			data.put(DevOpsConstants.JOB_URL_ATTR.toString(), replaceLast(jobUrl, "/",
					DevOpsConstants.JOB_STAGE_SEPARATOR.toString() + stageName + "/"));
		} else
			data.put(DevOpsConstants.JOB_NAME_ATTR.toString(), jobName);

		if (!GenericUtils.isEmpty(stepSysId))
			data.put(DevOpsConstants.STEP_SYSID_ATTR.toString(), stepSysId);

		addStageNodeParams(stageNode, data, jobName, jobUrl, DevOpsConstants.REST_POST_METHOD);

		try {
			if(!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {
				
				Map<String, String> tokenDetails = new HashMap<String, String>();
				tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
						devopsConfig.getTokenText(devopsConfig.getSecretCredentialId()));
				result = GenericUtils.parseResponseResult(
						CommUtils.callV2Support(DevOpsConstants.REST_POST_METHOD.toString(), devopsConfig.getMappingUrl(), params,
								data.toString(), devopsConfig.getUser(), devopsConfig.getPwd(), null, null,tokenDetails),
						DevOpsConstants.STEP_MAPPING_RESPONSE_ATTR.toString());
			}else {
				result = GenericUtils.parseResponseResult(
						CommUtils.call(DevOpsConstants.REST_POST_METHOD.toString(), devopsConfig.getMappingUrl(), params,
								data.toString(), devopsConfig.getUser(), devopsConfig.getPwd(), null, null),
						DevOpsConstants.STEP_MAPPING_RESPONSE_ATTR.toString());
			}
			
		} catch (Exception e) {
			printDebug("sendIsMappingValid", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.SEVERE);
		}
		return result;
	}

	private void addStageNodeParams(DevOpsPipelineNode stageNode, JSONObject params, String jobName, String jobUrl, DevOpsConstants callMethod) {
		if (stageNode != null) {
			String stageName = stageNode.getName();
			if (stageName != null && !stageName.isEmpty()) {
				params.put(DevOpsConstants.JOB_PARENT_STAGE_NAME.toString(),
						jobName + DevOpsConstants.JOB_STAGE_SEPARATOR.toString() + stageName);
				params.put(DevOpsConstants.JOB_PARENT_STAGE_URL.toString(),
						replaceLast(jobUrl, "/", DevOpsConstants.JOB_STAGE_SEPARATOR.toString() + stageName + "/"));
				// avoid adding entire currentStageNode data to query params for GET method
				if ((callMethod.equals(DevOpsConstants.REST_POST_METHOD) || callMethod.equals(DevOpsConstants.REST_PUT_METHOD))) {
					params.put(DevOpsConstants.JOB_PARENT_STAGE_DATA.toString(), getStageNodeJSONObject(stageNode));
					String parentStageExecutionURL = stageNode.getExecutionUrl();
					if (parentStageExecutionURL != null)
						params.put(DevOpsConstants.JOB_PARENT_STAGE_EXECUTION_URL.toString(), getJenkinsUrl() + parentStageExecutionURL + "wfapi/describe");
				}
			}
		}
	}

	private JSONObject getStageNodeJSONObject(DevOpsPipelineNode stageNode) {
		JSONObject stageNodeJSONObj = new JSONObject();
		stageNodeJSONObj.put("id", stageNode.getId());
		stageNodeJSONObj.put("name", stageNode.getName());
		stageNodeJSONObj.put("parentId", stageNode.getParentId());
		stageNodeJSONObj.put("upstreamStageName", stageNode.getUpstreamStageName());
		stageNodeJSONObj.put("upstreamTaskExecutionURL", stageNode.getUpstreamTaskExecURL());
		return stageNodeJSONObj;
	}


	public CauseOfBlockage getWaitingBlockage(String message) {
		final String _message = message;
		return new CauseOfBlockage() {
			@Override
			public String getShortDescription() {
				return _message;
			}
		};
	}

	public String registerFreestyleAndNotify(Queue.Item item, Job<?, ?> job, String token,
											 String jobId, String jobUrl, String jobName,
											 String jenkinsUrl) {
		printDebug("registerFreestyleAndNotify", null, null, Level.FINE);
		String result = null;
		try {
			JSONObject jobDetails = getJobDetailsForFreestyle(item, job, jenkinsUrl);

			// conditions in which the job should be cancelled (and item removed from the queue):
			// 1 when trigger=scm, if there's no difference between revisions
			//Example: duplicate triggers, and/or race condition where Polling action overlaps with callback receival and release of the job to start running
			/*if (jobDetails.getString(DevOpsConstants.TRIGGER_TYPE_ATTR.toString()).equals("scm")) {
				JSONObject scmChanges = jobDetails.getJSONObject(DevOpsConstants.SCM_CHANGES_ATTR.toString());
				if (scmChanges.containsKey("cancel") && scmChanges.getBoolean("cancel")) {
					output.put("error", true);
					return output;
				}
			}*/

			DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
			if(!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {
				Map<String, String> tokenDetails = new HashMap<String, String>();
				tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
						devopsConfig.getTokenText(devopsConfig.getSecretCredentialId()));
				result = sendJobAndCallbackUrlV2(token,
						jobUrl, jobName, null, null,
						jenkinsUrl, devopsConfig.getChangeControlUrl(), devopsConfig.getUser(),
						devopsConfig.getPwd(), devopsConfig.getToolId(),
						jobDetails, GenericUtils.isMultiBranch((job)), null, getJobProperty(job).getChangeRequestDetails(), null,null,null,tokenDetails);
				
			}else {
				result = sendJobAndCallbackUrl(token,
						jobUrl, jobName, null, null,
						jenkinsUrl, devopsConfig.getChangeControlUrl(), devopsConfig.getUser(),
						devopsConfig.getPwd(), devopsConfig.getToolId(),
						jobDetails, GenericUtils.isMultiBranch((job)), null, getJobProperty(job).getChangeRequestDetails(), null,null,null);
				
			}
			
			/* result: null (if call failed), "unknown", "true", "false" */
			if (result != null) {
				if (result.equalsIgnoreCase(
						DevOpsConstants.COMMON_RESPONSE_VALUE_TRUE.toString())) {
					DevOpsRootAction.registerWebhook(token, jobId);
					DevOpsRootAction.registerJob(jobId, token);
				}
			}

		} catch (Exception e) {
			//GenericUtils.printConsoleLog(listener, "SUCCESS: Register Artifact request was successful.");
			printDebug("registerAndFreestyleNotify", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.SEVERE);
		}
		return result;
	}

	public JSONObject getJobDetailsForFreestyle(Queue.Item item, Job<?, ?> controlledJob,
												String jenkinsUrl)
			throws InterruptedException {
		printDebug("getJobDetailsForFreestyle", null, null, Level.FINE);
		List<Cause> causes = item.getCauses();
		return _getJobDetails(causes, controlledJob, jenkinsUrl);
	}

	public JSONObject getJobDetailsForPipeline(Run<?, ?> run, Job<?, ?> controlledJob,
											   String jenkinsUrl, DevOpsPipelineNode devOpsPipelineNode)
			throws InterruptedException {
		printDebug("getJobDetailsForPipeline", null, null, Level.FINE);
		JSONObject json = new JSONObject();
		if (run != null) {
			if (devOpsPipelineNode != null) {
				String upstreamTaskExecutionURL = devOpsPipelineNode.getUpstreamTaskExecURL();
				if (StringUtils.isEmpty(upstreamTaskExecutionURL)) {
					json.put(DevOpsConstants.MESSAGE_ATTR.toString(),
							"Started by " + upstreamTaskExecutionURL);
					json.put(DevOpsConstants.TRIGGER_TYPE_ATTR.toString(),
							"upstream");
					json.put(DevOpsConstants.UPSTREAM_BUILD_URL_ATTR.toString(),
							upstreamTaskExecutionURL);
				} else {
					List<Cause> causes = run.getCauses();
					json = _getJobDetails(causes, controlledJob, jenkinsUrl);
				}

			} else {
				List<Cause> causes = run.getCauses();
				json = _getJobDetails(causes, controlledJob, jenkinsUrl);
			}
		}
		return json;
	}

	private JSONObject _getJobDetails(List<Cause> causes, Job<?, ?> job,
									  String jenkinsUrl) {
		printDebug("_getJobDetails", null, null, Level.FINE);
		JSONObject json = new JSONObject();
		for (Cause cause : causes) {
			json.put(DevOpsConstants.MESSAGE_ATTR.toString(),
					cause.getShortDescription());
			if (cause instanceof Cause.UserIdCause) {
				Cause.UserIdCause userCause = (Cause.UserIdCause) cause;
				json.put(DevOpsConstants.TRIGGER_TYPE_ATTR.toString(), "user");
				json.put(DevOpsConstants.USERNAME_ATTR.toString(),
						userCause.getUserName());
				json.put(DevOpsConstants.LAST_BUILD_URL_ATTR.toString(), "");
				Run<?, ?> lastRun = job.getLastBuild();
				if (lastRun != null) {
					String lastBuildUrl = jenkinsUrl + lastRun.getUrl();
					Matcher m = urlPatt.matcher(lastBuildUrl);
					if (m.matches())
						json.put(DevOpsConstants.LAST_BUILD_URL_ATTR.toString(),
								lastBuildUrl);
				}
				printDebug("_getJobDetails", new String[]{"cause is UserIdCause"},
						new String[]{userCause.getShortDescription()}, Level.FINE);
				break;
			} else if (cause instanceof Cause.UpstreamCause) {
				Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause) cause;
				json.put(DevOpsConstants.TRIGGER_TYPE_ATTR.toString(), "upstream");

				String upstreamBuildUrl = jenkinsUrl + upstreamCause.getUpstreamUrl() +
						upstreamCause.getUpstreamBuild() + "/";
				Matcher m = urlPatt.matcher(upstreamBuildUrl);
				if (m.matches())
					json.put(DevOpsConstants.UPSTREAM_BUILD_URL_ATTR.toString(),
							upstreamBuildUrl);

				printDebug("_getJobDetails", new String[]{"cause is UpstreamCause"},
						new String[]{upstreamCause.getShortDescription()}, Level.FINE);
				break;
			} else if (cause instanceof SCMTrigger.SCMTriggerCause) {
				SCMTrigger.SCMTriggerCause scmCause = (SCMTrigger.SCMTriggerCause) cause;
				printDebug("_getJobDetails", new String[]{"cause is SCMTriggerCause"},
						new String[]{scmCause.getShortDescription()}, Level.FINE);
				json.put(DevOpsConstants.TRIGGER_TYPE_ATTR.toString(), "scm");
				SCMTriggerItem tItem = SCMTriggerItem.SCMTriggerItems
						.asSCMTriggerItem(job); // tTtem = instance of Project
				if (tItem != null) {
					SCMTrigger trigger = tItem.getSCMTrigger();

					if (trigger != null) {
						File log = trigger.getLogFile();
						if (log != null) {
							if (log.canRead()) {
								try {
									String fContent = Util.loadFile(log);
									json.put(DevOpsConstants.SCM_LOG_ATTR.toString(),
											fContent);
								} catch (IOException e1) {
									e1.printStackTrace();
								}
							}
						}
					}
					@SuppressWarnings("unchecked")
					Collection<SCM> scms = (Collection<SCM>) tItem.getSCMs();
					for (SCM scm : scms) {
						if (scm.getClass().getName()
								.equals(DevOpsConstants.GIT_PLUGIN_SCM_CLASS
										.toString())) {
							json.put(DevOpsConstants.SCM_TYPE_ATTR.toString(), "git");
							// if scmChanges is empty, means that:
							// 		- there was multiple branches tracked, and/or
							//		- the local repo was not yet initialized
							// if scmChanges.cancel == true, will result in the job being cancelled (removed from queue)
							//JSONObject scmChanges = gitModel.getScmChanges(job, item);
							JSONObject scmChanges = new JSONObject();
							json.put(DevOpsConstants.SCM_CHANGES_ATTR.toString(),
									scmChanges);
							break;
						}
					}
				}
				break; // break needed to avoid running the job twice, for the odd case where we receive 2 SCMTriggerCause
			} else {
				printDebug("_getJobDetails",
						new String[]{"cause is " + cause.getClass().getSimpleName()},
						new String[]{cause.getShortDescription()}, Level.FINE);
				json.put(DevOpsConstants.TRIGGER_TYPE_ATTR.toString(), "default");
			}
		}
		return json;
	}

	public String registerPipelineAndNotify(Run<?, ?> run, Job<?, ?> controlledJob,
											String token, String jobUrl, String jobName,
											String stageId, String jenkinsUrl,
											DevOpsPipelineChangeStepExecution stepExecution, PipelineChangeResponse changeResponse) {
		printDebug("registerPipelineAndNotify", null, null, Level.FINE);
		String result = null;
		try {
			DevOpsPipelineNode stageNode = getStageNodeById(run, stageId);
			JSONObject jobDetails =
					getJobDetailsForPipeline(run, controlledJob, jenkinsUrl, stageNode);

			StepContext ctx = stepExecution.getContext();

			EnvVars vars = null;
			try {
				vars = ctx.get(EnvVars.class);
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}

			String changeRequestDetails = stepExecution.getStep().getChangeRequestDetails();
			if (GenericUtils.isNotEmpty(changeRequestDetails) && vars != null)
				changeRequestDetails = vars.expand(changeRequestDetails);

			//Fetching config parameters
			String applicationName = stepExecution.getStep().getApplicationName();
			String snapshotName = stepExecution.getStep().getSnapshotName();

			String buildUrl = DevOpsPipelineGraph.getStageExecutionUrl(stageNode.getPipelineExecutionUrl(), stageNode.getId());
			jobDetails.put(DevOpsConstants.BUILD_URL_ATTR.toString(), buildUrl);
			DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
			if(!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {
				
				Map<String, String> tokenDetails = new HashMap<String, String>();
				tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
						devopsConfig.getTokenText(devopsConfig.getSecretCredentialId()));

				result = sendJobAndCallbackUrlV2(token,
						jobUrl,
						jobName,
						stageNode.getName(), stageNode,
						jenkinsUrl,
						devopsConfig.getChangeControlUrl(),
						devopsConfig.getUser(),
						devopsConfig.getPwd(),
						devopsConfig.getToolId(),
						jobDetails, GenericUtils.isMultiBranch(controlledJob),
						vars != null ? vars.get("BRANCH_NAME") : null,
						changeRequestDetails,  changeResponse,applicationName, snapshotName,tokenDetails);
			}else {
				result = sendJobAndCallbackUrl(token,
						jobUrl,
						jobName,
						stageNode.getName(), stageNode,
						jenkinsUrl,
						devopsConfig.getChangeControlUrl(),
						devopsConfig.getUser(),
						devopsConfig.getPwd(),
						devopsConfig.getToolId(),
						jobDetails, GenericUtils.isMultiBranch(controlledJob),
						vars != null ? vars.get("BRANCH_NAME") : null,
						changeRequestDetails,  changeResponse,applicationName, snapshotName);
			}
			
			// only register webhook if able to post to SN endpoint
			if (null != result && !result.contains(DevOpsConstants.COMMON_RESULT_FAILURE.toString())) {
				if (result.equalsIgnoreCase(
						DevOpsConstants.COMMON_RESPONSE_VALUE_TRUE.toString())) {
					stepExecution.setToken(token);
					DevOpsRootAction.registerPipelineWebhook(stepExecution);
				}
			} else {
				String cause = "";
				if (null != result)
					cause = "Cause: " + result;
				printDebug("registerPipelineAndNotify", new String[]{"message"},
						new String[]{"Register change control failed. Response from sendJobAndCallbackUrl(): " + cause}, Level.FINE);
			}
		} catch (InterruptedException e) {
			printDebug("registerPipelineAndNotify", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.SEVERE);
		}
		return result;
	}

	public enum PipelineChangeAction {
		ABORT,
		WAIT,
	}

	public static class PipelineChangeResponse {
		PipelineChangeAction action;
		String errorMessage;
		String message;

		public String getErrorMessage() {
			return errorMessage;
		}

		public void setErrorMessage(String errorMessage) {
			this.errorMessage = errorMessage;
		}


		public PipelineChangeAction getAction() {
			return action;
		}

		public void setAction(PipelineChangeAction action) {
			this.action = action;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}
	}

	// Called from DevOpsPipelineChangeStepExecution.start()
	public PipelineChangeResponse handlePipeline(Run<?, ?> run, Job<?, ?> controlledJob,
												 DevOpsPipelineChangeStepExecution stepExecution) {

		printDebug("handlePipeline", null, null, Level.FINE);
		PipelineChangeResponse changeResponse = new PipelineChangeResponse();
		//boolean[] result = new boolean[2]; // 0: shouldAbort, 1: shouldWait
		//resp.setResult(result);
		if (run != null && controlledJob != null) {
			String jobUrl = controlledJob.getAbsoluteUrl();
			String jobName = controlledJob.getName();
			String jenkinsUrl = getJenkinsUrl();
			DevOpsPipelineGraph graph = run.getAction(DevOpsRunStatusAction.class).getPipelineGraph();

			if (jobUrl != null && jenkinsUrl != null && jobName != null) {
				String stageId = getCurrentStageId(stepExecution.getContext(), graph);
				DevOpsPipelineNode stageNode = getStageNodeById(run, stageId);

				printDebug("handlePipeline",
						new String[]{"message", "jobUrl", "jobName"},
						new String[]{"Job is under change control", jobUrl,
								jobName}, Level.FINE);
				// Generate a new token
				String token = getNewToken(controlledJob.getPronoun());
				printDebug("handlePipeline", new String[]{"token"},
						new String[]{token}, Level.FINE);

				// Register the Job callback hook, then notify SN
				String _result = registerPipelineAndNotify(run, controlledJob, token,
						jobUrl, jobName, stageNode.getId(), jenkinsUrl,
						stepExecution, changeResponse);
				if (_result != null) {
					if (_result.equalsIgnoreCase(
							DevOpsConstants.COMMON_RESPONSE_VALUE_TRUE
									.toString())) {
						printDebug("handlePipeline",
								new String[]{"message", "token"},
								new String[]{"Job registered", token}, Level.FINE);
						//result[1] = true; // shouldWait=true
						changeResponse.setAction(PipelineChangeAction.WAIT);
					} else {
						printDebug("handlePipeline", new String[]{"message"},
								new String[]{
										"Something went wrong when registering the job"}, Level.WARNING);
						//result[0] = true; // shouldAbort=true
						changeResponse.setAction(PipelineChangeAction.ABORT);
						changeResponse.setErrorMessage(_result);
					}
				} else {
					printDebug("handlePipeline", new String[]{"message"},
							new String[]{
									"Something went wrong when calling SN to register the job"}, Level.WARNING);
					//result[0] = true; // shouldAbort=true
					changeResponse.setAction(PipelineChangeAction.ABORT);
				}
			}
		}

		return changeResponse;
	}

	public String getCommFailureResult() {
		printDebug("getCommFailureResult", null, null, Level.FINE);
		JSONObject result = new JSONObject();
		result.put(DevOpsConstants.CALLBACK_RESULT_ATTR.toString(), DevOpsConstants.CALLBACK_RESULT_COMM_FAILURE.toString());
		return result.toString();
	}

	public String getAbortResult() {
		printDebug("getAbortResult", null, null, Level.FINE);
		JSONObject result = new JSONObject();
		result.put(DevOpsConstants.CALLBACK_CANCELED_ATTR.toString(), "true");
		return result.toString();
	}

	public void setAbortResultForFreestyle(String jobId, String _result) {
		printDebug("setAbortResultForFreestyle", new String[]{"jobId"},
				new String[]{jobId}, Level.FINE);
		String result = GenericUtils.isEmpty(_result) ? getAbortResult() : _result;
		if (jobId != null)
			DevOpsRootAction.setCallbackContent(jobId, result);
	}

	public void setAbortResultForFreestyle(String jobId) {
		printDebug("setAbortResultForFreestyle", new String[]{"jobId"},
				new String[]{jobId}, Level.FINE);
		if (jobId != null)
			DevOpsRootAction.setCallbackContent(jobId, getAbortResult());
	}

	private void cancelItem(Queue.Item item) {
		printDebug("cancelItem", null, null, Level.FINE);
		Jenkins jenkins = Jenkins.getInstanceOrNull();
		if (jenkins != null)
			jenkins.getQueue().cancel(item);
	}

	// Called from DevOpsQueueTaskDispatcher.canRun()
	public CauseOfBlockage handleFreestyle(Queue.Item item,
										   Job<?, ?> job) {
		printDebug("handleFreestyle", null, null, Level.FINE);
		if (item != null && job != null) {

			// First step is to check if this Job has already been evaluated and we have a response in the callbackContent hashmap
			String jobId = getJobId(item, job);
			printDebug("handleFreestyle", new String[]{"callback result -> jobId "}, new String[]{jobId}, Level.INFO);
			String result = getCallbackResult(jobId);
			if (result != null) {
				printDebug("handleFreestyle", new String[]{"callback result"}, new String[]{result}, Level.INFO);
				return null;
			}

			// No response yet for this Job
			else {
				printDebug("handleFreestyle", new String[]{"callback result in else case"}, new String[]{"null"}, Level.INFO);

				String changeRequestContent = getChangeRequestContent(jobId);
				printDebug("handleFreestyle", new String[]{"changeRequestContent"}, new String[]{changeRequestContent}, Level.INFO);
				if (changeRequestContent != null) {
					String changeRequestId = getChangeRequestInfo(changeRequestContent);
					printDebug("handleFreestyle", new String[]{"changeRequestId"}, new String[]{changeRequestId}, Level.INFO);
					if (!GenericUtils.isEmpty(changeRequestId))
						return getWaitingBlockage("Job is waiting for approval on change request: " + changeRequestId);
				}

				String jobUrl = job.getAbsoluteUrl();
				String jobName = job.getName();
				String jenkinsUrl = getJenkinsUrl();
				if (jobUrl != null && jenkinsUrl != null && jobName != null &&
						jobId != null) {
					String token = getToken(jobId);
					// Job already registered
					if (token != null) {
						printDebug("handleFreestyle", new String[]{"message", "token"},
								new String[]{
										"Job already registered, waiting for callback",
										token}, Level.FINE);

						// Job is waiting for callback
						if (isWaiting(token))
							return getWaitingBlockage("Job is waiting for approval");
					}
					// Job not registered
					else {
						printDebug("handleFreestyle", new String[]{"message", "token"},
								new String[]{"Job not registered", "null"}, Level.FINE);
						// Check if job is being tracked
						if (checkIsTracking(item).isTrack()) {
							// If Job is under change control, register and notify SN with callback URL
							String _result = sendIsUnderChgControl(jobUrl, jobName, null, null,
									GenericUtils.isMultiBranch((job)), null);
							if (_result != null) {
								// Job is under change control
								if (_result.equalsIgnoreCase(
										DevOpsConstants.COMMON_RESPONSE_VALUE_TRUE
												.toString())) {
									printDebug("handleFreestyle",
											new String[]{"message", "jobUrl", "jobName"},
											new String[]{"Job is under change control",
													jobUrl, jobName}, Level.FINE);

									// Generate a new token
									token = getNewToken(job.getPronoun());
									printDebug("handleFreestyle", new String[]{"token"},
											new String[]{token}, Level.FINE);

									// Register the Job callback hook, then notify SN
									_result = registerFreestyleAndNotify(item, job,
											token, jobId, jobUrl, jobName, jenkinsUrl);
									if (_result != null) {
										// Job registered successfully
										if (_result.equalsIgnoreCase(
												DevOpsConstants.COMMON_RESPONSE_VALUE_TRUE
														.toString())) {
											printDebug("handleFreestyle",
													new String[]{"message", "token"},
													new String[]{"Job registered", token}, Level.FINE);
											return getWaitingBlockage(
													"Job is waiting for approval");
										}
										// Could not register the Job callback, so there are no webhooks registered
										else {
											printDebug("handleFreestyle",
													new String[]{"message", "_result"},
													new String[]{
															"Something went wrong when registering the job",
															_result}, Level.WARNING);
											if (GenericUtils.isNotEmpty(_result) && _result.contains(DevOpsConstants.COMMON_RESULT_FAILURE.toString())) {
												setAbortResultForFreestyle(jobId, _result);
											} else {
												setAbortResultForFreestyle(jobId);
											}
										}
									}
									// Call to SN failed
									else {
										printDebug("handleFreestyle", new String[]{"message"},
												new String[]{
														"Something went wrong when calling SN to register the job"}, Level.WARNING);
										setAbortResultForFreestyle(jobId);
									}
								}
								// Job is not under change control
								else if (_result.equalsIgnoreCase(
										DevOpsConstants.COMMON_RESPONSE_VALUE_FALSE
												.toString())) {
									printDebug("handleFreestyle",
											new String[]{"message", "jobUrl"},
											new String[]{"Job is not under change control",
													jobUrl}, Level.FINE);
								} else if (_result.equalsIgnoreCase(
										DevOpsConstants.COMMON_RESPONSE_VALUE_UNKNOWN
												.toString())) {
									printDebug("handleFreestyle", new String[]{"message"},
											new String[]{
													"Job is not associated with any step"}, Level.FINE);
									setAbortResultForFreestyle(jobId);
								}

							}
							// Failed to check if the Job is under change control
							else {
								printDebug("handleFreestyle", new String[]{"message"},
										new String[]{
												"Something went wrong when checking if job is under change control"}, Level.WARNING);
								setAbortResultForFreestyle(jobId);
							}
						}
					}
				}
			}
		}
		return null;
	}

	// Called from DevOpsPipelineMapStepExecution.run()
	public boolean handleStepMapping(Run<?, ?> run,
									 Job<?, ?> controlledJob,
									 DevOpsPipelineMapStepExecution devOpsPipelineMapStepExecution, EnvVars vars) {

		boolean result = false;
		if (devOpsPipelineMapStepExecution == null)
			return result;

		DevOpsPipelineMapStep devOpsPipelineMapStep = devOpsPipelineMapStepExecution.getStep();
		if (devOpsPipelineMapStep == null)
			return result;

		String stepSysId = devOpsPipelineMapStep.getStepSysId();

		printDebug("handleStepMapping", new String[]{"stepSysId --"},
				new String[]{stepSysId}, Level.FINE);

		if (run != null && controlledJob != null) {
			String jobUrl = controlledJob.getAbsoluteUrl();
			String jobName = controlledJob.getName();
			String jenkinsUrl = getJenkinsUrl();
			DevOpsPipelineGraph graph = run.getAction(DevOpsRunStatusAction.class).getPipelineGraph();

			if (jobUrl != null && jenkinsUrl != null && jobName != null) {

				String stageId = getCurrentStageId(devOpsPipelineMapStepExecution.getContext(), graph);
				DevOpsPipelineNode stageNode = getStageNodeById(run, stageId);

				// return true if step is already associated
				if (isStepAssociated(run, stageNode.getId())) {
					this.associateStepToNode(run, stageNode.getId());
					printDebug("handleStepMapping", new String[]{"message"},
							new String[]{"Step has been associated already"}, Level.FINE);
					return true;
				}

				String _result = sendUpdateMapping(jobUrl, jobName, stageNode.getName(), stageNode,
						stepSysId,
						GenericUtils.isMultiBranch(controlledJob),
						vars != null ? vars.get("BRANCH_NAME") : null);
				if (null != _result && !_result.contains(DevOpsConstants.COMMON_RESULT_FAILURE.toString())) {
					// associated successfully
					if (_result.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_VALUE_TRUE.toString())) {
						result = true;
						this.associateStepToNode(run, stageNode.getName());
						printDebug("handleStepMapping", new String[]{"message"},
								new String[]{"Step associated successfully"}, Level.FINE);
					}
					// could not associate for some reason
					else
						printDebug("handleStepMapping", new String[]{"message"},
								new String[]{"Step could not be associated - invalid"}, Level.WARNING);
				} else {
					String cause = "";
					if (null != _result)
						cause = "Cause: " + _result;
					printDebug("handleStepMapping", new String[]{"message"}, new String[]{
							"Something when wrong when calling SN to associate the step. Reason: " + cause}, Level.WARNING);
				}
			}
		}
		return result;
	}

	public DevOpsPipelineNode getStageNodeByName(Run<?, ?> run, String stageName) {
		printDebug("getStageNodeByName", null, null, Level.FINE);
		if (run != null) {
			DevOpsRunStatusAction action = run.getAction(DevOpsRunStatusAction.class);
			if (action != null) {
				return action.getPipelineGraph().getNodeByName(stageName);
			}
		}
		return null;
	}

	public DevOpsPipelineNode getStageNodeById(Run<?, ?> run, String stageId) {
		printDebug("getStageNodeByName", null, null, Level.FINE);
		if (run != null) {
			DevOpsRunStatusAction action = run.getAction(DevOpsRunStatusAction.class);
			if (action != null) {
				return action.getPipelineGraph().getNodeById(stageId);
			}
		}
		return null;
	}

	private boolean isStepAssociated(Run<?, ?> run, String stageId) {
		DevOpsRunStatusAction action = run.getAction(DevOpsRunStatusAction.class);
		if (action != null) {
			return action.getPipelineGraph().isStepAssociated(stageId);
		}
		return false;
	}

	public void associateStepToNode(Run<?, ?> run, String stageName) {
		if (null != run && null != stageName) {
			DevOpsRunStatusAction action = run.getAction(DevOpsRunStatusAction.class);
			if (action != null) {
				action.getPipelineGraph().addStepToNode(stageName);
			}
		}

	}

	public boolean isChangeStepInProgress(Run<?, ?> run, String stageId) {
		DevOpsPipelineNode currentNode = getStageNodeById(run, stageId);
		if (null != currentNode)
			return currentNode.isChangeCtrlInProgress();
		return false;
	}

	public void markChangeStepToProgress(Run<?, ?> run, String stageId) {
		DevOpsPipelineNode currentNode = getStageNodeById(run, stageId);
		if (null != currentNode)
			currentNode.setChangeCtrlInProgress(true);
	}
	public String handleArtifactRegistration(StepContext stepContext, Run<?, ?> run, TaskListener listener, String artifactPayload, EnvVars vars) {

		String result = null;
		printDebug("handleArtifactRegistration", new String[]{"artifactPayload --"},
				new String[]{artifactPayload}, Level.FINE);
		String buildNumber = null, stageName = null, branchName = null, jobUrl = null, jobName = null;
		if (run != null) {
			DevOpsRunStatusAction action = run.getAction(DevOpsRunStatusAction.class);
			if (action != null) {
				stageName = getCurrentStageName(stepContext, action.getPipelineGraph());
			}
		}

		// TODO - this is being repeated in other method too, see if we can generalise it by an object
		if (vars != null) {
			jobName = vars.get("JOB_NAME");
			buildNumber = vars.get("BUILD_NUMBER");

			if (GenericUtils.isNotEmpty(vars.get("GIT_BRANCH")))
				branchName = vars.get("GIT_BRANCH");
			else if (GenericUtils.isNotEmpty(vars.get("BRANCH_NAME")))
				branchName = vars.get("BRANCH_NAME");
			jobUrl = vars.get("JOB_URL");
		} else {
			return result;
		}

		result = registerArtifact(listener, artifactPayload, jobName, jobUrl, buildNumber, stageName, branchName,
				GenericUtils.isFreeStyleProject(run));

		return result;

	}

	public String registerArtifact(TaskListener listener, String artifactsPayload, String jobName, String jobUrl,
								   String buildNumber, String stageName, String branchName, boolean isFreeStyle) {

		printDebug("registerArtifact", null, null, Level.FINE);
		JSONObject queryParams = new JSONObject();
		JSONObject payload = new JSONObject();
		String result = null;
		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
		try {
			// If artifact tool Id is available, add this to query param (it's an optional parameter).
			if (devopsConfig.getSnArtifactToolId() != null && devopsConfig.getSnArtifactToolId().length() > 0)
				queryParams.put(DevOpsConstants.TOOL_ID_ATTR.toString(), devopsConfig.getSnArtifactToolId());
			// add orchestration tool id to q-params
			queryParams.put(DevOpsConstants.ORCHESTRATION_TOOL_ID_ATTR.toString(), devopsConfig.getToolId());

			JSONObject artifactsPayloadJSON = JSONObject.fromObject(artifactsPayload);
			if (artifactsPayloadJSON.containsKey(DevOpsConstants.ARTIFACT_ARTIFACTS_ATTR.toString())) {
				JSONArray artifactsJSONArray = artifactsPayloadJSON
						.getJSONArray(DevOpsConstants.ARTIFACT_ARTIFACTS_ATTR.toString());
				payload.put(DevOpsConstants.ARTIFACT_ARTIFACTS_ATTR.toString(), artifactsJSONArray); // artifacts
			}
			// replace stageName from given payload
			if (artifactsPayloadJSON.containsKey(DevOpsConstants.ARTIFACT_STAGE_NAME.toString()))
				stageName = artifactsPayloadJSON.getString(DevOpsConstants.ARTIFACT_STAGE_NAME.toString());

			// replace branchName from given payload
			if (artifactsPayloadJSON.containsKey(DevOpsConstants.ARTIFACT_BRANCH_NAME.toString()))
				branchName = artifactsPayloadJSON.getString(DevOpsConstants.ARTIFACT_BRANCH_NAME.toString());

			//replace buildNumber/taskExecNum from given payload
			if (artifactsPayloadJSON.containsKey(DevOpsConstants.ARTIFACT_TASK_EXEC_NUM.toString()))
				buildNumber = artifactsPayloadJSON.getString(DevOpsConstants.ARTIFACT_TASK_EXEC_NUM.toString());

			if (isFreeStyle)
				payload.put(DevOpsConstants.ARTIFACT_PROJECT_NAME.toString(), jobName); // projectName
			else
				payload.put(DevOpsConstants.ARTIFACT_PIPELINE_NAME.toString(), jobName); // pipelineName

			if (null != buildNumber)
				payload.put(DevOpsConstants.ARTIFACT_TASK_EXEC_NUM.toString(), buildNumber); // buildNumber

			if (null != stageName)
				payload.put(DevOpsConstants.ARTIFACT_STAGE_NAME.toString(), stageName); // stageName

			if (null != branchName)
				payload.put(DevOpsConstants.ARTIFACT_BRANCH_NAME.toString(), branchName); // branchName

			printDebug("registerArtifact", new String[]{"message"},
					new String[]{"Payload: " + payload.toString()}, Level.FINE);
			GenericUtils.printConsoleLog(listener, "Register artifact payload: " + payload.toString());

			// make a POST call
			JSONObject response = null;
			if(!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {
				Map<String, String> tokenDetails = new HashMap<String, String>();
				tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
						devopsConfig.getTokenText(devopsConfig.getSecretCredentialId()));
				response = CommUtils.callV2Support(DevOpsConstants.REST_POST_METHOD.toString(),
						devopsConfig.getArtifactRegistrationUrl(), queryParams, payload.toString(),
						devopsConfig.getUser(), devopsConfig.getPwd(), null, null,tokenDetails);
			}else {
				response = CommUtils.call(DevOpsConstants.REST_POST_METHOD.toString(),
						devopsConfig.getArtifactRegistrationUrl(), queryParams, payload.toString(),
						devopsConfig.getUser(), devopsConfig.getPwd(), null, null);
			}
			

			if (null != response) {
				// log the response for user
				GenericUtils.printConsoleLog(listener, "Register artifact on URL " + devopsConfig.getArtifactRegistrationUrl()
						+ " responded with : " + response.toString());
				// validate response
				result = GenericUtils.parseResponseResult(response,
						DevOpsConstants.ARTIFACT_REGISTER_STATUS_ATTR.toString());
			}

		} catch (Exception e) {
			printDebug("registerArtifact", new String[]{"exception"}, new String[]{e.getMessage()}, Level.SEVERE);
			GenericUtils.printConsoleLog(listener, "Register artifact request could not be sent due to the exception: " + e.getMessage());
		}

		return result;
	}

	public String handleArtifactCreatePackage(StepContext stepContext, Run<?, ?> run, TaskListener listener, String packageName, String payload, EnvVars vars) {

		String result = null;
		printDebug("handleArtifactCreatePackage", new String[]{"packageName --"}, new String[]{packageName}, Level.FINE);

		if (run != null) {
			String buildNumber = null, stageName = null, branchName = null, jobUrl = null, jobName = null;

			DevOpsRunStatusAction action = run.getAction(DevOpsRunStatusAction.class);
			if (action != null)
				stageName = DevOpsRunListener.DevOpsStageListener.getCurrentStageName(stepContext, action.getPipelineGraph());

			// TODO - this is being repeated in other method too, see if we can generalise it by an object
			if (vars != null) {
				jobName = vars.get("JOB_NAME");
				buildNumber = vars.get("BUILD_NUMBER");

				if (GenericUtils.isNotEmpty(vars.get("GIT_BRANCH")))
					branchName = vars.get("GIT_BRANCH");
				else if (GenericUtils.isNotEmpty(vars.get("BRANCH_NAME")))
					branchName = vars.get("BRANCH_NAME");
				jobUrl = vars.get("JOB_URL");
			} else {
				return result;
			}

			result = createArtifactPackage(listener, packageName, payload, jobName, jobUrl, buildNumber, stageName, branchName,
					GenericUtils.isFreeStyleProject(run));
		}

		return result;

	}

	public String createArtifactPackage(TaskListener listener, String artifactName, String artifactsPayload, String jobName, String jobUrl,
										String buildNumber, String stageName, String branchName, boolean isFreeStyle) {

		printDebug("createArtifactPackage", null, null, Level.FINE);
		JSONObject queryParams = new JSONObject();
		JSONObject payload = new JSONObject();
		List<JSONObject> artifactsList = new ArrayList<JSONObject>();
		String result = null;
		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
		try {
			// If artifact tool Id is available, add this to query param (it's an optional parameter).
			if (devopsConfig.getSnArtifactToolId() != null && devopsConfig.getSnArtifactToolId().length() > 0)
				queryParams.put(DevOpsConstants.TOOL_ID_ATTR.toString(), devopsConfig.getSnArtifactToolId());
			// add orchestration tool id to q-params
			queryParams.put(DevOpsConstants.ORCHESTRATION_TOOL_ID_ATTR.toString(), devopsConfig.getToolId());

			// Prepare Artifacts payload
			payload.put(DevOpsConstants.ARTIFACT_NAME_ATTR.toString(), artifactName); // name
			// Add pipeline/build details to artifacts, if currentBuildInfo flag is set to true
			// sample artifacts payload: artifacts: ” [ { url: “url1”, currentBuildInfo : true}, { “url”: “url2", currentBuildInfo : true } ] ”
			JSONObject artifactsPayloadJSON = JSONObject.fromObject(artifactsPayload);
			if (artifactsPayloadJSON.containsKey(DevOpsConstants.ARTIFACT_ARTIFACTS_ATTR.toString())) {
				JSONArray artifactsJSONArray = artifactsPayloadJSON
						.getJSONArray(DevOpsConstants.ARTIFACT_ARTIFACTS_ATTR.toString());
				for (Object artifactObj : artifactsJSONArray) {
					JSONObject artifactJSON = (JSONObject) artifactObj;
					if (artifactJSON.containsKey(DevOpsConstants.ARTIFACT_CURRENT_BUILD_INFO.toString())
							&& artifactJSON.getString(DevOpsConstants.ARTIFACT_CURRENT_BUILD_INFO.toString())
							.equals(DevOpsConstants.COMMON_RESPONSE_VALUE_TRUE.toString())) {
						// 1. remove the currentBuildInfo param
						artifactJSON.remove(DevOpsConstants.ARTIFACT_CURRENT_BUILD_INFO.toString());
						// 2. add build details
						artifactJSON = addBuildDetails(artifactJSON, jobName, buildNumber, stageName, branchName);
					}
					// add artifactJSON to Array
					artifactsList.add(artifactJSON);
				}
				payload.put(DevOpsConstants.ARTIFACT_ARTIFACTS_ATTR.toString(), artifactsList); // artifacts
			}
			// replace stageName from given payload
			if (artifactsPayloadJSON.containsKey(DevOpsConstants.ARTIFACT_STAGE_NAME.toString()))
				stageName = artifactsPayloadJSON.getString(DevOpsConstants.ARTIFACT_STAGE_NAME.toString());

			// replace branchName from given payload
			if (artifactsPayloadJSON.containsKey(DevOpsConstants.ARTIFACT_BRANCH_NAME.toString()))
				branchName = artifactsPayloadJSON.getString(DevOpsConstants.ARTIFACT_BRANCH_NAME.toString());

			// replace buildNumber/taskExecNum from given payload
			if (artifactsPayloadJSON.containsKey(DevOpsConstants.ARTIFACT_TASK_EXEC_NUM.toString()))
				buildNumber = artifactsPayloadJSON.getString(DevOpsConstants.ARTIFACT_TASK_EXEC_NUM.toString());

			if (isFreeStyle)
				payload.put(DevOpsConstants.ARTIFACT_PROJECT_NAME.toString(), jobName); // projectName
			else
				payload.put(DevOpsConstants.ARTIFACT_PIPELINE_NAME.toString(), jobName); // pipelineName

			if (null != buildNumber)
				payload.put(DevOpsConstants.ARTIFACT_TASK_EXEC_NUM.toString(), buildNumber); // buildNumber

			if (null != stageName)
				payload.put(DevOpsConstants.ARTIFACT_STAGE_NAME.toString(), stageName); // stageName

			if (null != branchName)
				payload.put(DevOpsConstants.ARTIFACT_BRANCH_NAME.toString(), branchName); // branchName

			printDebug("createArtifactPackage", new String[]{"message"},
					new String[]{"Payload: " + payload.toString()}, Level.FINE);
			GenericUtils.printConsoleLog(listener, "Create Artifact package payload: " + payload.toString());

			// make a POST call
			JSONObject response = null;
			if(!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {
				Map<String, String> tokenDetails = new HashMap<String, String>();
				tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
						devopsConfig.getTokenText(devopsConfig.getSecretCredentialId()));
				response = CommUtils.callV2Support(DevOpsConstants.REST_POST_METHOD.toString(),
						devopsConfig.getArtifactCreatePackageUrl(), queryParams, payload.toString(),
						devopsConfig.getUser(), devopsConfig.getPwd(), null, null,tokenDetails);
			}else {
				response = CommUtils.call(DevOpsConstants.REST_POST_METHOD.toString(),
						devopsConfig.getArtifactCreatePackageUrl(), queryParams, payload.toString(),
						devopsConfig.getUser(), devopsConfig.getPwd(), null, null);
			}
			
			//validate response and assign it to result.
			if (response != null) {
				// log the response for user
				GenericUtils.printConsoleLog(listener, "Create artifact package on URL "
						+ devopsConfig.getArtifactCreatePackageUrl() + " responded with : " + response.toString());

				result = GenericUtils.parseResponseResult(response,
						DevOpsConstants.ARTIFACT_REGISTER_STATUS_ATTR.toString());
			}

		} catch (Exception e) {
			printDebug("createArtifactPackage", new String[]{"exception"}, new String[]{e.getMessage()}, Level.SEVERE);
			GenericUtils.printConsoleLog(listener, "Create Artifact package request could not be sent due to the exception: " + e.getMessage());
		}

		return result;
	}

	public JSONObject addBuildDetails(JSONObject artifactJSON, String jobName, String buildNumber, String stageName, String branchName) {

		if (null == artifactJSON.get(DevOpsConstants.ARTIFACT_PIPELINE_NAME.toString()))
			artifactJSON.put(DevOpsConstants.ARTIFACT_PIPELINE_NAME.toString(), jobName); // pipelineName

		if (null == artifactJSON.get(DevOpsConstants.ARTIFACT_TASK_EXEC_NUM.toString()))
			artifactJSON.put(DevOpsConstants.ARTIFACT_TASK_EXEC_NUM.toString(), buildNumber); // buildNumber

		if (null == artifactJSON.get(DevOpsConstants.ARTIFACT_STAGE_NAME.toString()) && null != stageName)
			artifactJSON.put(DevOpsConstants.ARTIFACT_STAGE_NAME.toString(), stageName); // stageName

		if (null == artifactJSON.get(DevOpsConstants.ARTIFACT_BRANCH_NAME.toString()))
			artifactJSON.put(DevOpsConstants.ARTIFACT_BRANCH_NAME.toString(), branchName); // branchName

		return artifactJSON;
	}

	public JSONObject createChangeset(String applicationName, TaskListener listener) {

		JSONObject queryParams = new JSONObject();

		JSONObject filePayloadJSON = new JSONObject();

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();


		queryParams.put(DevOpsConstants.CONFIG_APPLICATION_NAME.toString(), applicationName);

		JSONObject response = CommUtils.call(DevOpsConstants.REST_POST_METHOD.toString(),
				devopsConfig.getCDMChangeSetCreationURL(), queryParams, filePayloadJSON.toString(), devopsConfig.getUser(),
				devopsConfig.getPwd(), null, null);

		return response;
	}

	public JSONObject uploadData(String applicationName, String changesetNumber, String dataFormat, String path,
								 boolean autoCommit, boolean autoDelete, boolean autoValidate, String fileContent, String target, String deployableName,
								 String collectionName, boolean autoPublish, String transactionSource) {
		JSONObject queryParams = new JSONObject();

		queryParams.put(DevOpsConstants.CONFIG_APPLICATION_NAME.toString(), applicationName);
		queryParams.put(DevOpsConstants.CONFIG_CHANGESET_NUMBER.toString(), changesetNumber);
		queryParams.put(DevOpsConstants.CONFIG_NAME_PATH.toString(), path);
		queryParams.put(DevOpsConstants.CONFIG_DATA_FORMAT.toString(), dataFormat);
		queryParams.put(DevOpsConstants.CONFIG_AUTO_COMMIT.toString(), autoCommit);
		queryParams.put(DevOpsConstants.CONFIG_AUTO_DELETE.toString(), autoDelete);
		queryParams.put(DevOpsConstants.CONFIG_AUTO_VALIDATE.toString(), autoValidate);
		queryParams.put(DevOpsConstants.CONFIG_DEPLOYABLE_NAME.toString(), deployableName);
		queryParams.put(DevOpsConstants.CONFIG_COLLECTION_NAME.toString(), collectionName);

		if(autoPublish)
			queryParams.put("publishOption", "publish_valid");
		else
			queryParams.put("publishOption", "publish_none");


		JSONObject filePayloadJSON = new JSONObject();

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		filePayloadJSON.put(DevOpsConstants.CONFIG_FILE_CONTENT.toString(), fileContent);

		JSONObject response = null;

		if (target.equalsIgnoreCase(DevOpsConstants.CONFIG_COMPONENT_TYPE.toString()))
			response = CommUtils.call(DevOpsConstants.REST_POST_METHOD.toString(),
					devopsConfig.getCDMUploadToComponentURL(), queryParams, fileContent, devopsConfig.getUser(),
					devopsConfig.getPwd(), "text/plain", transactionSource);
		else if (target.equalsIgnoreCase(DevOpsConstants.CONFIG_DEPLOYABLE_TYPE.toString()))
			response = CommUtils.call(DevOpsConstants.REST_POST_METHOD.toString(),
					devopsConfig.getCDMUploadToDeployableURL(), queryParams, fileContent, devopsConfig.getUser(),
					devopsConfig.getPwd(), "text/plain", transactionSource);
		else if (target.equalsIgnoreCase(DevOpsConstants.CONFIG_COLLECTION_TYPE.toString()))
			response = CommUtils.call(DevOpsConstants.REST_POST_METHOD.toString(),
					devopsConfig.getCDMUploadToCollectionURL(), queryParams, fileContent, devopsConfig.getUser(),
					devopsConfig.getPwd(), "text/plain", transactionSource);
		else
			return null;

		return response;
	}

	public JSONObject checkStatusForUpload(String uploadId) {

		JSONObject queryParams = new JSONObject();
		JSONObject filePayloadJSON = new JSONObject();

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		String deleteURL = devopsConfig.getUploadStatusURL() + uploadId;
		JSONObject response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
				deleteURL, queryParams, filePayloadJSON.toString(), devopsConfig.getUser(),
				devopsConfig.getPwd(), null, null);

		return response;

	}

	public JSONObject insertExportRequest(String applicationName, String deployableName, String exporterName, String exporterFormat, JSONObject exporterArgs, String snapshotName, String transactionSource) {

		JSONObject queryParams = new JSONObject();
		JSONObject filePayloadJSON = new JSONObject();

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		queryParams.put(DevOpsConstants.CONFIG_DEPLOYABLE_NAME.toString(), deployableName);
		queryParams.put(DevOpsConstants.CONFIG_EXPORTER_NAME.toString(), exporterName);
		queryParams.put(DevOpsConstants.CONFIG_APPLICATION_NAME.toString(), applicationName);
		queryParams.put(DevOpsConstants.CONFIG_EXPORTER_FORMAT.toString(), exporterFormat);
		queryParams.put(DevOpsConstants.CONFIG_EXPORTER_ARGUMENTS.toString(), exporterArgs);
		queryParams.put(DevOpsConstants.CONFIG_SNAPSHOT_NAME.toString(), snapshotName);

		JSONObject response = CommUtils.call(DevOpsConstants.REST_POST_METHOD.toString(),
				devopsConfig.getExportRequestURL(), queryParams, filePayloadJSON.toString(), devopsConfig.getUser(),
				devopsConfig.getPwd(), null, transactionSource);

		return response;
	}

	public JSONObject getImpactedDeployables(String changesetId) {
		JSONObject queryParams = new JSONObject();
		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		JSONObject filePayloadJSON = new JSONObject();


		JSONObject response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
				devopsConfig.getImpactedDeployableURL(changesetId), queryParams, filePayloadJSON.toString(), devopsConfig.getUser(),
				devopsConfig.getPwd(), null, null);

		return response;
	}

	public JSONObject fetchExportStatus(String exportId) {

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		String exportStatusURL = devopsConfig.getExportConfigStatusURL(exportId);

		JSONObject response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
				exportStatusURL, null, null, devopsConfig.getUser(),
				devopsConfig.getPwd(), null, null);

		return response;
	}

	public JSONObject fetchExportData(String exportId) {

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		String exportDataURL = devopsConfig.getExportConfigDataURL(exportId);

		JSONObject response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
				exportDataURL, null, null, devopsConfig.getUser(),
				devopsConfig.getPwd(), null, null);
		return response;
	}

	public JSONObject getSnapshotsByDeployables(String applicationName, String deployableName, String changesetNumber, boolean isValidated, String transactionSource, boolean noImapactedDeployable) {
		JSONObject queryParams = new JSONObject();
		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
		String query = "deployable_id.name=" + deployableName + "^cdm_application_id.sys_id=" + applicationName;
		String queryLimit = "";

		if (StringUtils.isEmpty(changesetNumber)) {
			if(!isValidated) {
				query = query +"^ORDERBYDESCsys_created_on";
				queryLimit = "1";
			}
			else { 
				if(!noImapactedDeployable) {
					query = query +"^validationINpassed,in_progress,passed_with_exception,requested^ORDERBYDESCsys_created_on";
					queryLimit = "2";
				}
				else {
					query = query +"^validationINpassed,passed_with_exception^ORDERBYDESCsys_created_on";
					queryLimit = "1";
				}
			}
		}
		else {
			query = query + "^changeset_id.number=" + changesetNumber;
			queryLimit = "1";
		}

		queryParams.put(DevOpsConstants.TABLE_API_QUERY.toString(), query);
		queryParams.put(DevOpsConstants.TABLE_API_FIELDS.toString(), "sys_id,name,description,validation,published,sys_created_on");
		queryParams.put(DevOpsConstants.TABLE_API_LIMIT.toString(), queryLimit);

		JSONObject response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
				devopsConfig.getSnapshotStatusURL(), queryParams, null, devopsConfig.getUser(),
				devopsConfig.getPwd(), null, transactionSource);

		return response;
	}


	public JSONObject snapShotExists(String applicationName, List<String> deployableNames, String changesetNumber) {

		JSONObject queryParams = new JSONObject();
		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
		String deployableNamesCommaSeparated = String.join(",", deployableNames);

		String query = "deployable_id.nameIN" + deployableNamesCommaSeparated + "^cdm_application_id.sys_id=" + applicationName + "^changeset_id.number=" + changesetNumber;
		queryParams.put(DevOpsConstants.TABLE_API_QUERY.toString(), query);
		queryParams.put(DevOpsConstants.TABLE_API_FIELDS.toString(), "sys_id,name,description,validation,last_validated,published,last_published,sys_created_on");

		JSONObject response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
				devopsConfig.getSnapshotStatusURL(), queryParams, null, devopsConfig.getUser(),
				devopsConfig.getPwd(), null, null);

		return response;
	}

	public JSONObject getDeployableName(String sysId) {
		JSONObject queryParams = new JSONObject();

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
		queryParams.put(DevOpsConstants.TABLE_API_QUERY.toString(),"sys_id="+sysId);
		queryParams.put(DevOpsConstants.TABLE_API_FIELDS.toString(), "deployable_id.name,cdm_deployable_id.environment_type");
		JSONObject response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
				devopsConfig.getSnapshotStatusURL(), queryParams, null, devopsConfig.getUser(),
				devopsConfig.getPwd(), null, null);
		return response;
	}

	public JSONObject querySnapShotStatus(String appSysId, List<String> deployableNames, List<String> snapshotNames, int retryCount, boolean checkForNotValidated) throws InterruptedException {
		JSONObject queryParams = new JSONObject();
		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		String validationStates = "in_progress,requested";
		String snapShotNamesCommaSeparated = String.join(",", snapshotNames);
		String deployableNamesCommaSeparated = String.join(",", deployableNames);

		String query = "cdm_application_id.sys_id="+appSysId+"^cdm_deployable_id.nameIN"+deployableNamesCommaSeparated+"^nameIN" + snapShotNamesCommaSeparated + "^validationIN" + validationStates;
		if(retryCount > 1 && checkForNotValidated) {
			queryParams.put(DevOpsConstants.TABLE_API_QUERY.toString(), query+",not_validated");
			Thread.sleep(500);
		}
		else
			queryParams.put(DevOpsConstants.TABLE_API_QUERY.toString(), query);
		queryParams.put(DevOpsConstants.TABLE_API_FIELDS.toString(), "name,validation");

		JSONObject response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
				devopsConfig.getSnapshotStatusURL(), queryParams, null, devopsConfig.getUser(),
				devopsConfig.getPwd(), null, null);

		return response;
	}

	public JSONObject fetchSnapshotRecord(String applicationName, String deployableName, String snapshotName) {
		JSONObject queryParams = new JSONObject();

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
		String query = "deployable_id.name=" + deployableName + "^application_id.name=" + applicationName;
		if (GenericUtils.isEmpty(snapshotName))
			query = query + "^ORDERBYDESCsys_created_on";
		else
			query = query + "^name=" + snapshotName;

		String fields=DevOpsConstants.CONFIG_SNAPSHOT_SYS_ID.toString()+","+DevOpsConstants.CONFIG_ENVIRONMENT_TYPE.toString();

		queryParams.put(DevOpsConstants.TABLE_API_QUERY.toString(), query);
		queryParams.put(DevOpsConstants.TABLE_API_FIELDS.toString(), fields);
		queryParams.put(DevOpsConstants.TABLE_API_LIMIT.toString(), "1");

		JSONObject response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
				devopsConfig.getSnapshotStatusURL(), queryParams, null, devopsConfig.getUser(),
				devopsConfig.getPwd(), null, null);

		return response;
	}

	public JSONObject publishSnapshot(String snapshotId, TaskListener listener, String transactionSource) {

		JSONObject queryParams = new JSONObject();
		JSONObject filePayloadJSON = new JSONObject();

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		JSONObject response = new JSONObject();
		try {
			response = CommUtils.callSafe(DevOpsConstants.REST_POST_METHOD.toString(),
					devopsConfig.getPublishSnapshotURL(snapshotId), queryParams, filePayloadJSON.toString(), devopsConfig.getUser(),
					devopsConfig.getPwd(), null, transactionSource);
		} catch (Exception e) {
			GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_PUBLISH_STEP_FUNCTION_NAME + " - Publish Failed due to Connection Issue");
			response.put("failureCause", "Failed due to Exception");
			return response;
		}
		return response;
	}

	public JSONObject registerChangeset(String pipelineName, String branchName, String toolId, String buildNumber, String type, boolean isMultiBranch, String changesetNumber, String snapshotName, String applicationName, TaskListener listener) {

		JSONObject queryParams = new JSONObject();
		JSONObject filePayloadJSON = new JSONObject();

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		queryParams.put(DevOpsConstants.ARTIFACT_PIPELINE_NAME.toString(), pipelineName);
		queryParams.put(DevOpsConstants.SCM_BRANCH_NAME.toString(), branchName);
		queryParams.put(DevOpsConstants.TOOL_ID_ATTR.toString(), toolId);
		queryParams.put(DevOpsConstants.CONFIG_BUILD_NUMBER.toString(), buildNumber);
		queryParams.put("type", type);
		queryParams.put(DevOpsConstants.IS_MULTI_BRANCH_ATTR.toString(),isMultiBranch);

		filePayloadJSON.put("changeSetId", changesetNumber);
		filePayloadJSON.put(DevOpsConstants.CONFIG_SNAPSHOT_NAME.toString(), snapshotName);
		filePayloadJSON.put(DevOpsConstants.CONFIG_APPLICATION_NAME.toString(), applicationName);

		int retryCount = 0;
		int retryFrequency = 220;
		JSONObject response = null;
		JSONObject responseBody = new JSONObject();
		String statusMessage = "";

		while (retryCount <= 20) {
			retryCount++;
			if(retryCount % 2 == 0)
				retryFrequency = retryFrequency *2;
			response = CommUtils.call(DevOpsConstants.REST_POST_METHOD.toString(),
					devopsConfig.getPipelineRegisterURL(), queryParams, filePayloadJSON.toString(), devopsConfig.getUser(),
					devopsConfig.getPwd(), null, null);

			if (response == null) {
				try {
					Thread.sleep(retryFrequency);
				} catch (InterruptedException e) {
					GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString() + " - " + e.getMessage());
				}
				continue;
			}

			JSONObject status = response.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
			statusMessage = status.getString(DevOpsConstants.COMMON_RESPONSE_STATUS.toString());

			if (!(statusMessage.equalsIgnoreCase("Could not find matching task execution for given pipeline name and build number"))) {//Todo
				if (statusMessage.equalsIgnoreCase("Success")) {
					responseBody.put(DevOpsConstants.COMMON_RESPONSE_STATUS.toString(), "Success");
					responseBody.put(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString(), "Successfully Registered");
					return responseBody;
				} else {
					responseBody.put(DevOpsConstants.COMMON_RESPONSE_STATUS.toString(), "Failure");
					responseBody.put(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString(), statusMessage);
					return responseBody;
				}
			}

			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_UPLOAD_STEP_FUNCTION_NAME.toString() + " - " + e.getMessage());
			}
		}

		if (response != null) {
			responseBody.put(DevOpsConstants.COMMON_RESPONSE_STATUS.toString(), "Failure");
			responseBody.put(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString(), statusMessage);
			return responseBody;
		}
		responseBody.put(DevOpsConstants.COMMON_RESPONSE_STATUS.toString(), "Failure");
		responseBody.put(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString(), "Failed to Register Changeset");
		return responseBody;
	}

	public JSONObject validateSnapshot(String snapshotId, TaskListener listener, String transactionSource) {

		JSONObject queryParams = new JSONObject();
		JSONObject filePayloadJSON = new JSONObject();

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		JSONObject response = new JSONObject();
		try {
			response = CommUtils.callSafe(DevOpsConstants.REST_POST_METHOD.toString(),
					devopsConfig.getValidateSnapshotURL(snapshotId), queryParams, filePayloadJSON.toString(), devopsConfig.getUser(),
					devopsConfig.getPwd(), null, transactionSource);
		} catch (Exception e) {
			GenericUtils.printConsoleLog(listener, DevOpsConstants.CONFIG_VALIDATE_STEP_FUNCTION_NAME + " - Validation of snapshot failed " + e.getMessage());
			response.put(DevOpsConstants.COMMON_RESULT_FAILURE.toString(), "Validate failed due to exception " + e.getMessage());
			return response;
		}
		return response;
	}

	public JSONObject getChangesetId(String changesetId) {
		JSONObject queryParams = new JSONObject();

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
		String query = "number=" + changesetId;

		queryParams.put(DevOpsConstants.TABLE_API_QUERY.toString(), query);
		queryParams.put(DevOpsConstants.TABLE_API_FIELDS.toString(), "sys_id,state,cdm_application.node.name");
		queryParams.put(DevOpsConstants.TABLE_API_LIMIT.toString(), "1");

		JSONObject response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
				devopsConfig.getChangesetURL(), queryParams, null, devopsConfig.getUser(),
				devopsConfig.getPwd(), null, null);

		return response;
	}

	public JSONObject checkForValidApp(String applicationName) {

		JSONObject queryParams = new JSONObject();
		JSONObject filePayloadJSON = new JSONObject();

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		String query = "node.name="+applicationName;

		queryParams.put(DevOpsConstants.TABLE_API_QUERY.toString(), query);
		queryParams.put(DevOpsConstants.TABLE_API_FIELDS.toString(), "sys_id,node.name");

		JSONObject response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
				devopsConfig.getValidAppURL(), queryParams, filePayloadJSON.toString(), devopsConfig.getUser(),
				devopsConfig.getPwd(), null, null);

		return response;
	}

	public JSONObject getValidationResults(String snapshotSysId, String policy, String format) {

		JSONObject queryParams = new JSONObject();

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
		String baseQuery = "snapshot.sys_id="+snapshotSysId+"^is_latest=true";
		String query = "";

		if(policy.isEmpty()) {
			queryParams.put(DevOpsConstants.TABLE_API_QUERY.toString(), baseQuery);
			queryParams.put(DevOpsConstants.TABLE_API_FIELDS.toString(), "snapshot.application_id.name,policy.name,snapshot.name,impacted_node.name,node_path,policy_execution.output");
		}
		else {
			if(format.equalsIgnoreCase("xml")) {
				query = baseQuery+"^policy.name="+policy;
				queryParams.put(DevOpsConstants.TABLE_API_QUERY.toString(), query);
				queryParams.put(DevOpsConstants.TABLE_API_FIELDS.toString(), "description,impacted_node.name,node_path");
			}
			else {
				query = baseQuery+"^policy.name="+policy;
				queryParams.put(DevOpsConstants.TABLE_API_QUERY.toString(), query);
				queryParams.put(DevOpsConstants.TABLE_API_FIELDS.toString(), "description,impacted_node.name,node_path,type,policy_execution.decision");
			}
		}
		JSONObject response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
				devopsConfig.getPolicyValidationURL(), queryParams, null, devopsConfig.getUser(),
				devopsConfig.getPwd(), null, null);

		return response;
	}

	public JSONObject getConfigInfo(String info) {
		JSONObject configStatus = null;
		try {
			JSONObject jsonObject = JSONObject.fromObject(info);
			if (jsonObject.containsKey("configResult")) {
				return jsonObject.getJSONObject("configResult");
			}
		} catch (Exception e) {
			printDebug("getConfigInfo", new String[]{"exception"},
					new String[]{e.getMessage()}, Level.INFO);
		}
		return configStatus;
	}

	public JSONObject registerSecurityResult(JSONObject payload) {
		JSONObject queryParams = new JSONObject();
		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
		String securityRegisterURL = devopsConfig.getSecurityResultRegistrationURL();
		queryParams.put(DevOpsConstants.TOOL_ID_ATTR.toString(), devopsConfig.getToolId());
		JSONObject response = null;
		if(!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {
			Map<String, String> tokenDetails = new HashMap<String, String>();
			tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
					devopsConfig.getTokenText(devopsConfig.getSecretCredentialId()));
			response = CommUtils.callV2Support(DevOpsConstants.REST_POST_METHOD.toString(),
					securityRegisterURL, queryParams, payload.toString(), devopsConfig.getUser(),
					devopsConfig.getPwd(), null, null, tokenDetails);
		}else {
			response = CommUtils.call(DevOpsConstants.REST_POST_METHOD.toString(),
					securityRegisterURL, queryParams, payload.toString(), devopsConfig.getUser(),
					devopsConfig.getPwd(), null, null);
		}
		

		return response;
	}

	public JSONObject fetchDeployables(String appSysId) {
		JSONObject queryParams = new JSONObject();
		JSONObject filePayloadJSON = new JSONObject();

		DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

		String query = "cdm_app="+appSysId;

		queryParams.put(DevOpsConstants.TABLE_API_QUERY.toString(), query);
		queryParams.put(DevOpsConstants.TABLE_API_FIELDS.toString(), "node.name");

		JSONObject response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
				devopsConfig.getDeployablesURL(), queryParams, filePayloadJSON.toString(), devopsConfig.getUser(),
				devopsConfig.getPwd(), null, null);

		return response;
	}
}
