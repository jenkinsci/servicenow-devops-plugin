package io.jenkins.plugins.model;

import java.util.ArrayList;
import java.util.List;

public class DevOpsRunStatusStageModel {
    private String name;
    private String id;
    private String phase;
    private long duration;
    private String url; 
    private String result;
    private String upstreamTaskExecutionURL;
    private String upstreamStageName;
    private String parentExecutionUrl;
    private String pipelineExecutionUrl;
    private List<String> waitForChildExecutions;
    private List<String> log;
    private long timestamp;
    private String parentStageName;
    private String stageStatusFromTag;
/*
API (wfapi) for individual stage execution example (build #6, stageId #6)
http://localhost:8090/jenkins/job/felipe-pipeline/6/execution/node/6/wfapi/describe
 */

    public DevOpsRunStatusStageModel() {
        this.name = "";
        this.id = "";
        this.phase = "";
        this.url = "";
        this.result = "";
        this.upstreamTaskExecutionURL = "";
        this.upstreamStageName = "";
        this.parentExecutionUrl = "";
        this.waitForChildExecutions = new ArrayList<String>();
        this.pipelineExecutionUrl = "";
        this.duration = 0;
        this.timestamp = 0;
        this.parentStageName="";
        this.log = new ArrayList<String>();
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getPhase() {
        return phase;
    }
    public void setPhase(String phase) {
        this.phase = phase;
    }
    public long getDuration() {
        return duration;
    }
    public void setDuration(long duration) {
        this.duration = duration;
    }
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public String getResult() {
        return result;
    }
    public void setResult(String result) {
        this.result = result;
    }
    public String getUpstreamTaskExecutionUrl() {
        return upstreamTaskExecutionURL;
    }
    public void setUpstreamTaskExecutionURL(String upstreamTaskExecutionURL) {
        this.upstreamTaskExecutionURL = upstreamTaskExecutionURL;
    }
    public String getUpstreamStageName() {
        return upstreamStageName;
    }
    public void setUpstreamStageName(String upstreamStageName) {
        this.upstreamStageName = upstreamStageName;
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

    public String getParentStageName() {
        return parentStageName;
    }

    public void setParentStageName(String parentStageName) {
        this.parentStageName = parentStageName;
    }
    
    public String getStageStatusFromTag() {
        return stageStatusFromTag;
    }

    public void setStageStatusFromTag(String stageStatusFromTag) {
        this.stageStatusFromTag = stageStatusFromTag;
    }

    public String getParentExecutionUrl() {
        return parentExecutionUrl;
    }

    public void setParentExecutionUrl(String parentExecutionUrl) {
        this.parentExecutionUrl = parentExecutionUrl;
    }

    public String getPipelineExecutionUrl() {
        return pipelineExecutionUrl;
    }

    public void setPipelineExecutionUrl(String pipelineExecutionUrl) {
        this.pipelineExecutionUrl = pipelineExecutionUrl;
    }

    public List<String> getWaitForChildExecutions() {
        return waitForChildExecutions;
    }

    public void setWaitForChildExecutions(List<String> waitForChildExecutions) {
        this.waitForChildExecutions = waitForChildExecutions;
    }
}