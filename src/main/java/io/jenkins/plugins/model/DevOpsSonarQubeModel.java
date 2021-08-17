package io.jenkins.plugins.model;

import java.util.Objects;

public class DevOpsSonarQubeModel {
	private String scanID;
	private String url;


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
	}

	@Override public int hashCode() {
		return Objects.hash(url,scanID);
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
	@Override
	public String toString() {
		return ("scanID: "+this.scanID + "   url: " + this.url+"  ");
	}
}
