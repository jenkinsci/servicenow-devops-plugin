package io.jenkins.plugins.model;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.utils.CommUtils;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONObject;


public class DevOpsNotificationModel {

	private final Gson gson;
	private static final Logger LOGGER =
			Logger.getLogger(DevOpsNotificationModel.class.getName());

	public DevOpsNotificationModel() {
		this.gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.IDENTITY).setPrettyPrinting().create();
	}

	public void send(DevOpsRunStatusModel model) {

		if (model != null) {//&&

			DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
			printDebug("send", null, null, Level.FINE);

			JSONObject params = new JSONObject();
			params.put(DevOpsConstants.TOOL_TYPE_ATTR.toString(),
					DevOpsConstants.TOOL_TYPE.toString());

			sendNotification(devopsConfig.getNotificationUrl(), gson.toJson(model), params);

		} else {
			LOGGER.log(Level.INFO,
					"DevOpsRunStatusModel is null or empty");
		}
	}


	public void sendTestResults(DevOpsTestSummary testModel) {

		if (testModel != null) {//&&
			DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
			printDebug("sendTestResults", null, null, Level.FINE);

			sendNotification(devopsConfig.getTestUrl(), gson.toJson(testModel), new JSONObject());
		} else {
			LOGGER.log(Level.INFO,
					"DevOpsRunStatusTestModel is null or empty");
		}
	}

	private void sendNotification(String notificationUrl, String data, JSONObject params) {

		if (GenericUtils.isDevOpsConfigurationEnabled() && GenericUtils.isDevOpsConfigurationValid()) {//&&
				DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

				printDebug("sendNotification", null, null, Level.FINE);

				String toolId = devopsConfig.getToolId();
				params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), toolId);
				String user = devopsConfig.getUser();
				String pwd = devopsConfig.getPwd();
				
				if (!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {
					Map<String, String> tokenDetails = new HashMap<String, String>();
					tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
							devopsConfig.getTokenText(devopsConfig.getSecretCredentialId()));
					CommUtils.callV2Support("POST", notificationUrl, params, data, user, pwd, null, null, tokenDetails);
				} else {
					CommUtils.call("POST", notificationUrl, params, data, user, pwd, null, null);
				}
				
				

		} else {
			LOGGER.log(Level.INFO,
					"ServiceNow Devops is disabled for all jobs or global configuration" +
					" is invalid");
		}

	}


	private void printDebug(String methodName, String[] variables, String[] values, Level logLevel) {
		GenericUtils.printDebug(DevOpsNotificationModel.class.getName(), methodName, variables, values, logLevel);
	}

}