package io.jenkins.plugins.model;

import java.util.List;

public class DevOpsPullRequestModel {
	private List<String> pullRequestRepoUrls;
	private String pullRequestNumber;

	public DevOpsPullRequestModel(List<String> pullRequestRepoUrls, String pullRequestNumber) {
		this.pullRequestRepoUrls = pullRequestRepoUrls;
		this.pullRequestNumber = pullRequestNumber;
	}

	public List<String> getPullRequestRepoUrls() {
		return pullRequestRepoUrls;
	}

	public void setPullRequestRepoUrls(List<String> pullRequestRepoUrls) {
		this.pullRequestRepoUrls = pullRequestRepoUrls;
	}

	public String getPullRequestNumber() {
		return pullRequestNumber;
	}

	public void setPullRequestNumber(String pullRequestNumber) {
		this.pullRequestNumber = pullRequestNumber;
	}
}
