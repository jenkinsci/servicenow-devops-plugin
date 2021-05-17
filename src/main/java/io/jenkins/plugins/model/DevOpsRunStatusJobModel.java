package io.jenkins.plugins.model;

public class DevOpsRunStatusJobModel {
    private String name;
    private String url;

    public DevOpsRunStatusJobModel() {
        this.name = "";
        this.url = "";
    }
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}