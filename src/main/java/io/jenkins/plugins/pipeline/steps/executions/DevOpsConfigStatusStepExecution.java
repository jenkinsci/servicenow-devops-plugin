package io.jenkins.plugins.pipeline.steps.executions;

import hudson.AbortException;
import hudson.model.Result;
import java.io.IOException;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsConfigStatusStep;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import io.jenkins.plugins.utils.DevOpsConstants;

/*
  @author : roy.ca@servicenow.com
  Story : STRY51855155
  
  This step gets the application name and config deployable name as input.
  And returns the  snapshotId, validationResults and validationStatus of the latest snapshot

  Example : snDevOpsConfigSnapshot(applicationName: "application_name", deployableName: "deployable_name")
*/

public class DevOpsConfigStatusStepExecution extends SynchronousStepExecution<Boolean> {

    private static final long serialVersionUID = 1L;

    private DevOpsConfigStatusStep step;
    
    public DevOpsConfigStatusStepExecution(StepContext context, DevOpsConfigStatusStep step) {
		super(context);
		this.step = step;
    }
    
    @Override
    protected Boolean run() throws Exception {
        Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);
	 
        DevOpsModel model = new DevOpsModel();
        
        GenericUtils.printConsoleLog(listener, "snDevOpsConfigSnapshot - ConfigStatus Step Exceution starts");

        JSONObject snapshotStatus = new JSONObject();
        if(validateStepInputs()) {
            try {
                snapshotStatus = model.getSnapshotStatus(this.step.getApplicationName().trim(), this.step.getDeployableName().trim());
             } catch(Exception e) {
                 GenericUtils.printConsoleLog(listener, "snDevOpsConfigSnapshot - Status step failed : "+e.getMessage());
            }
            if(snapshotStatus.containsKey(DevOpsConstants.COMMON_RESULT_ERROR.toString())) {
                JSONObject error = snapshotStatus.getJSONObject(DevOpsConstants.COMMON_RESULT_ERROR.toString());
                String errorMessage = error.getString(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
                GenericUtils.printConsoleLog(listener, "snDevOpsConfigSnapshot - Fetching of snapshot failed due to : "+errorMessage);
                run.setResult(Result.FAILURE);
                throw new AbortException("Unable to fetch snapshot details.");
            }
            else{
                    JSONArray result = snapshotStatus.getJSONArray(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
                    if(result.size() == 0) {
                        GenericUtils.printConsoleLog(listener, "snDevOpsConfigSnapshot - There is no published snapshot for given application and deployable");
                        return Boolean.valueOf(true);
                    }
                    else {
                        GenericUtils.printConsoleLog(listener, "snDevOpsConfigSnapshot - Snapshot Status is : "+ snapshotStatus);
                        return Boolean.valueOf(true);
                    }
                }
            }
        else{
            run.setResult(Result.FAILURE);
            throw new AbortException("Missing Parameters.");
            }
        }

    
    private Boolean validateStepInputs() throws Exception
    {
        TaskListener listener = getContext().get(TaskListener.class);
        
        if(null == this.step.getApplicationName() || this.step.getApplicationName().trim().length() == 0) {
            GenericUtils.printConsoleLog(listener, "snDevOpsConfigSnapshot - Application Name is missing");
            GenericUtils.printConsoleLog(listener, "snDevOpsConfigSnapshot - Mandatory Parameters are ApplicationName and Deployable Name.");
            return false;
        }

        if(null == this.step.getDeployableName() || this.step.getDeployableName().trim().length() == 0) {
            GenericUtils.printConsoleLog(listener, "snDevOpsConfigSnapshot - Deployable Name is missing");
            GenericUtils.printConsoleLog(listener, "snDevOpsConfigSnapshot - Mandatory Parameters are ApplicationName and Deployable Name.");
            return false;
        }
        return true;
    }
}