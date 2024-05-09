package io.jenkins.plugins.model;

import io.jenkins.plugins.config.DevOpsConfigurationEntry;
import net.sf.json.JSONObject;

public class DevOpsPipelineInfoConfig {

	private String configKey;
	private boolean track;
	private JSONObject testInfo;
	private boolean unreachable;
	private String errorMessage;
	private DevOpsConfigurationEntry devopsConfig;


	public DevOpsPipelineInfoConfig(boolean track, DevOpsConfigurationEntry devopsConfig, String configKey) {
		this.track = track;
		this.devopsConfig = devopsConfig;
		this.configKey = configKey;
	}

	public DevOpsPipelineInfoConfig(boolean track, DevOpsConfigurationEntry devopsConfig, String configKey, JSONObject testInfo) {
		this.track = track;
		this.devopsConfig = devopsConfig;
		this.configKey = configKey;
		this.testInfo = testInfo;
	}

	public DevOpsPipelineInfoConfig(boolean track, DevOpsConfigurationEntry devopsConfig, String configKey, boolean unreachable, String errorMessage) {
		this.track = track;
		this.devopsConfig = devopsConfig;
		this.unreachable = unreachable;
		this.errorMessage = errorMessage;
		this.configKey = configKey;
	}

	public boolean isUnreachable() {
		return unreachable;
	}

	public void setUnreachable(boolean unreachable) {
		this.unreachable = unreachable;
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

	public void setConfigKey(String configKey) {
		this.configKey = configKey;
	}

	public String getConfigKey() {
		return configKey;
	}

	public DevOpsConfigurationEntry getDevopsConfig() {
		return devopsConfig;
	}

	public void setDevopsConfigs(DevOpsConfigurationEntry devopsConfig) {
		this.devopsConfig = devopsConfig;
	}
}
