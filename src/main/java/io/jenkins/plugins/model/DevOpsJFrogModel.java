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

	public DevOpsJFrogModel(String buildName, String buildNumber, String startedTimeStamp, String artifactoryUrl) {
		this.buildName = buildName;
		this.buildNumber = buildNumber;
		this.startedTimeStamp = startedTimeStamp;
		this.artifactoryUrl = artifactoryUrl;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		DevOpsJFrogModel that = (DevOpsJFrogModel) o;
		return buildName.equalsIgnoreCase(that.buildName) &&
				buildNumber.equalsIgnoreCase(that.buildNumber) &&
				(startedTimeStamp == null || (startedTimeStamp.equals(that.startedTimeStamp))) &&
				artifactoryUrl.equals(that.artifactoryUrl);
	}

	@Override
	public int hashCode() {
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
}
