package io.jenkins.plugins.model;

import java.util.List;

public class DevOpsRunStatusTestModel {
    private int total;
    private int failed;
    private int passed;
    private int skipped;
    private int regression;
    private int fixed;
    private float duration;
    private String name;
    private List<DevOpsRunStatusTestSuiteModel> suites;

    public DevOpsRunStatusTestModel() {
        this.total = 0;
        this.failed = 0;
        this.passed = 0;
        this.skipped = 0;
        this.regression =0;
        this.fixed = 0;
    }

    public int getTotal() {
        return total;
    }
    public void setTotal(int total) {
        this.total = total;
    }
    public int getFailed() {
        return failed;
    }
    public void setFailed(int failed) {
        this.failed = failed;
    }
    public int getPassed() {
        return passed;
    }
    public void setPassed(int passed) {
        this.passed = passed;
    }
    public int getFixed() {
        return fixed;
    }
    public void setFixed(int fixed) {
        this.fixed = fixed;
    }
    public int getRegression() {
        return regression;
    }
    public void setRegression(int regression) {
        this.regression = regression;
    }
    public int getSkipped() {
        return skipped;
    }
    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }
    public List<DevOpsRunStatusTestSuiteModel> getSuites() {
        return suites;
    }
    public void setSuites(List<DevOpsRunStatusTestSuiteModel> suites) {
        this.suites = suites;
    }


    public float getDuration() {
        return duration;
    }

    public void setDuration(float duration) {
        this.duration = duration;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}