package io.jenkins.plugins.model;

import java.util.Objects;

public class DevOpsSonarQubeModel {
	private String scanID;
	private String url;
	private String stageNodeId;


	public DevOpsSonarQubeModel() {
	}

	@Override public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		DevOpsSonarQubeModel that = (DevOpsSonarQubeModel) o;
		return scanID.equalsIgnoreCase(that.scanID) &&
				url.equalsIgnoreCase(that.url);
		// stageNodeId removed from comparison to avoid duplicates in results
	}

	@Override public int hashCode() {
		return Objects.hash(url, scanID);
		// stageNodeId removed from hashCode for consistency with equals method
	}

	public String getScanID() {
		return scanID;
	}

	public void setScanID(String scanID) {
		this.scanID = scanID;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}
	
	public String getStageNodeId() {
		return stageNodeId;
	}

	public void setStageNodeId(String stageNodeId) {
		this.stageNodeId = stageNodeId;
	}
	@Override
	public String toString() {
		return ("scanID: " + this.scanID + "   url: " + this.url + "   stageNodeId: " + this.stageNodeId + "  ");
	}
}
