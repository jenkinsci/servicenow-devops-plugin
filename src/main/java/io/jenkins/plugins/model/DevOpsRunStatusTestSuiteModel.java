package io.jenkins.plugins.model;

import java.util.List;

public class DevOpsRunStatusTestSuiteModel {

    private List<DevOpsRunStatusTestCaseModel> cases;
    private float duration;
    private String name;
    private String stderr;
    private String stdout;

    public DevOpsRunStatusTestSuiteModel() {
        this.duration = 0;
        this.name = "";
        this.stderr = "";
        this.stdout = "";
    }

    public void setDuration(float duration) {
        this.duration = duration;
    }
    public float getDuration() {
        return duration;
    }

    public void setName(String name) {
        if (name != null)
            this.name = name;
    }
    public String getName() {
        return name;
    }

    public void setStdErr(String stderr) {
        if (stderr != null)
            this.stderr = stderr;
    }
    public String getStdErr() {
        return stderr;
    }

    public void setStdOut(String stdout) {
        if (stdout != null)
            this.stdout = stdout;
    }
    public String getStdOut() {
        return stdout;
    }

    public void setCases(List<DevOpsRunStatusTestCaseModel> cases) {
        this.cases = cases;
    }

    public List<DevOpsRunStatusTestCaseModel> getCases() {
        return cases;
    }

}