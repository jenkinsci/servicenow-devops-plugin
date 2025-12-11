package io.jenkins.plugins.model;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.TimeZone;

public class DevOpsTestSummary {
	private String name;
	private int passedTests;
	private int failedTests;
	private int skippedTests;
	private int blockedTests;
	private int totalTests;
	private String startTime;
	private String finishTime;
	private float duration;
	private int buildNumber;
	private String stageName;
	private String pipelineName;
	private String projectName;
	private int passingPercent;
	private String url;
	private transient boolean resultsFound = true;
	private String fileContent;
	private String branch;
	private String isMultiBranch;
	private String stageNodeId;



	public static class Builder {
		private String name;
		private int total;
		private int failed;
		private int passed;
		private int blocked;
		private float duration;
		private int skipped;
		private int fixed;
		private String pipelineName;
		private String projectName;
		private String stageName;
		private int buildNumber;
		private String reportUrl;
		private long startTime;
		private long finishTime;
		private String fileContent;
		private String branchName;
		private String multiBranch;
		private String stageNodeId;


		public Builder(String name) {
			this.name = name;
		}

		public Builder total(int total) {
			this.total = total;
			return this;
		}

		public Builder failed(int failed) {
			this.failed = failed;
			return this;
		}

		public Builder passed(int passed) {
			this.passed = passed;
			return this;
		}

		public Builder duration(float duration) {
			this.duration = duration;
			return this;
		}

		public Builder blocked(int blocked) {
			this.blocked = blocked;
			return this;
		}

		public Builder skipped(int skipped) {
			this.skipped = skipped;
			return this;
		}

		public Builder fixed(int fixed) {
			this.fixed = fixed;
			return this;
		}

		public Builder inStage(String stageName) {
			this.stageName = stageName;
			return this;
		}

		public Builder buildNumber(int buildNo) {
			this.buildNumber = buildNo;
			return this;
		}

		public Builder inPipeline(String pipelineName) {
			this.pipelineName = pipelineName;
			return this;
		}

		public Builder inProject(String projName) {
			this.projectName = projName;
			return this;
		}

		public Builder reportUrl(String url) {
			this.reportUrl = url;
			return this;
		}

		public Builder start(long start) {
			this.startTime = start;
			return this;
		}


		public Builder finish(long finish) {
			this.finishTime = finish;
			return this;
		}

		public Builder fileContent(String fileContent) {
			this.fileContent = fileContent;
			return this;
		}

		public Builder branchName(String bname) {
			this.branchName = bname;
			return this;
		}

		public Builder multiBranch(String multi) {
			this.multiBranch = multi;
			return this;
		}
		
		public Builder stageNodeId(String stageNodeId) {
			this.stageNodeId = stageNodeId;
			return this;
		}

		public DevOpsTestSummary build() {

			DevOpsTestSummary testSummary = new DevOpsTestSummary();

			testSummary.duration = this.duration;

			testSummary.totalTests = this.total;
			testSummary.passedTests = this.passed;
			testSummary.failedTests = this.failed;
			testSummary.blockedTests = this.blocked;
			testSummary.skippedTests = this.skipped;

			testSummary.stageName = this.stageName;
			testSummary.pipelineName = this.pipelineName;
			testSummary.projectName = this.projectName;
			testSummary.buildNumber = this.buildNumber;

			testSummary.url = this.reportUrl;

			DateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
			utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			testSummary.startTime = utcFormat.format(this.startTime);
			testSummary.finishTime = utcFormat.format(this.finishTime);

			testSummary.fileContent = this.fileContent;

			//set passing percent
			int total = this.total - this.skipped;
			if (total > 0)
				testSummary.passingPercent = ((this.passed + this.fixed) / total) * 100;

			//set name
			testSummary.name = this.name + " - " + this.buildNumber;

			testSummary.isMultiBranch = this.multiBranch;
			testSummary.branch = this.branchName;
			testSummary.stageNodeId = this.stageNodeId;

			return testSummary;
		}

	}

	private DevOpsTestSummary() {
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getPassedTests() {
		return passedTests;
	}

	public void setPassedTests(int passedTests) {
		this.passedTests = passedTests;
	}

	public int getFailedTests() {
		return failedTests;
	}

	public void setFailedTests(int failedTests) {
		this.failedTests = failedTests;
	}

	public int getSkippedTests() {
		return skippedTests;
	}

	public void setSkippedTests(int skippedTests) {
		this.skippedTests = skippedTests;
	}

	public int getBlockedTests() {
		return blockedTests;
	}

	public void setBlockedTests(int blockedTests) {
		this.blockedTests = blockedTests;
	}

	public int getTotalTests() {
		return totalTests;
	}

	public void setTotalTests(int totalTests) {
		this.totalTests = totalTests;
	}

	public String getStartTime() {
		return startTime;
	}

	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}

	public String getFinishTime() {
		return finishTime;
	}

	public void setFinishTime(String finishTime) {
		this.finishTime = finishTime;
	}

	public float getDuration() {
		return duration;
	}

	public void setDuration(float duration) {
		this.duration = duration;
	}

	public int getBuildNumber() {
		return buildNumber;
	}

	public void setBuildNumber(int buildNumber) {
		this.buildNumber = buildNumber;
	}

	public String getStageName() {
		return stageName;
	}

	public void setStageName(String stageName) {
		this.stageName = stageName;
	}

	public String getPipelineName() {
		return pipelineName;
	}

	public void setPipelineName(String pipelineName) {
		this.pipelineName = pipelineName;
	}

	public boolean isResultsFound() {
		return resultsFound;
	}

	public void setResultsFound(boolean resultsFound) {
		this.resultsFound = resultsFound;
	}

	public int getPassingPercent() {
		return passingPercent;
	}

	public void setPassingPercent(int passingPercent) {
		this.passingPercent = passingPercent;
	}

	public String getUrl() {
		return url;
	}

	private String getUrlForEquals(){
		return (url==null)? "null": url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getFileContent() {
		return fileContent;
	}

	public void setFileContent(String fileContent) {
		this.fileContent = fileContent;
	}

	private String geFileContentForEquals(){
		return (fileContent==null)? "null": fileContent;
	}

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public String getBranch() {
		return branch;
	}

	public void setBranch(String branch) {
		this.branch = branch;
	}

	public String isMultiBranch() {
		return isMultiBranch;
	}

	public void setMultiBranch(String multiBranch) {
		isMultiBranch = multiBranch;
	}
	
	public String getStageNodeId() {
		return stageNodeId;
	}
	
	public void setStageNodeId(String stageNodeId) {
		this.stageNodeId = stageNodeId;
	}

	@Override public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		DevOpsTestSummary that = (DevOpsTestSummary) o;
		return buildNumber == that.buildNumber &&
			Objects.equals(pipelineName, that.pipelineName) &&
			Objects.equals(getUrlForEquals(), that.getUrlForEquals()) &&
			Objects.equals(geFileContentForEquals(), that.geFileContentForEquals()) &&
			Objects.equals(branch, that.branch);
		// stageName and stageNodeId removed from comparison to avoid duplicates in test results
	}

	@Override public int hashCode() {
		return Objects.hash(buildNumber, pipelineName, getUrlForEquals(), geFileContentForEquals(), branch);
		// stageName and stageNodeId removed from hashCode for consistency with equals method
	}
}
