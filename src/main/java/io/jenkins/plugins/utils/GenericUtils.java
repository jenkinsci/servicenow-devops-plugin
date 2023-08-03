package io.jenkins.plugins.utils;

import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.EnvVars;
import hudson.logging.LogRecorder;
import hudson.logging.LogRecorderManager;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.config.DevOpsConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public final class GenericUtils {
	private GenericUtils() {
	}

	public static final Pattern urlPatt = Pattern.compile(
			"^(https?):\\/\\/[-a-zA-Z0-9+&@#\\/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#\\/%=~_|]");

	public static boolean checkUrlValid(String url) {
		Matcher m = urlPatt.matcher(url);
		return m.matches();
	}

	public static boolean checkParameters(JSONObject formData) {
		return formData.containsKey("approval") && formData.containsKey("url") &&
		       formData.containsKey("user") && formData.containsKey("pwd");
	}

	public static String parseResponseResult(JSONObject jsonObject, String attr) {
		if (jsonObject != null) {
			try {
				if (jsonObject.containsKey(DevOpsConstants.COMMON_RESPONSE_RESULT.toString())) {
					JSONObject result = jsonObject.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
					if (result.containsKey(attr) && result.containsKey(DevOpsConstants.COMMON_RESPONSE_STATUS.toString())) {
						if (result.getString(DevOpsConstants.COMMON_RESPONSE_STATUS.toString())
								.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_SUCCESS.toString()))
							return result.getString(attr);
					}
					else if (result.containsKey(DevOpsConstants.COMMON_RESPONSE_DETAILS.toString()) && result.containsKey(DevOpsConstants.COMMON_RESPONSE_STATUS.toString())) {
						if (result.getString(DevOpsConstants.COMMON_RESPONSE_STATUS.toString())
								.equalsIgnoreCase(DevOpsConstants.COMMON_RESULT_ERROR.toString())) {
							JSONObject details = result.getJSONObject(DevOpsConstants.COMMON_RESPONSE_DETAILS.toString());
							if (details.containsKey(DevOpsConstants.COMMON_RESPONSE_ERRORS.toString())) {
								JSONArray errors = details.getJSONArray(DevOpsConstants.COMMON_RESPONSE_ERRORS.toString());
								JSONObject errorObj = new JSONObject();
								JSONArray errorMsgArray = new JSONArray();
								for (int i=0; i<errors.size(); i++) {
									JSONObject errorMessageObj = errors.getJSONObject(i);
									if (errorMessageObj.containsKey(DevOpsConstants.MESSAGE_ATTR.toString())) 
										errorMsgArray.add(errorMessageObj.getString(DevOpsConstants.MESSAGE_ATTR.toString()));
								}
								errorObj.put(DevOpsConstants.COMMON_RESULT_FAILURE.toString(), errorMsgArray.join(",", true));
								return errorObj.toString();
							}
						}
					}
				} 
				else if (jsonObject.containsKey(DevOpsConstants.COMMON_RESULT_FAILURE.toString()))
					return jsonObject.toString();
				else if (jsonObject.containsKey(DevOpsConstants.COMMON_RESULT_ERROR.toString())) {
					JSONObject errorObj = jsonObject.getJSONObject(DevOpsConstants.COMMON_RESULT_ERROR.toString());
					if (errorObj.containsKey(DevOpsConstants.MESSAGE_ATTR.toString())) {
						String message = errorObj.getString(DevOpsConstants.MESSAGE_ATTR.toString());
						errorObj = new JSONObject();
						errorObj.put(DevOpsConstants.COMMON_RESULT_FAILURE.toString(), message);
						return errorObj.toString();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public static Boolean checkIfAttributeExist(JSONObject jsonObject, String attr) {
		Boolean valid = false;
		if (jsonObject != null) {
			try {
				if (jsonObject.containsKey(DevOpsConstants.COMMON_RESPONSE_RESULT.toString())) {
					JSONObject result = jsonObject.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
					if (result.containsKey(attr)) {
						Object valueObj = result.get(attr);
						if (valueObj != null)
							valid = true;
					}
				} 
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return valid;
	}

	private static Logger getLogger() {
		return Logger.getLogger(getLoggerName());
	}

	private static String getLoggerName() {
		return DevOpsConstants.LOGGER_NAME.toString();
	}

	private static void createLogRecorder(String name, Level logLevel) {
		Jenkins jenkins = Jenkins.getInstanceOrNull();
		if (jenkins != null) {
			LogRecorderManager logRecorderManager = jenkins.getLog();

			if (logRecorderManager.getLogRecorder(getLoggerName()) == null) {
				logRecorderManager.doNewLogRecorder(getLoggerName());
			}
			LogRecorder logRecorder = logRecorderManager.getLogRecorder(getLoggerName());
			LogRecorder.Target logRecorderTarget = null;

			for (LogRecorder.Target target : logRecorder.targets) {
				if (target.name == getLoggerName()) {
					logRecorderTarget = target;
				}
			}

			if (logRecorderTarget == null){
				logRecorderTarget = new LogRecorder.Target(getLoggerName(), logLevel);
				logRecorder.targets.add(logRecorderTarget);
			} else {
				if (!logRecorderTarget.getLevel().equals(logLevel)) {
					logRecorder.targets.remove(logRecorderTarget);
					logRecorderTarget = new LogRecorder.Target(getLoggerName(), logLevel);
					logRecorder.targets.add(logRecorderTarget);
				}
			}
	}
	}


	public static void configureLogger(String logLevel) {
		Level lv;
		Level logRecorderLogLevel;
		if (logLevel.equals("inherit")) {
			lv = null;
			logRecorderLogLevel = Logger.getLogger("").getLevel();
		} else {
			lv = Level.parse(logLevel.toUpperCase(Locale.ENGLISH));
			logRecorderLogLevel = lv;
		}
		getLogger().setLevel(lv);
		createLogRecorder(getLoggerName(), logRecorderLogLevel);
	}

	public static void printDebug(String className, String methodName, String[] variables,
	                              String[] values, Level logLevel) {
		if (variables != null && values != null) {
			if (variables.length == values.length) {
				for (int i = 0; i < variables.length; i++) {
					String message =
							className + "." + methodName + "(), " + variables[i] +
									": " + values[i];
					getLogger().log(logLevel, message);
				}
			}
		} else {
			String message = className + "." + methodName + "()";
			getLogger().log(logLevel, message);
		}
	}


	public static boolean isDevOpsConfigurationEnabled() {
		DevOpsConfiguration devopsConfig = DevOpsConfiguration.get();

		if (devopsConfig.isSnDevopsEnabled()) {
			return true;
		} else
			Logger.getLogger(GenericUtils.class.getName())
					.info("!!!  SnDevops is Disabled is for all jobs.");

		return false;
	}

	public static boolean isDevOpsConfigurationValid() {
		DevOpsConfiguration devopsConfig = DevOpsConfiguration.get();
		if (devopsConfig.isSnDevopsEnabled()) {
			StringBuilder errMsg = new StringBuilder("!!! Invalid SnDevops " +
					"Configuration - ");
			boolean result = true;

			if (isEmpty(devopsConfig.getInstanceUrl())) {
				result = false;
				errMsg.append(" InstanceURL not provided |");
			}

			if (isEmpty(devopsConfig.getToolId())) {
				result = false;
				errMsg.append(" ToolId not provided |");
			}

			if (isEmpty(devopsConfig.getApiVersion())) {
				result = false;
				errMsg.append(" API version not provided |");
			}

			if (DevOpsConstants.VERSION_V1.toString().equals(devopsConfig.getApiVersion()) && isEmpty(devopsConfig.getUser())) {
				result = false;
				errMsg.append(" User not provided  |");
			}

			if (DevOpsConstants.VERSION_V1.toString().equals(devopsConfig.getApiVersion()) && isEmpty(devopsConfig.getPwd())) {
				result = false;
				errMsg.append(" Password not provided |");
			}
			
			if (DevOpsConstants.VERSION_V2.toString().equals(devopsConfig.getApiVersion()) 
					&& isEmpty(devopsConfig.getTokenText(devopsConfig.getSecretCredentialId()))) {
				result = false;
				errMsg.append(" Secret Token not provided |");
			}

			if (!result)
				Logger.getLogger(GenericUtils.class.getName())
						.info(errMsg.toString());

			return result;
		}
		return false;
	}


	public static Boolean isMultiBranch(Job<?, ?> job) {
		String job_pronoun = job.getPronoun();

		if (job_pronoun == null ||
		    ! (job_pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString())
				    || job_pronoun.equalsIgnoreCase(DevOpsConstants.PIPELINE_PRONOUN.toString())
		            || job_pronoun.equalsIgnoreCase(DevOpsConstants.BITBUCKET_MULTI_BRANCH_PIPELINE_PRONOUN
				    .toString())))
			return false;

		if (job.getParent() != null &&
		    DevOpsConstants.MULTI_BRANCH_PROJECT_CLASS.toString()
				    .equalsIgnoreCase(job.getParent().getClass().getName()))
			return true;

		return false;
	}


	public static DevOpsConfiguration getDevOpsConfiguration() {
		final DevOpsConfiguration devopsConfig = DevOpsConfiguration.get();
		if (devopsConfig == null) {
			throw new IllegalStateException("DevopsConfiguration is not available");
		}
		return devopsConfig;
	}
	
	public static boolean isFreeStyleProject(Run<?, ?> run) {
		return (run.getParent() instanceof FreeStyleProject);
	}

	public static EnvVars getEnvVars(Run<?, ?> run, TaskListener listener) {
		EnvVars vars = null;
		if (run != null && listener != null) {
			try {
				vars = run.getEnvironment(listener);
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
		return vars;
	}
	
	public static void printConsoleLog(TaskListener listener, String message) {
		if (null != listener) {
			String snPrefix = "[ServiceNow DevOps] ";
			listener.getLogger().println(snPrefix + message);
		}
	}


	public static boolean isEmpty(final CharSequence cs) {
		return cs == null || cs.length() == 0;
	}
	
	public static boolean isEmptyOrDefault(final CharSequence cs) {
		return cs == null || cs.length() == 0 || DevOpsConstants.SN_DEFUALT.toString().equals(cs);
	}

	public static boolean isNotEmpty(final CharSequence cs) {
		return !isEmpty(cs);
	}
	public static String getPropertyValueFromJsonTree (Object jsonInput, String[] str) {
		if (jsonInput == null) return null;
		JSONObject json = JSONObject.fromObject(jsonInput);
		Object jsonObj = null;
		for(String key : str){
			if(key != null && json!= null && json.containsKey(key)){
				jsonObj = json.get(key);
			} else return null;

			if(jsonObj != null && jsonObj instanceof JSONObject)
				json = JSONObject.fromObject(jsonObj);
			else json = null;
		}
		if(jsonObj == null) return null;
		return jsonObj.toString();
	}
}