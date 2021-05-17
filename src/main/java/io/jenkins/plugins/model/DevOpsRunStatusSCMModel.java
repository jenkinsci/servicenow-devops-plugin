package io.jenkins.plugins.model;

import java.util.List;

public class DevOpsRunStatusSCMModel {

    private String url;
    private String branch;
    private String commit;
    private List<String> changes;
    private List<String> culprits;
    
    public DevOpsRunStatusSCMModel() {
        this.url = "";
        this.branch = "";
        this.commit= "";
    }

    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public String getBranch() {
        return branch;
    }
    public void setBranch(String branch) {
        this.branch = branch;
    }
    public String getCommit() {
        return commit;
    }
    public void setCommit(String commit) {
        this.commit = commit;
    }
    public List<String> getChanges() {
        return changes;
    }
    public void setChanges(List<String> changes) {
        this.changes = changes;
    }
    public List<String> getCulprits() {
        return culprits;
    }
    public void setCulprits(List<String> culprits) {
        this.culprits = culprits;
    }
}