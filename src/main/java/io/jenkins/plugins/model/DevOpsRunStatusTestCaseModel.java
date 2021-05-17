package io.jenkins.plugins.model;

public class DevOpsRunStatusTestCaseModel {
    private String className;
    private float duration;
    private String name;
    private String status;
    private boolean skipped;
    private String stderr;
    private String stdout;
    private String errorDetails;
    private String errorStackTrace;

    public DevOpsRunStatusTestCaseModel() {
        this.className = "";
        this.duration = 0;
        this.name = "";
        this.status = "";
        this.stderr = "";
        this.stdout = "";
        this.errorDetails = "";
        this.errorStackTrace = "";
    }

    public void setClassName(String className) {
        if (className != null)
            this.className = className;
    }
    public String getClassName() {
        return className;
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

    public void setStatus(String status) {
        if (status != null)
            this.status = status;
    }
    public String getStatus() {
        return status;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }
    public boolean getSkipped() {
        return skipped;
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

    public void setErrorDetails(String errorDetails) {
        if (errorDetails != null)
            this.errorDetails = errorDetails;
    }
    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorStackTrace(String errorStackTrace) {
        if (errorStackTrace != null)
            this.errorStackTrace = errorStackTrace;
    }
    public String getErrorStackTrace() {
        return errorStackTrace;
    }

}