package io.jenkins.plugins.model;

import java.util.ArrayList;
import java.util.List;

public class DevOpsJFrogBuildModel {

	private String artifactoryUrl;
	private List<DevOpsJFrogModel> builds = new ArrayList<>();

	public DevOpsJFrogBuildModel(String artifactoryUrl, DevOpsJFrogModel devOpsJFrogModel) {
		this.artifactoryUrl = artifactoryUrl;
		builds.add(devOpsJFrogModel);
	}

	public void addBuild(DevOpsJFrogModel devOpsJFrogModel) {
		builds.add(devOpsJFrogModel);
	}

	public String getArtifactoryUrl() {
		return artifactoryUrl;
	}

	public void setArtifactoryUrl(String artifactoryUrl) {
		this.artifactoryUrl = artifactoryUrl;
	}

	public List<DevOpsJFrogModel> getBuilds() {
		return builds;
	}

	public void setBuilds(List<DevOpsJFrogModel> builds) {
		this.builds = builds;
	}
}
