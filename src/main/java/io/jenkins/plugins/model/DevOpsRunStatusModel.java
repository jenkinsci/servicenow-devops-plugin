package io.jenkins.plugins.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class DevOpsRunStatusModel {

	private DevOpsRunStatusSCMModel scmModel;
	private DevOpsRunStatusStageModel stageModel;
	private DevOpsRunStatusTestModel testModel;
	private DevOpsRunStatusJobModel jobModel;
	private List<DevOpsTestSummary> testSummaries;
	private List<String> log;
	private String url;
	private int number;
	private String phase;
	private String result;
	private String startDateTime;
	private String endDateTime;
	private String triggerType;
	private String upstreamTaskExecutionURL;
	private String pronoun;
	private String isMultiBranch;
	private long timestamp;

	public DevOpsRunStatusModel() {
		this.url = "";
		this.number = 0;
		this.phase = "";
		this.result = "";
		this.startDateTime = "";
		this.endDateTime = "";
		this.triggerType = "";
		this.upstreamTaskExecutionURL = "";
		this.pronoun = "";
		this.timestamp = 0;
		this.isMultiBranch = "";
		this.log = new ArrayList<String>();
	}

	public String isMultiBranch() {
		return isMultiBranch;
	}

	public void setMultiBranch(String multiBranch) {
		isMultiBranch = multiBranch;
	}

	public DevOpsRunStatusSCMModel getSCMModel() {
		return scmModel;
	}

	public void setSCMModel(DevOpsRunStatusSCMModel scmModel) {
		this.scmModel = scmModel;
	}

	public DevOpsRunStatusStageModel getStageModel() {
		return stageModel;
	}

	public void setStageModel(DevOpsRunStatusStageModel stageModel) {
		this.stageModel = stageModel;
	}

	public DevOpsRunStatusTestModel getTestModel() {
		return testModel;
	}

	public void setTestModel(DevOpsRunStatusTestModel testModel) {
		this.testModel = testModel;
	}

	public DevOpsRunStatusJobModel getJobModel() {
		return jobModel;
	}

	public void setJobModel(DevOpsRunStatusJobModel jobModel) {
		this.jobModel = jobModel;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(int number) {
		this.number = number;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public String getPhase() {
		return phase;
	}

	public void setPhase(String phase) {
		this.phase = phase;
	}

	public String getStartDateTime() {
		return startDateTime;
	}

	public void setStartDateTime(String startDateTime) {
		this.startDateTime = startDateTime;
	}

	public String getEndDateTime() {
		return endDateTime;
	}

	public void setEndDateTime(String endDateTime) {
		this.endDateTime = endDateTime;
	}

	public String getTriggerType() {
		return triggerType;
	}

	public void setTriggerType(String triggerType) {
		this.triggerType = triggerType;
	}

	public String getUpstreamTaskExecutionUrl() {
		return upstreamTaskExecutionURL;
	}

	public void setUpstreamTaskExecutionURL(String upstreamTaskExecutionURL) {
		this.upstreamTaskExecutionURL = upstreamTaskExecutionURL;
	}

	public String getPronoun() {
		return pronoun;
	}

	public void setPronoun(String pronoun) {
		this.pronoun = pronoun;
	}

	public List<String> getLog() {
		return log;
	}

	public void setLog(List<String> log) {
		this.log = log;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	@JsonIgnore
	public List<DevOpsTestSummary> getTestSummaries() {
		return testSummaries;
	}

	public void setTestSummaries(List<DevOpsTestSummary> testSummaries) {
		this.testSummaries = testSummaries;
	}

	public void addTotTestSummaries(DevOpsTestSummary testSummary) {
		if (this.testSummaries == null)
			this.testSummaries = new ArrayList<DevOpsTestSummary>();

		this.testSummaries.add(testSummary);
	}
}


/* 
taskExecution - JOB STARTED:
{
  "number": 40,
  "URL": "https://jenkins.mycompnay.com:8080/job/Mobile-Platform-CI/40/",
  "startDateTime": "1970-01-01T08:15:30-05:00",
  "endDateTime": "1970-01-01T08:25:30-05:00",
  "triggerType": "upstream",
  "result": "",
  "phase": "STARTED",
  "upstreamTaskExecutionURL": "https://jenkins.mycompnay.com:8080/job/Mobile-Platform-Build/40/",
  "orchestrationTaskDetails": {
    "name": "Mobile-Platform-CI",
    "URL": "https://jenkins.mycompnay.com:8080/job/Mobile-Platform-CI/"
  },
  "reports": {
    "testReports": {
      "passedTests": 70,
      "failedTests": 20,
      "skippedTests": 10,
      "totalTests": 100
    }
  },
  "stage" : {
      "name": "",
      "id": "",
      "phase": "",
      "duration": 0,
      "result": ""
  },
  "scm": {
      "URL": "https://github.com/DevOpsManiac/CorpSite",
      "branch": "origin/master",
      "commit": "76b8e84af3498a0bef4a920365092ef3bf5253be",
      "changes": [
        "README.md",
        "setup/system_setup.xml",
        "setup/devops_setup.xml"
      ],
      "culprits": [
        "kit.corry",
        "noreply"
      ]
   },
   "artifacts": {
      "target/globex-web.war": {
        "archive": "http://jenkins-uat.sndevops.xyz/job/CorpSite%20PROD%20deploy/174/artifact/target/globex-web.war"
      }
    }
}


taskExecution required
{
  "number": 40,
  "url": "https://jenkins.mycompnay.com:8080/job/Mobile-Platform-CI/40/",
  "startDateTime": "1970-01-01T08:15:30-05:00",
  "endDateTime": "1970-01-01T08:25:30-05:00",
  "triggerType": "upstream",
  "result": "Success",
  "orchestrationTaskDetails": {
    "name": "Mobile-Platform-CI",
    "url": "https://jenkins.mycompnay.com:8080/job/Mobile-Platform-CI/"
  }
}

taskExecution full
{
  "number": 40,
  "url": "https://jenkins.mycompnay.com:8080/job/Mobile-Platform-CI/40/",
  "startDateTime": "1970-01-01T08:15:30-05:00",
  "endDateTime": "1970-01-01T08:25:30-05:00",
  "triggerType": "upstream",
  "result": "Success",
  "upstream": {
    "url": "https://jenkins.mycompnay.com:8080/job/Mobile-Platform-Build/40/"
  },
  "orchestrationTaskDetails": {
    "name": "Mobile-Platform-CI",
    "url": "https://jenkins.mycompnay.com:8080/job/Mobile-Platform-CI/"
  },
  "changes": [
    "3fa85f64-5717-4562-b3fc-2c963f66afa6"
  ],
  "reports": {
    "testReports": {
      "passedTests": 70,
      "failedTests": 20,
      "skippedTests": 10,
      "totalTests": 100
    }
  }
}
*/

/* 
Event samples (notification plugin)

1 Prod-Deploy-STARTED
{
  "name": "CorpSite PROD deploy",
  "display_name": "CorpSite PROD deploy",
  "url": "job/CorpSite%20PROD%20deploy/",
  "build": {
    "full_url": "http://jenkins-uat.sndevops.xyz/job/CorpSite%20PROD%20deploy/174/",
    "number": 174,
    "queue_id": 3883,
    "timestamp": 1562107157549,
    "phase": "STARTED",
    "url": "job/CorpSite%20PROD%20deploy/174/",
    "scm": {
      "url": "https://github.com/DevOpsManiac/CorpSite",
      "branch": "origin/master",
      "commit": "084aa15a58a95a1f9c2c952dcad24ed650a1685b",
      "changes": [
        
      ],
      "culprits": [
        "noreply"
      ]
    },
    "log": "",
    "notes": "",
    "artifacts": {
      
    }
  },
  "sn_tool_id": "0ef2281fdbdab300811177421f9619df"
}

2 Prod-Deploy-COMPLETED

{
  "name": "CorpSite PROD deploy",
  "display_name": "CorpSite PROD deploy",
  "url": "job/CorpSite%20PROD%20deploy/",
  "build": {
    "full_url": "http://jenkins-uat.sndevops.xyz/job/CorpSite%20PROD%20deploy/174/",
    "number": 174,
    "queue_id": 3883,
    "timestamp": 1562107177879,
    "phase": "COMPLETED",
    "status": "SUCCESS",
    "url": "job/CorpSite%20PROD%20deploy/174/",
    "scm": {
      "url": "https://github.com/DevOpsManiac/CorpSite",
      "branch": "origin/master",
      "commit": "76b8e84af3498a0bef4a920365092ef3bf5253be",
      "changes": [
        "README.md",
        "setup/system_setup.xml",
        "setup/devops_setup.xml"
      ],
      "culprits": [
        "kit.corry",
        "noreply"
      ]
    },
    "log": "",
    "notes": "",
    "test_summary": {
      "total": 2,
      "failed": 0,
      "passed": 2,
      "skipped": 0,
      "failed_tests": [
        
      ]
    },
    "artifacts": {
      "target/globex-web.war": {
        "archive": "http://jenkins-uat.sndevops.xyz/job/CorpSite%20PROD%20deploy/174/artifact/target/globex-web.war"
      }
    }
  },
  "sn_tool_id": "0ef2281fdbdab300811177421f9619df"
}

3 UAT-Test-STARTED:

{
  "name": "CorpSite UAT test",
  "display_name": "CorpSite UAT test",
  "url": "job/CorpSite%20UAT%20test/",
  "build": {
    "full_url": "http://jenkins-uat.sndevops.xyz/job/CorpSite%20UAT%20test/183/",
    "number": 183,
    "queue_id": 3882,
    "timestamp": 1562107046661,
    "phase": "STARTED",
    "url": "job/CorpSite%20UAT%20test/183/",
    "scm": {
      "url": "https://github.com/DevOpsManiac/CorpSite",
      "branch": "origin/master",
      "commit": "76b8e84af3498a0bef4a920365092ef3bf5253be",
      "changes": [
        
      ],
      "culprits": [
        
      ]
    },
    "log": "",
    "notes": "",
    "artifacts": {
      
    }
  },
  "sn_tool_id": "0ef2281fdbdab300811177421f9619df"
}

4 UAT-Test-COMPLETED:

{
  "name": "CorpSite UAT test",
  "display_name": "CorpSite UAT test",
  "url": "job/CorpSite%20UAT%20test/",
  "build": {
    "full_url": "http://jenkins-uat.sndevops.xyz/job/CorpSite%20UAT%20test/183/",
    "number": 183,
    "queue_id": 3882,
    "timestamp": 1562107070165,
    "phase": "COMPLETED",
    "status": "SUCCESS",
    "url": "job/CorpSite%20UAT%20test/183/",
    "scm": {
      "url": "https://github.com/DevOpsManiac/CorpSite",
      "branch": "origin/master",
      "commit": "76b8e84af3498a0bef4a920365092ef3bf5253be",
      "changes": [
        
      ],
      "culprits": [
        
      ]
    },
    "log": "",
    "notes": "",
    "test_summary": {
      "total": 2,
      "failed": 0,
      "passed": 2,
      "skipped": 0,
      "failed_tests": [
        
      ]
    },
    "artifacts": {
      "target/globex-web.war": {
        "archive": "http://jenkins-uat.sndevops.xyz/job/CorpSite%20UAT%20test/183/artifact/target/globex-web.war"
      }
    }
  },
  "sn_tool_id": "0ef2281fdbdab300811177421f9619df"
}

5 UAT-Deploy-STARTED:

{
  "name": "CorpSite UAT deploy",
  "display_name": "CorpSite UAT deploy",
  "url": "job/CorpSite%20UAT%20deploy/",
  "build": {
    "full_url": "http://jenkins-uat.sndevops.xyz/job/CorpSite%20UAT%20deploy/234/",
    "number": 234,
    "queue_id": 3881,
    "timestamp": 1562107020747,
    "phase": "STARTED",
    "url": "job/CorpSite%20UAT%20deploy/234/",
    "scm": {
      "url": "https://github.com/DevOpsManiac/CorpSite",
      "branch": "origin/master",
      "commit": "76b8e84af3498a0bef4a920365092ef3bf5253be",
      "changes": [
        
      ],
      "culprits": [
        
      ]
    },
    "log": "",
    "notes": "",
    "artifacts": {
      
    }
  },
  "sn_tool_id": "0ef2281fdbdab300811177421f9619df"
}

6 UAT-Deploy-COMPLETED:

{
  "name": "CorpSite UAT deploy",
  "display_name": "CorpSite UAT deploy",
  "url": "job/CorpSite%20UAT%20deploy/",
  "build": {
    "full_url": "http://jenkins-uat.sndevops.xyz/job/CorpSite%20UAT%20deploy/234/",
    "number": 234,
    "queue_id": 3881,
    "timestamp": 1562107039929,
    "phase": "COMPLETED",
    "status": "SUCCESS",
    "url": "job/CorpSite%20UAT%20deploy/234/",
    "scm": {
      "url": "https://github.com/DevOpsManiac/CorpSite",
      "branch": "origin/master",
      "commit": "76b8e84af3498a0bef4a920365092ef3bf5253be",
      "changes": [
        
      ],
      "culprits": [
        
      ]
    },
    "log": "",
    "notes": "",
    "test_summary": {
      "total": 2,
      "failed": 0,
      "passed": 2,
      "skipped": 0,
      "failed_tests": [
        
      ]
    },
    "artifacts": {
      "target/globex-web.war": {
        "archive": "http://jenkins-uat.sndevops.xyz/job/CorpSite%20UAT%20deploy/234/artifact/target/globex-web.war"
      }
    }
  },
  "sn_tool_id": "0ef2281fdbdab300811177421f9619df"
}

7 CI-STARTED:

{
  "name": "CorpSite CI",
  "display_name": "CorpSite CI",
  "url": "job/CorpSite%20CI/",
  "build": {
    "full_url": "http://jenkins-uat.sndevops.xyz/job/CorpSite%20CI/317/",
    "number": 317,
    "queue_id": 3880,
    "timestamp": 1562106987020,
    "phase": "STARTED",
    "url": "job/CorpSite%20CI/317/",
    "scm": {
      "url": "https://github.com/DevOpsManiac/CorpSite",
      "branch": "origin/master",
      "commit": "76b8e84af3498a0bef4a920365092ef3bf5253be",
      "changes": [
        
      ],
      "culprits": [
        
      ]
    },
    "log": "",
    "notes": "",
    "artifacts": {
      
    }
  },
  "sn_tool_id": "0ef2281fdbdab300811177421f9619df"
}

8 CI-COMPLETED:

{
  "name": "CorpSite CI",
  "display_name": "CorpSite CI",
  "url": "job/CorpSite%20CI/",
  "build": {
    "full_url": "http://jenkins-uat.sndevops.xyz/job/CorpSite%20CI/317/",
    "number": 317,
    "queue_id": 3880,
    "timestamp": 1562107009956,
    "phase": "COMPLETED",
    "status": "SUCCESS",
    "url": "job/CorpSite%20CI/317/",
    "scm": {
      "url": "https://github.com/DevOpsManiac/CorpSite",
      "branch": "origin/master",
      "commit": "76b8e84af3498a0bef4a920365092ef3bf5253be",
      "changes": [
        
      ],
      "culprits": [
        
      ]
    },
    "log": "",
    "notes": "",
    "test_summary": {
      "total": 2,
      "failed": 0,
      "passed": 2,
      "skipped": 0,
      "failed_tests": [
        
      ]
    },
    "artifacts": {
      
    }
  },
  "sn_tool_id": "0ef2281fdbdab300811177421f9619df"
}
*/