package io.jenkins.plugins.utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.util.VersionNumber;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Functions;
import hudson.logging.LogRecorder;
import hudson.logging.LogRecorderManager;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.config.DevOpsConfigurationEntry;
import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.model.DevOpsPipelineInfoConfig;
import io.jenkins.plugins.pipeline.steps.DevOpsStep;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import static io.jenkins.plugins.utils.DevOpsConstants.COMMON_RESPONSE_VALUE_UNKNOWN;


public final class GenericUtils {
	private static final Logger LOGGER = Logger.getLogger(GenericUtils.class.getName());

	private GenericUtils() {
	}

	public static final Pattern urlPatt = Pattern.compile(
			"^(https:\\/\\/[-a-zA-Z0-9+&@#\\/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#\\/%=~_|]|http:\\/\\/localhost:\\d{2,5}\\/?)$");

	public static boolean checkUrlValid(String url) {
		Matcher m = urlPatt.matcher(url);
		return m.matches();
	}

	public static String removeTrailingSlashes(String url) {
		if (isEmpty(url))
			return url;
		return url.replaceAll("/+$", "");
	}

	public static boolean isWindows() {
		if (Functions.isWindows()) {
			return true;
		}
		return false;
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
					} else if (result.containsKey(DevOpsConstants.COMMON_RESPONSE_DETAILS.toString()) && result.containsKey(DevOpsConstants.COMMON_RESPONSE_STATUS.toString())) {
						if (result.getString(DevOpsConstants.COMMON_RESPONSE_STATUS.toString())
								.equalsIgnoreCase(DevOpsConstants.COMMON_RESULT_ERROR.toString())) {
							JSONObject details = result.getJSONObject(DevOpsConstants.COMMON_RESPONSE_DETAILS.toString());
							if (details.containsKey(DevOpsConstants.COMMON_RESPONSE_ERRORS.toString())) {
								JSONArray errors = details.getJSONArray(DevOpsConstants.COMMON_RESPONSE_ERRORS.toString());
								JSONObject errorObj = new JSONObject();
								JSONArray errorMsgArray = new JSONArray();
								for (int i = 0; i < errors.size(); i++) {
									JSONObject errorMessageObj = errors.getJSONObject(i);
									if (errorMessageObj.containsKey(DevOpsConstants.MESSAGE_ATTR.toString()))
										errorMsgArray.add(errorMessageObj.getString(DevOpsConstants.MESSAGE_ATTR.toString()));
								}
								errorObj.put(DevOpsConstants.COMMON_RESULT_FAILURE.toString(), errorMsgArray.join(",", true));
								return errorObj.toString();
							}
						}
					}
				} else if (jsonObject.containsKey(DevOpsConstants.COMMON_RESULT_FAILURE.toString()))
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

	public static String getConfigEntryTrackKey(String instanceUrl, String toolId) {
		if (isNotEmpty(instanceUrl) && isNotEmpty(toolId))
			return toolId + "_" + GenericUtils.removeTrailingSlashes(instanceUrl);
		return null;
	}

	public static String getAutoGeneratedConfigName(String instanceUrl) throws URISyntaxException {
		if (!StringUtils.isBlank(instanceUrl)) {
			String domain = new URI(instanceUrl).getHost();
			String instanceName = domain.startsWith("www.") ? domain.substring(4) : domain;
			return String.format("DevOps-%s-%s", instanceName, new Date().getTime());
		}
		return String.format("DevOps-%s", new Date().getTime());
	}

	public static JSONObject getTrackInfoForConfigKey(JSONObject jsonObject, String configKey) {
		if (jsonObject != null && GenericUtils.isNotEmpty(configKey)) {
			try {
				if (jsonObject.containsKey(configKey)) {
					JSONObject configResult = jsonObject.getJSONObject(configKey);
					if (configResult.containsKey(DevOpsConstants.COMMON_RESPONSE_RESULT.toString())) {
						JSONObject result = configResult.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
						if (result.containsKey("track")) {
							return configResult;
						}
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
		try {
			Jenkins jenkins = Jenkins.getInstanceOrNull();
			if (jenkins != null) {
				LogRecorderManager logRecorderManager = jenkins.getLog();
				if (logRecorderManager.getLogRecorder(getLoggerName()) == null) {
					logRecorderManager.doNewLogRecorder(getLoggerName());
				}
				LogRecorder logRecorder = logRecorderManager.getLogRecorder(getLoggerName());

				Optional<Method> getLoggersMethod = Arrays.stream(logRecorder.getClass().getDeclaredMethods())
						.filter(method -> method.getName().equals("getLoggers"))
						.findFirst();

				if (getLoggersMethod.isPresent()) {
					// The getLoggers method exists, using latest code to add logger to logrecoder
					List<LogRecorder.Target> loggers = (List<LogRecorder.Target>) getLoggersMethod.get().invoke(logRecorder);
					boolean loggerFound = false;

					Iterator<LogRecorder.Target> targetIterator = loggers.iterator();

					while (targetIterator.hasNext()) {
						LogRecorder.Target target = targetIterator.next();
						// Remove all loggers except one (which matches name and loglevel)
						if (!loggerFound && getLoggerName().equalsIgnoreCase(target.getName()) && target.getLevel().equals(logLevel)) {
							loggerFound = true;
						} else {
							targetIterator.remove();
						}
					}
					if (!loggerFound) {
						loggers.add(new LogRecorder.Target(getLoggerName(), logLevel));
					}
					logRecorder.save();
					LOGGER.severe("using getLoggers added LogRecords logger with logLevel: " + logLevel);

				} else {
					// The getLoggers method does not exist, using logRecorder.targets
					boolean loggerFound = false;
					for (LogRecorder.Target target : logRecorder.targets) {
						// Remove all loggers except one (which matches name and loglevel)
						if (!loggerFound && getLoggerName().equalsIgnoreCase(target.getName()) && target.getLevel().equals(logLevel)) {
							loggerFound = true;
						} else {
							logRecorder.targets.remove(target);
						}
					}
					if (!loggerFound) {
						logRecorder.targets.add(new LogRecorder.Target(getLoggerName(), logLevel));
					}
					logRecorder.save();
					LOGGER.severe("using logRecorder.targets added LogRecords logger with logLevel: " + logLevel);
				}
			}
		} catch (IOException | IllegalAccessException | InvocationTargetException e) {
			LOGGER.severe("Error occuired during ServiceNowDevops logRecorder creation");
			e.printStackTrace();
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

	public static void printDebug(String className, String methodName, String logMessage, Level logLevel) {
		if (logMessage != null) {
			String message = className + "." + methodName + "(), " + logMessage;
			getLogger().log(logLevel, message);
		} else {
			String message = className + "." + methodName + "()";
			getLogger().log(logLevel, message);
		}
	}

	public static String getStackTraceAsString(Throwable throwable) {
		if (throwable == null) {
			return "Throwable is null";
		}
		StringWriter sw = new StringWriter();
		try (PrintWriter pw = new PrintWriter(sw)) {
			throwable.printStackTrace(pw);
		}
		return sw.toString();
	}

	public static boolean isDevOpsConfigurationEnabled() {
		List<DevOpsConfigurationEntry> devopsConfigEntries = DevOpsConfiguration.get().getEntries();
		for (DevOpsConfigurationEntry configEntry : devopsConfigEntries) {
			if (configEntry.getActive())
				return true;
		}
		Logger.getLogger(GenericUtils.class.getName()).info("!!!  There are no active DevOps configurations available");
		return false;
	}

	public static Boolean isMultiBranch(Job<?, ?> job) {
		String job_pronoun = job.getPronoun();

		if (job_pronoun == null ||
				!(job_pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString())
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

	public static DevOpsConfigurationEntry getDevOpsConfigurationEntry() {
		List<DevOpsConfigurationEntry> devopsConfigEntries = DevOpsConfiguration.get().getEntries();
		for (DevOpsConfigurationEntry configEntry : devopsConfigEntries) {
			if (configEntry.getActive())
				return configEntry;
		}
		throw new IllegalStateException("DevopsConfiguration is not available");
	}

	public static DevOpsConfigurationEntry getDevOpsConfigurationEntryOrDefault(String configurationName) {
		boolean hasConfigurationName = isEmpty(configurationName) ? false : true;
		List<DevOpsConfigurationEntry> devopsConfigEntries = DevOpsConfiguration.get().getEntries();
		for (DevOpsConfigurationEntry configEntry : devopsConfigEntries) {
			if (configEntry.getActive() && (hasConfigurationName && configEntry.getName().equals(configurationName) || (!hasConfigurationName && configEntry.getDefaultConnection())))
				return configEntry;
		}
		return null;
	}

	public static DevOpsConfigurationEntry getDevOpsConfigurationEntryByInstanceUrlAndToolId(String instanceUrl, String toolId) {
		List<DevOpsConfigurationEntry> devopsConfigEntries = DevOpsConfiguration.get().getEntries();
		for (DevOpsConfigurationEntry configEntry : devopsConfigEntries) {
			if (configEntry.getActive() && configEntry.getInstanceUrl().equals(instanceUrl) && configEntry.getToolId().equals(toolId))
				return configEntry;
		}
		return null;
	}

	public static DevOpsPipelineInfoConfig getPipelineInfoConfigFromConfigEntry(DevOpsModel.DevOpsPipelineInfo pipelineInfo, DevOpsConfigurationEntry entry) {
		if (pipelineInfo == null || entry == null)
			return null;
		List<DevOpsPipelineInfoConfig> pipelineInfoConfigs = pipelineInfo.getDevopsPipelineConfigs();
		String configKey = getConfigEntryTrackKey(entry.getInstanceUrl(), entry.getToolId());
		for (DevOpsPipelineInfoConfig config : pipelineInfoConfigs) {
			if (config.getConfigKey().equals(configKey))
				return config;
		}
		return null;
	}

	public static List<DevOpsConfigurationEntry> getActiveDevOpsConfigurationEntry() {
		List<DevOpsConfigurationEntry> devopsConfigEntries = DevOpsConfiguration.get().getEntries();
		List<DevOpsConfigurationEntry> activeDevopsConfigEntries = new ArrayList<>();
		for (DevOpsConfigurationEntry configEntry : devopsConfigEntries) {
			if (configEntry.getActive() && isNotEmpty(configEntry.getInstanceUrl()) && isNotEmpty(configEntry.getToolId()))
				activeDevopsConfigEntries.add(configEntry);
		}
		return activeDevopsConfigEntries;
	}

	public static boolean handleConfigurationNotFound(DevOpsStep step, DevOpsJobProperty jobProperty, TaskListener listener, StepContext stepContext, boolean isAsyncStep, boolean isIgnoreErrors) throws AbortException {
		boolean hasConfigurationName = isEmpty(step.getConfigurationName()) ? false : true;
		String message = hasConfigurationName ? "[ServiceNow DevOps] Could not find an active configuration with name " + step.getConfigurationName() : "[ServiceNow DevOps] Could not find an active default configuration";
		if (isIgnoreErrors || (jobProperty != null && jobProperty.isIgnoreSNErrors())) {
			message = message.concat(", but will ignore");
			listener.getLogger().println(message);
			if (isAsyncStep)
				stepContext.onSuccess(message);
		} else {
			if (isAsyncStep)
				stepContext.onFailure(new AbortException(message));
			else
				throw new AbortException(message);
		}
		return true;
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

	public static String getPropertyValueFromJsonTree(Object jsonInput, String[] str) {
		if (jsonInput == null) return null;
		JSONObject json = JSONObject.fromObject(jsonInput);
		Object jsonObj = null;
		for (String key : str) {
			if (key != null && json != null && json.containsKey(key)) {
				jsonObj = json.get(key);
			} else return null;

			if (jsonObj != null && jsonObj instanceof JSONObject)
				json = JSONObject.fromObject(jsonObj);
			else json = null;
		}
		if (jsonObj == null) return null;
		return jsonObj.toString();
	}


	public static String getRequestInfo(StaplerRequest request, String content) {
		StringBuilder sb = new StringBuilder();
		if (request != null) {
			sb.append("Request Info:\n");
			sb.append("Path: ").append(request.getOriginalRestOfPath()).append("\n");
			sb.append("Parameters: ").append(request.getParameterMap()).append("\n");
			if (content != null) {
				sb.append("Content: ").append(content);
			}
		}
		return sb.toString();
	}

	public static String getResponseInfo(StaplerResponse response) {
		StringBuilder sb = new StringBuilder();
		if (response != null) {
			sb.append("\nResponse Info:\n");
			sb.append("Status Code: ").append(response.getStatus()).append("\n");
			for (String headerName : response.getHeaderNames()) {
				String headerValue = response.getHeader(headerName);
				sb.append(headerName).append(": ").append(headerValue).append("\n");
			}
			// Add more information as needed
		}
		return sb.toString();
	}

	public static String getJenkinsVersion() {
		Jenkins jenkins = Jenkins.getInstanceOrNull();
		if(jenkins != null) {
			VersionNumber versionNumber = jenkins.getVersion();
			return versionNumber != null ? versionNumber.toString() : COMMON_RESPONSE_VALUE_UNKNOWN.toString();
		}
		return COMMON_RESPONSE_VALUE_UNKNOWN.toString();
	}
}