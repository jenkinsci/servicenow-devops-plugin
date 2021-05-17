package io.jenkins.plugins.utils;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.EnvVars;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.config.DevOpsConfiguration;
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

	public static void printDebug(String className, String methodName, String[] variables,
	                              String[] values, boolean debug) {
		if (debug) {
			if (variables != null && values != null) {
				if (variables.length == values.length) {
					for (int i = 0; i < variables.length; i++) {
						String message =
								className + "." + methodName + "(), " + variables[i] +
								": " + values[i];
						Logger.getLogger(GenericUtils.class.getName()).info(message);
					}
				}
			} else {
				String message = className + "." + methodName + "()";
				Logger.getLogger(GenericUtils.class.getName()).info(message);
			}
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

			if (isEmpty(devopsConfig.getUser())) {
				result = false;
				errMsg.append(" User not provided  |");
			}

			if (isEmpty(devopsConfig.getPwd())) {
				result = false;
				errMsg.append(" Password not provided |");
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
		    ! (job_pronoun.equalsIgnoreCase(DevOpsConstants.PIPELINE_PRONOUN.toString())
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

	public static boolean isNotEmpty(final CharSequence cs) {
		return !isEmpty(cs);
	}
}