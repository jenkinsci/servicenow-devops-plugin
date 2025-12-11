package io.jenkins.plugins.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hudson.EnvVars;
import hudson.model.Run;
import io.jenkins.plugins.DevOpsRunStatusAction;
import io.jenkins.plugins.config.DevOpsConfigurationEntry;
import io.jenkins.plugins.utils.CommUtils;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONObject;


public class DevOpsNotificationModel {

	private final Gson gson;
	private static final Logger LOGGER =
			Logger.getLogger(DevOpsNotificationModel.class.getName());

	/**
	 * Constructor for DevOpsNotificationModel.
	 * 
	 * STRY60920723: We exclude the stageNodeId field from all serialized objects 
	 * (security results, sonar results, and test results models) in notification events.
	 * This exclusion was introduced while developing the pipeline-details API to ensure 
	 * consistency and prevent unnecessary data exposure in notification payloads. 
	 * The stageNodeId is only needed internally for pipeline graph construction and 
	 * is not required in external notifications.
	 */
	public DevOpsNotificationModel() {
		// Configure Gson to exclude stageNodeId field from all serialized objects
		this.gson = new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
			.setPrettyPrinting()
			// STRY60920723: Add exclusion strategy to ignore stageNodeId in all models
			// for security/sonar/test results in notification events
			.setExclusionStrategies(new ExclusionStrategy() {
				@Override
				public boolean shouldSkipField(FieldAttributes f) {
					// Skip the stageNodeId field in any class - used for internal pipeline graph construction only
					return "stageNodeId".equals(f.getName());
				}

				@Override
				public boolean shouldSkipClass(Class<?> clazz) {
					// Don't skip any classes
					return false;
				}
			})
			.create();
	}

	public void send(DevOpsRunStatusModel model, DevOpsConfigurationEntry devopsConfig) {

		if (model != null) {

			printDebug("send", null, null, Level.FINE);

			JSONObject params = new JSONObject();
			params.put(DevOpsConstants.TOOL_TYPE_ATTR.toString(),
					DevOpsConstants.TOOL_TYPE.toString());

			if (devopsConfig != null)
				sendNotification(devopsConfig, devopsConfig.getNotificationUrl(), gson.toJson(model), params);

		} else {
			LOGGER.log(Level.INFO,
					"DevOpsRunStatusModel is null or empty");
		}
	}


	public void sendTestResults(DevOpsTestSummary testModel, DevOpsConfigurationEntry devopsConfig) {
		if (testModel != null) {
			printDebug("sendTestResults", null, null, Level.FINE);
			if (devopsConfig != null)
				sendNotification(devopsConfig, devopsConfig.getTestUrl(), gson.toJson(testModel), new JSONObject());
		} else {
			LOGGER.log(Level.INFO,
					"DevOpsRunStatusTestModel is null or empty");
		}
	}

	public void sendNotificationToConfigurations(DevOpsRunStatusAction action, DevOpsModel.DevOpsPipelineInfo pipelineInfo, boolean isStageStart, Run<?, ?> run, EnvVars vars) {
		if (action != null && pipelineInfo != null) {
			List<DevOpsPipelineInfoConfig> pipelineInfoConfigs = pipelineInfo.getDevopsPipelineConfigs();
			for (DevOpsPipelineInfoConfig pipelineInfoConfig : pipelineInfoConfigs) {
				if (pipelineInfoConfig.isTrack()) {
					// Inject test type mappings on existing notification payload
					int testsAdded = action.addTestSummariesForTestTypeMappings(pipelineInfoConfig.getTestInfo(), isStageStart, run, vars);
					// Send notifications
					send(action.getModel(), pipelineInfoConfig.getDevopsConfig());
					if (action.getModel().getTestSummaries() != null && action.getModel().getTestSummaries().size() > 0) {
						for (DevOpsTestSummary devOpsTestSummary : action.getModel().getTestSummaries()) {
							sendTestResults(devOpsTestSummary, pipelineInfoConfig.getDevopsConfig());
						}
					}
					// Reset to common model
					action.removeTestSummariesForTestTypeMappings(testsAdded);
				}
			}
		}
	}

	private void sendNotification(DevOpsConfigurationEntry devopsConfig, String notificationUrl, String data, JSONObject params) {

		if (devopsConfig != null && notificationUrl != null) {
			printDebug("sendNotification", new String[] { "configurationName" }, new String[] { devopsConfig.getName() }, Level.FINE);

			String toolId = devopsConfig.getToolId();
			params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), toolId);
			String user = DevOpsConfigurationEntry.getUser(devopsConfig.getCredentialsId());
			String pwd = DevOpsConfigurationEntry.getPwd(devopsConfig.getCredentialsId());

			if (!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {
				Map<String, String> tokenDetails = new HashMap<String, String>();
				tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
						DevOpsConfigurationEntry.getTokenText(devopsConfig.getSecretCredentialId()));
				CommUtils.callV2Support("POST", notificationUrl, params, data, user, pwd, null, null, tokenDetails);
			} else {
				CommUtils.call("POST", notificationUrl, params, data, user, pwd, null, null);
			}

		}

	}


	private void printDebug(String methodName, String[] variables, String[] values, Level logLevel) {
		GenericUtils.printDebug(DevOpsNotificationModel.class.getName(), methodName, variables, values, logLevel);
	}
}