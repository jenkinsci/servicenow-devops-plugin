package io.jenkins.plugins.model;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.DevOpsRootAction;
import io.jenkins.plugins.DevOpsRunStatusAction;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineChangeStepExecution;
import io.jenkins.plugins.utils.CommUtils;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.logging.Level;

import static io.jenkins.plugins.DevOpsRunListener.DevOpsStageListener.getCurrentStageId;

public class DevOpsChangePollingModel {
    public DevOpsPipelineNode getStageNodeById(Run<?, ?> run, String stageId) {
        DevOpsModel model = new DevOpsModel();
        return model.getStageNodeById(run, stageId);
    }
    public long getChangeStartTime(Run<?, ?> run, String stageId){
        DevOpsPipelineNode currentNode = getStageNodeById(run, stageId);
        if (null != currentNode)
            return currentNode.getChangeStartTime();
        return 0;
    }

    public void setChangeStartTime(Run<?, ?> run, String stageId, long startTime){
        DevOpsPipelineNode currentNode = getStageNodeById(run, stageId);
        if (null != currentNode)
            currentNode.setChangeStartTime(startTime);
    }
    public void launchChangePollingThread(TaskListener listener, Run<?, ?> run, Job<?, ?> controlledJob,
                                          DevOpsPipelineChangeStepExecution stepExecution) {

        DevOpsPipelineGraph graph = run.getAction(DevOpsRunStatusAction.class).getPipelineGraph();
        String stageId = getCurrentStageId(stepExecution.getContext(), graph);
        long startTime = System.currentTimeMillis();
        if(this.getChangeStartTime(run,stageId) > 0)
            startTime = this.getChangeStartTime(run,stageId);
        else
            this.setChangeStartTime(run,stageId, startTime);

        long pollingIntervalFinal = stepExecution.getStep().getPollingInterval() > 0 ? stepExecution.getStep().getPollingInterval() * 1000l : -1;
        long changeFailureTimeoutFinal = stepExecution.getStep().getChangeCreationTimeOut() * 1000l;
        long changeStepTimeoutFinal = stepExecution.getStep().getChangeStepTimeOut() * 1000l ;

        if(pollingIntervalFinal <=0 && changeFailureTimeoutFinal <= 0 && changeStepTimeoutFinal <= 0)
            return;

        final long stageStartTime = startTime;
        Runnable pollingThread = () ->
        {
            Thread.currentThread().setName("DevopsChangePollingThread");
            final String CHG_STEP = "changeStep"; // Priority: 1
            final String CHG_CREATION = "changeCreation"; // Priority: 2
            final String POLLING = "polling"; // Priority: 3
            class Interval {
                long intervalTime;
                String type;
                int priority;
                public Interval(long intervalTime, int priority, String type){
                    this.intervalTime = intervalTime;
                    this.type = type;
                    this.priority = priority;
                }
            }
            Comparator<Interval> comparator = (Interval interval1, Interval interval2) -> {
                if (interval1.intervalTime > interval2.intervalTime)
                    return 1;
                else if (interval1.intervalTime == interval2.intervalTime){
                    if(interval1.priority > interval2.priority)
                        return 1;
                    else if (interval1.priority == interval2.priority)
                        return 0;
                    else
                        return -1;
                }
                else return -1;
            };
            try {
                long sleepTime = 0;
                long nextPollingTime = pollingIntervalFinal;
                boolean isChangeCreationChecked = false;
                DevOpsChangeRequestDetails previousChangeDetails = new DevOpsChangeRequestDetails();
                PriorityQueue<Interval> sleepIntervals = new PriorityQueue<>(3, comparator);
                while(!Thread.currentThread().isInterrupted()) {
                    long nextPollingTimeTemp = -1;
                    if(pollingIntervalFinal >= 0)
                        sleepIntervals.add(new Interval(nextPollingTime, 3, POLLING));

                    long duration = System.currentTimeMillis() - stageStartTime;
                    if(!isChangeCreationChecked && changeFailureTimeoutFinal > 0)
                        if(changeFailureTimeoutFinal <= duration) {
                            sleepIntervals.add(new Interval(0, 2, CHG_CREATION));
                        }else if(changeFailureTimeoutFinal <= duration + nextPollingTime){
                            sleepIntervals.add(new Interval(changeFailureTimeoutFinal - duration, 2, CHG_CREATION));
                            nextPollingTimeTemp = nextPollingTime - (changeFailureTimeoutFinal - duration);
                        }else
                            sleepIntervals.add(new Interval(changeFailureTimeoutFinal - duration, 2, CHG_CREATION));

                    if(changeStepTimeoutFinal > 0)
                        if(changeStepTimeoutFinal <= duration) {
                            sleepIntervals.add(new Interval(0, 1, CHG_STEP));
                        }else if(changeStepTimeoutFinal <= duration + nextPollingTime){
                            sleepIntervals.add(new Interval(changeStepTimeoutFinal - duration, 1, CHG_STEP));
                            if(nextPollingTimeTemp == -1 || changeFailureTimeoutFinal > changeStepTimeoutFinal)
                                nextPollingTimeTemp = nextPollingTime - (changeStepTimeoutFinal - duration);
                        }else
                            sleepIntervals.add(new Interval(changeStepTimeoutFinal - duration, 1, CHG_STEP));

                    if(nextPollingTimeTemp != -1) nextPollingTime = nextPollingTimeTemp;
                    if(sleepIntervals.isEmpty()) break;
                    Interval nextInterval = sleepIntervals.poll();
                    sleepTime  = nextInterval.intervalTime;
                    if (sleepTime > 0) Thread.sleep(sleepTime);

                    JSONObject response = this.getChangeStatusInfo(run, controlledJob, stepExecution);
                    boolean changeFound = Boolean.parseBoolean(GenericUtils.parseResponseResult(response, DevOpsConstants.CHANGE_FOUND.toString()));
                    switch (nextInterval.type){
                        case CHG_CREATION:
                            isChangeCreationChecked = true;
                            if(!changeFound) this.checkAndLogChangeCreationFailure(stepExecution, listener);
                            break;
                        case CHG_STEP:
                            this.checkAndLogChangeStepTimeout(stepExecution, listener);
                            break;
                        default:
                            previousChangeDetails = this.logPollingMessages(listener, response, previousChangeDetails);
                            nextPollingTime = pollingIntervalFinal;
                    }
                    sleepIntervals.clear();
                }
            } catch (InterruptedException e) {
                printDebug("launchChangePollingThread", new String[]{"message"},
                        new String[]{"[ServiceNow DevOps] Polling is stopped"}, Level.INFO);
            } catch (Exception e) {
                printDebug("launchChangePollingThread", new String[]{"exception"},
                        new String[]{e.getMessage()}, Level.WARNING);
            }
        };
        Thread pollingThreadImpl = new Thread(pollingThread);
        stepExecution.setPollingThread(pollingThreadImpl);
        pollingThreadImpl.start();
    }

    public void checkAndLogChangeStepTimeout(DevOpsPipelineChangeStepExecution stepExecution, TaskListener listener) throws IOException, InterruptedException {
        boolean abort = stepExecution.getStep().isAbortOnChangeStepTimeOut();
        listener.getLogger().println("[ServiceNow DevOps] Change Step timeout occurred.");
        this.resumeOrAbortThePipeline(stepExecution, listener, abort, "Pipeline aborted due to change step timeout");
    }
    public void checkAndLogChangeCreationFailure (DevOpsPipelineChangeStepExecution stepExecution, TaskListener listener) throws IOException, InterruptedException {
        boolean abort = stepExecution.getStep().isAbortOnChangeCreationFailure();
        listener.getLogger().println("[ServiceNow DevOps] Change Creation failure timeout occurred.");
        this.resumeOrAbortThePipeline(stepExecution, listener, abort, "Pipeline aborted due to change creation timeout");
    }

    public void resumeOrAbortThePipeline (DevOpsPipelineChangeStepExecution stepExecution, TaskListener listener, boolean abort, String failureMessage) throws IOException, InterruptedException {
        DevOpsRootAction.deregisterPipelineWebhook(stepExecution);
        if(abort){
            stepExecution.getContext().get(Run.class).setResult(Result.FAILURE);
            stepExecution.getContext().onFailure(new AbortException("[ServiceNow DevOps] " + failureMessage));
            listener.getLogger().println("[ServiceNow DevOps] Aborting the pipeline");
        }
        else{
            stepExecution.getContext().onSuccess("[ServiceNow DevOps] Resuming the pipeline");
            listener.getLogger().println("[ServiceNow DevOps] Resuming the pipeline");
        }
        stepExecution.stopPollingThread();
    }
    public DevOpsChangeRequestDetails logPollingMessages (TaskListener listener, JSONObject response, DevOpsChangeRequestDetails previousChangeDetails){
        boolean changeFound = Boolean.parseBoolean(GenericUtils.parseResponseResult(response, DevOpsConstants.CHANGE_FOUND.toString()));
        if(changeFound){
            String changeState = GenericUtils.parseResponseResult(response, DevOpsConstants.CHANGE_STATE_DISPLAY_VALUE.toString());
            String changeAssignmentGroup = GenericUtils.parseResponseResult(response, DevOpsConstants.CHANGE_ASSIGNMENT_GROUP.toString());
            String changeApprovers = GenericUtils.parseResponseResult(response, DevOpsConstants.CHANGE_APPROVERS.toString());
            String changeStartDate = GenericUtils.parseResponseResult(response, DevOpsConstants.CHANGE_START_DATE.toString());
            String changeEndDate = GenericUtils.parseResponseResult(response, DevOpsConstants.CHANGE_END_DATE.toString());
            String changeDetails = GenericUtils.parseResponseResult(response, DevOpsConstants.CHANGE_DETAILS.toString());
            DevOpsChangeRequestDetails currentChangeDetails = new DevOpsChangeRequestDetails(changeState, changeAssignmentGroup, changeApprovers, changeStartDate, changeEndDate, changeDetails);
            if(!previousChangeDetails.equals(currentChangeDetails)){
                listener.getLogger().println();
                listener.getLogger().println("[ServiceNow DevOps] Change State: " + changeState);
                if(changeApprovers != null){
                    listener.getLogger().println("[ServiceNow DevOps] Change AssignmentGroup: " + changeAssignmentGroup +
                            ", Change Approvers: " + changeApprovers);
                }if(GenericUtils.isNotEmpty(changeStartDate) && GenericUtils.isNotEmpty(changeEndDate)){
                    listener.getLogger().println("[ServiceNow DevOps] Planned Start Date: " + changeStartDate + " UTC" +
                            ", Planned End Date: " + changeEndDate + " UTC");
                }
                if(changeDetails != null){
                    listener.getLogger().println("[ServiceNow DevOps] Change Details: " + changeDetails);
                }
                return currentChangeDetails;
            }
        }
        return previousChangeDetails;
    }

    public JSONObject getChangeStatusInfo(Run<?, ?> run, Job<?, ?> controlledJob,
                                          DevOpsPipelineChangeStepExecution stepExecution) throws IOException, InterruptedException {

        JSONObject response = null;

        if (run != null && controlledJob != null) {
            JSONObject queryParams = new JSONObject();
            String jobName = controlledJob.getName();
            DevOpsPipelineGraph graph = run.getAction(DevOpsRunStatusAction.class).getPipelineGraph();

            if (jobName != null) {
                String stageId = getCurrentStageId(stepExecution.getContext(), graph);
                DevOpsPipelineNode stageNode = getStageNodeById(run, stageId);
                String stageName =  stageNode.getName();
                String buildNumber = String.valueOf(run.getNumber());

                EnvVars vars = stepExecution.getContext().get(EnvVars.class);
                String branchName = vars.get(DevOpsConstants.PIPELINE_BRANCH_NAME.toString());
                String pipelineName = vars.get(DevOpsConstants.PIPELINE_JOB_NAME.toString());

                DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();

                queryParams.put(DevOpsConstants.TOOL_ID_ATTR.toString(), devopsConfig.getToolId());
                queryParams.put(DevOpsConstants.TOOL_TYPE_ATTR.toString(), DevOpsConstants.TOOL_TYPE.toString());
                queryParams.put(DevOpsConstants.ARTIFACT_PIPELINE_NAME.toString(), pipelineName);
                queryParams.put(DevOpsConstants.ARTIFACT_STAGE_NAME.toString(), stageName);
                queryParams.put(DevOpsConstants.CONFIG_BUILD_NUMBER.toString(), buildNumber);
                queryParams.put(DevOpsConstants.SCM_BRANCH_NAME.toString(), branchName);
				if (!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {

					Map<String, String> tokenDetails = new HashMap<String, String>();
					tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
							devopsConfig.getTokenText(devopsConfig.getSecretCredentialId()));
					response = CommUtils.callV2Support(DevOpsConstants.REST_GET_METHOD.toString(),
							devopsConfig.getChangeInfoUrl(), queryParams, null, devopsConfig.getUser(),
							devopsConfig.getPwd(), null, null, tokenDetails);
				} else {
					response = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
							devopsConfig.getChangeInfoUrl(), queryParams, null, devopsConfig.getUser(),
							devopsConfig.getPwd(), null, null);
				}
                

            }
        }
        return response;
    }
    private void printDebug(String methodName, String[] variables, String[] values, Level logLevel) {
        GenericUtils
                .printDebug(DevOpsModel.class.getName(), methodName, variables, values,
                        logLevel);
    }
}
