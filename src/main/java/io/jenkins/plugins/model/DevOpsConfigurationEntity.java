package io.jenkins.plugins.model;

public class DevOpsConfigurationEntity {

	private String instanceUrl;
	private String toolId;
	private String token;
	private String tokenId;
	private String name;
	private boolean defaultConnection;

	private boolean skipValidation;

	public DevOpsConfigurationEntity() {}

	public String getInstanceUrl() {
		return instanceUrl;
	}

	public void setInstanceUrl(String instanceUrl) {
		this.instanceUrl = instanceUrl;
	}

	public String getToolId() {
		return toolId;
	}

	public void setToolId(String toolId) {
		this.toolId = toolId;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getTokenId() {
		return tokenId;
	}

	public void setTokenId(String tokenId) {
		this.tokenId = tokenId;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public boolean isDefaultConnection() {
		return defaultConnection;
	}

	public void setDefaultConnection(boolean defaultConnection) {
		this.defaultConnection = defaultConnection;
	}

	public boolean isSkipValidation() {
		return skipValidation;
	}
	public void setSkipValidation(boolean skipValidation) {
		this.skipValidation = skipValidation;
	}
}