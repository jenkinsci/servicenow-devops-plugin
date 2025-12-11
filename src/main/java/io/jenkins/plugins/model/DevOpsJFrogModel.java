package io.jenkins.plugins.model;

import java.util.Objects;

import com.google.gson.annotations.SerializedName;

public class DevOpsJFrogModel {
	@SerializedName(value = "build.name")
	private String buildName;

	@SerializedName(value = "build.number")
	private String buildNumber;

	@SerializedName(value = "build.started")
	private String startedTimeStamp;

	private transient String artifactoryUrl;
	
	// Used to associate this model with a specific pipeline stage
	private String stageNodeId;

	public DevOpsJFrogModel(String buildName, String buildNumber, String startedTimeStamp, String artifactoryUrl) {
		this(buildName, buildNumber, startedTimeStamp, artifactoryUrl, null);
	}
	
	public DevOpsJFrogModel(String buildName, String buildNumber, String startedTimeStamp, String artifactoryUrl, String stageNodeId) {
		this.buildName = buildName;
		this.buildNumber = buildNumber;
		this.startedTimeStamp = startedTimeStamp;
		this.artifactoryUrl = artifactoryUrl;
		this.stageNodeId = stageNodeId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		DevOpsJFrogModel that = (DevOpsJFrogModel) o;
		// stageNodeId removed from comparison to avoid duplicates in JFrog results
		return buildName.equalsIgnoreCase(that.buildName) &&
				buildNumber.equalsIgnoreCase(that.buildNumber) &&
				(startedTimeStamp == null || (startedTimeStamp.equals(that.startedTimeStamp))) &&
				Objects.equals(artifactoryUrl, that.artifactoryUrl);
	}

	@Override
	public int hashCode() {
		// stageNodeId removed from hashCode for consistency with equals method
		return Objects.hash(buildName, buildNumber, startedTimeStamp, artifactoryUrl);
	}

	public String getBuildName() {
		return buildName;
	}

	public void setBuildName(String buildName) {
		this.buildName = buildName;
	}

	public String getBuildNumber() {
		return buildNumber;
	}

	public void setBuildNumber(String buildNumber) {
		this.buildNumber = buildNumber;
	}

	public String getStartedTimeStamp() {
		return startedTimeStamp;
	}

	public void setStartedTimeStamp(String startedTimeStamp) {
		this.startedTimeStamp = startedTimeStamp;
	}

	public String getArtifactoryUrl() {
		return artifactoryUrl;
	}

	public void setArtifactoryUrl(String artifactoryUrl) {
		this.artifactoryUrl = artifactoryUrl;
	}
	
	public String getStageNodeId() {
		return stageNodeId;
	}
	
	public void setStageNodeId(String stageNodeId) {
		this.stageNodeId = stageNodeId;
	}
}
