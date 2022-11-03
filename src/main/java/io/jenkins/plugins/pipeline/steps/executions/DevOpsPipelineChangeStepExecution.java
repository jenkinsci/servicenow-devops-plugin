package io.jenkins.plugins.pipeline.steps.executions;

import static io.jenkins.plugins.DevOpsRunListener.DevOpsStageListener.getCurrentStageId;

import java.io.IOException;
import java.util.logging.Level;

import io.jenkins.plugins.DevOpsRunListener;
import io.jenkins.plugins.DevOpsRunStatusAction;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.model.*;
import io.jenkins.plugins.utils.CommUtils;
import io.jenkins.plugins.utils.DevOpsConstants;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.DevOpsRootAction;
import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.pipeline.steps.DevOpsPipelineChangeStep;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;

public class DevOpsPipelineChangeStepExecution extends AbstractStepExecutionImpl {

	private static final long serialVersionUID = 1L;

	private String callbackUrl;
	private String token;
	private DevOpsPipelineChangeStep step;

	public DevOpsPipelineChangeStepExecution(StepContext context, DevOpsPipelineChangeStep step) {
		super(context);
		this.step = step;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getToken() {
		return token;
	}

	public void setCallbackUrl(String callbackUrl) {
		this.callbackUrl = callbackUrl;
	}

	public String getCallbackUrl() {
		return callbackUrl;
	}

	public DevOpsPipelineChangeStep getStep() {
		return step;
	}

	@Override
	public boolean start() throws Exception {
		printDebug("start", null, null, Level.FINE);
		step.getDescriptor();
		DevOpsModel model = new DevOpsModel();
		Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);
		DevOpsModel.DevOpsPipelineInfo pipelineInfo = model.getPipelineInfo(run.getParent(), run.getId());
		DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());

		if (!this.step.isEnabled()) {
			getContext().onSuccess("[ServiceNow DevOps] Change control for step is disabled");
			return true;
		}
		
		//Validating config parameters
		if((this.step.getApplicationName() != null && this.step.getSnapshotName() == null) || 
				(this.step.getApplicationName() == null && this.step.getSnapshotName() != null)) {
					if (this.step.isIgnoreErrors() || jobProperties.isIgnoreSNErrors()) {
						listener.getLogger()
								.println("[ServiceNow DevOps] You must provide both application name and snapshot name.");
						getContext().onSuccess("[ServiceNow DevOps] You must provide both application name and snapshot name.");
						return true;
					}
					else {
							getContext().onFailure(new AbortException("Both application name and snapshot name should be provided"));
							return true;
					}
		}

		String pronoun = run.getParent().getPronoun();
		boolean isPullRequestPipeline = pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString());
		boolean pipelineTrack = model.checkIsTrackingCache(run.getParent(), run.getId());
		DevOpsConfiguration devopsConfig = DevOpsConfiguration.get();
		if (pipelineTrack && ((isPullRequestPipeline && devopsConfig.isTrackPullRequestPipelinesCheck()) || (!isPullRequestPipeline))) {

			// check if this step is already under change control
			DevOpsPipelineGraph graph = run.getAction(DevOpsRunStatusAction.class).getPipelineGraph();
			String currentStageId = getCurrentStageId(getContext(), graph);
			boolean isChangeStepInProgress = model.isChangeStepInProgress(run, currentStageId);
			if (isChangeStepInProgress) {
				getContext().onSuccess("[ServiceNow DevOps] A Change is already in progress");
				listener.getLogger().println("[ServiceNow DevOps] A Change is already in progress");
				return true;
			}
			// mark change begin status
			model.markChangeStepToProgress(run, currentStageId);

			DevOpsModel.PipelineChangeResponse changeResponse = model.handlePipeline(run, run.getParent(), this);
			// boolean[] result = changeResponse.getResult();

			// result[0]: shouldAbort, result[1]: shouldWait
			if (changeResponse.getAction() == DevOpsModel.PipelineChangeAction.WAIT) {
				listener.getLogger().println("[ServiceNow DevOps] Job is under change control"); // result will be set
				if(!GenericUtils.isEmpty(changeResponse.getMessage())) {
					listener.getLogger().println("[ServiceNow DevOps] " + changeResponse.getMessage());
				}
				// once callback is
				// received on
				// onTriggered
				return false;
			}

			if (changeResponse.getAction() == DevOpsModel.PipelineChangeAction.ABORT) {
				if (jobProperties.isIgnoreSNErrors() || this.step.isIgnoreErrors()) {
					if(this.step.getApplicationName() != null || this.step.getSnapshotName() != null) {
						listener.getLogger().println("[ServiceNow DevOps] "+changeResponse.getErrorMessage());
						getContext().onSuccess("[ServiceNow DevOps] "+changeResponse.getErrorMessage());
					}
					else {
						listener.getLogger().println("[ServiceNow DevOps] Error registering the job. Ignoring " + "error");
						getContext().onSuccess("[ServiceNow DevOps] Error registering the job. Ignoring " + "error");
					}
				} else {
					evaluateResultForPipeline(null, model.getAbortResult(), pipelineInfo,
							changeResponse.getErrorMessage());
				}
			} else {
				listener.getLogger().println("[ServiceNow DevOps] Job is not under change control");
				getContext().onSuccess("[ServiceNow DevOps] Job is not under change control");
			}
			return true;
		}

		// ServiceNow is unrecheable
		else if (pipelineInfo != null && pipelineInfo.isUnreacheable()) {
			if (this.step.isIgnoreErrors() || jobProperties.isIgnoreSNErrors()) {
				listener.getLogger()
						.println("[ServiceNow DevOps] ServiceNow instance not contactable, but will ignore");
				getContext().onSuccess("[ServiceNow DevOps] ServiceNow instance not contactable, but will ignore");
				return true;
			} else
				evaluateResultForPipeline(null, model.getCommFailureResult(), pipelineInfo, null);
		}

		// Tracking disabled
		getContext().onSuccess("[ServiceNow DevOps] Change control check not needed");
		return true;
	}

	@Override
	public void stop(Throwable cause) throws Exception {
		DevOpsRootAction.deregisterPipelineWebhook(this);
		getContext().onFailure(cause);
	}

	@Override
	public void onResume() {
		DevOpsModel model = new DevOpsModel();
		Run<?, ?> run = null;

		EnvVars vars = null;
		FlowNode fn = null;
		DevOpsPipelineGraph graph = null;
		TaskListener listener = null;
		try {
			run = getContext().get(Run.class);
			vars = getContext().get(EnvVars.class);
			listener = getContext().get(TaskListener.class);
			this.log(listener, "[ServiceNow DevOps] Job restarted");
			// After an unsafe restart, all caches are cleared so we need to re-establish
			// those

			if (run != null && vars != null) {
				DevOpsRunStatusAction action = run.getAction(DevOpsRunStatusAction.class);
				if (action != null)
					graph = action.getPipelineGraph();
				DevOpsModel.DevOpsPipelineInfo pipelineInfo = model.checkIsTracking(run.getParent(), run.getId(),
						vars.get("BRANCH_NAME"));
				if (pipelineInfo != null) {
					model.addToPipelineInfoCache(run.getParent().getFullName(), run.getId(), pipelineInfo);
					if (pipelineInfo.isTrack())
						model.addToTrackingCache(run.getParent().getFullName(), run.getId(), pipelineInfo);
				}
				// Also need to re attach the GraphListener to the run, so we can get the
				// failure event
				FlowExecution ex = ((WorkflowRun) run).getExecution();
				if (null != ex)
					ex.addListener(new DevOpsRunListener.DevOpsStageListener(run, vars, new DevOpsNotificationModel()));
			}
		} catch (IOException e) {
			printDebug("onResume", new String[]{"IOException"},
					new String[]{e.getMessage()}, Level.SEVERE);
		} catch (InterruptedException e) {
			printDebug("onResume", new String[]{"InterruptedException"},
					new String[]{e.getMessage()}, Level.SEVERE);
		}
		super.onResume();
		String currentStageId = getCurrentStageId(getContext(), graph);
		boolean isChangeStepInProgress = model.isChangeStepInProgress(run, currentStageId);
		if (isChangeStepInProgress) {
			this.log(listener, "[ServiceNow DevOps] A Change is already in progress");

			String jenkinsUrl = model.getJenkinsUrl();
			DevOpsPipelineNode stageNode = model.getStageNodeById(run, currentStageId);
			String buildUrl = DevOpsPipelineGraph.getStageExecutionUrl(stageNode.getPipelineExecutionUrl(),stageNode.getId());

			DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
			DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());
			JSONObject params = new JSONObject();
			params.put(DevOpsConstants.BUILD_URL_ATTR.toString(), buildUrl);
			JSONObject infoAPIResponse = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
					devopsConfig.getCallbackUrl(), params, null,
					devopsConfig.getUser(), devopsConfig.getPwd(), null, null);
			JSONObject result = (null != infoAPIResponse && !infoAPIResponse.isNullObject()) ? infoAPIResponse.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString()) : null;
			if (null != result && !result.isNullObject()) {
				String apiResult = result.getString("result");
				String token = result.getString("token");
				String changeComments = result.getString(DevOpsConstants.CALLBACK_RESULT_COMMENTS.toString());

				if (DevOpsConstants.CALLBACK_RESULT_PENDING.toString().equals(apiResult)) {
					this.setToken(token);
					DevOpsRootAction.registerPipelineWebhook(this);
					printDebug("onResume", new String[]{"message"},
							new String[]{"Job waiting for change callback"}, Level.FINE);
					this.log(listener, "[ServiceNow DevOps] Job waiting for change callback");

				} else {
					Job<?, ?> job = run.getParent();
					if (job != null) {
						String jobUrl = job.getAbsoluteUrl();
						String jobName = job.getName();
						if (jobUrl != null && jenkinsUrl != null && jobName != null) {
							model.sendBuildAndToken(token, jenkinsUrl, buildUrl, jobUrl, jobName, stageNode.getName(), stageNode,
									GenericUtils.isMultiBranch(job), vars != null ? vars.get("BRANCH_NAME") : null,
									true);
						}

					}
					if (!DevOpsConstants.CALLBACK_RESULT_SUCCESS.toString().equals(apiResult)) {
						String message = "";
						// Check if it was canceled by user
						if (DevOpsConstants.CALLBACK_RESULT_CANCELED.toString().equals(apiResult)) {
							message = "Canceled";
							printDebug("onResume", new String[]{"message"},
									new String[]{"Job was canceled"}, Level.FINE);
							this.log(listener, "[ServiceNow DevOps] Job was canceled");
							if (!GenericUtils.isEmpty(changeComments))
								this.log(listener, "[ServiceNow DevOps] \nCancel comments:\n" + changeComments);
						}

						// Not canceled and not approved
						else {
							message = "Not approved";
							printDebug("onResume", new String[]{"message"},
									new String[]{"Job was not approved for execution"},
									Level.FINE);
							this.log(listener,
									"[ServiceNow DevOps] Job was not approved for execution");
							if (!GenericUtils.isEmpty(changeComments))
								this.log(listener, "[ServiceNow DevOps] \nRejection comments:\n" + changeComments);
						}
						run.setResult(Result.FAILURE);
						getContext().onFailure(new AbortException(message));

					} else {

						printDebug("onResume", new String[]{"message"},
								new String[]{"Job approved for execution"}, Level.FINE);
						this.log(listener,
								"[ServiceNow DevOps] Job has been approved for execution");
						getContext().onSuccess(
								"[ServiceNow DevOps] Job has been approved for execution");
						if (!GenericUtils.isEmpty(changeComments))
							this.log(listener, "[ServiceNow DevOps] \nApproval comments:\n" + changeComments);
					}
				}
			} else {
				if (this.step.isIgnoreErrors() || jobProperties.isIgnoreSNErrors()) {
					this.log(listener, "[ServiceNow DevOps] ServiceNow instance not contactable, but will ignore");
					getContext().onSuccess("[ServiceNow DevOps] ServiceNow instance not contactable, but will ignore");
				} else {
					run.setResult(Result.FAILURE);
					getContext()
							.onFailure(new AbortException("[ServiceNow DevOps] ServiceNow instance not contactable"));
				}
			}
		} else {
			this.log(listener, "[ServiceNow DevOps] Job is not under change control");
			getContext().onSuccess("[ServiceNow DevOps] Job is not under change control");
		}

	}

	private void log(TaskListener listener, String message) {
		if (null != listener)
			listener.getLogger().println(message);
	}

	public void evaluateResultForPipeline(String token, String result, DevOpsModel.DevOpsPipelineInfo pipelineInfo,
	                                      String errorMessage) {
		DevOpsModel model = new DevOpsModel();
		TaskListener listener = null;

		Run<?, ?> run = null;
		try {
			run = getContext().get(Run.class);
			listener = getContext().get(TaskListener.class);
			if (run != null && result != null) {
				// If there's a token, this function was called by onTriggered callback handler
				// If there's no token, this function was called by model.handlePipeline and we
				// should probably fail the job as something didn't go as expected
				if (token != null) {
					EnvVars vars = null;
					FlowNode fn = null;
					try {
						fn = getContext().get(FlowNode.class);
						vars = getContext().get(EnvVars.class);
					} catch (IOException | InterruptedException e) {
						printDebug("evaluateResultForPipeline", new String[]{"InterruptedException"},
								new String[]{e.getMessage()}, Level.SEVERE);
					}
					Job<?, ?> job = run.getParent();
					if (job != null) {

						String jobUrl = job.getAbsoluteUrl();
						String jobName = job.getName();
						String jenkinsUrl = model.getJenkinsUrl();


						if (jobUrl != null && jenkinsUrl != null && jobName != null) {
							DevOpsRunStatusAction devOpsRunStatusAction = run.getAction(DevOpsRunStatusAction.class);

							String stageId = getCurrentStageId(getContext(), devOpsRunStatusAction.getPipelineGraph());
							DevOpsPipelineNode stageNode = model.getStageNodeById(run, stageId);
							String buildUrl = DevOpsPipelineGraph.getStageExecutionUrl(stageNode.getPipelineExecutionUrl(),stageId);
							model.sendBuildAndToken(token, jenkinsUrl, buildUrl, jobUrl, jobName, stageNode.getName(), stageNode,
									GenericUtils.isMultiBranch(job), vars != null ? vars.get("BRANCH_NAME") : null, true);
						}

					}
				}
				if (!model.isApproved(result)) {
					String message = "";
					// Check if it was canceled by user
					if (model.isCanceled(result)) {
						message = "Canceled";
						printDebug("evaluateResultForPipeline", new String[]{"message"},
								new String[]{"Job was canceled"}, Level.FINE);
						listener.getLogger().println("[ServiceNow DevOps] Job was canceled");
						String changeComments = model.getChangeComments(result);
						if (!GenericUtils.isEmpty(changeComments))
							listener.getLogger().println("[ServiceNow DevOps] \nCancel comments:\n" + changeComments);
					} else if (model.isCommFailure(result) && pipelineInfo != null) {
						message = pipelineInfo.getErrorMessage();
						printDebug("evaluateResultForPipeline", new String[]{"message"},
								new String[]{message}, Level.FINE);
						listener.getLogger().println("[ServiceNow DevOps] " + message);
					}
					// Not canceled and not approved
					else {
						message = "Not approved";
						String displayMessage = GenericUtils.isEmpty(errorMessage) ? "Job was not approved for execution"
								: errorMessage;
						printDebug("evaluateResultForPipeline", new String[]{"message"},
								new String[]{displayMessage},
								Level.FINE);
						listener.getLogger().println(
								"[ServiceNow DevOps] " + displayMessage);

						String changeComments = model.getChangeComments(result);
						if (!GenericUtils.isEmpty(changeComments))
							listener.getLogger().println("[ServiceNow DevOps] \nRejection comments:\n" + changeComments);
					}
					run.setResult(Result.FAILURE);
					getContext().onFailure(new AbortException(message));

				} else {
					printDebug("evaluateResultForPipeline", new String[]{"message"},
							new String[]{"Job approved for execution"}, Level.FINE);
					listener.getLogger().println(
							"[ServiceNow DevOps] Job has been approved for execution");
					getContext().onSuccess(
							"[ServiceNow DevOps] Job has been approved for execution");
					String changeComments = model.getChangeComments(result);
					if (!GenericUtils.isEmpty(changeComments))
						listener.getLogger().println("[ServiceNow DevOps] \nApproval comments:\n" + changeComments);
				}
			}
		} catch (IOException | InterruptedException e) {
			printDebug("evaluateResultForPipeline", new String[]{"InterruptedException"},
					new String[]{e.getMessage()}, Level.SEVERE);
		}
	}

	// called from DevOpsRootAction _handlePipelineCallback
	public void onTriggered(String token, String result) {
		if (token != null && result != null) {
			DevOpsRootAction.deregisterPipelineWebhook(this);
			evaluateResultForPipeline(token, result, null, null);
		}
	}

	// called from DevOpsRootAction _displayPipelineChangeRequestInfo
	public void displayPipelineChangeRequestInfo(String token, String info) {
		printDebug("displayPipelineChangeRequestInfo", new String[]{"info"}, new String[]{info}, Level.FINE);
		if (token != null && info != null) {
			DevOpsModel model = new DevOpsModel();
			TaskListener listener = null;
			try {
				listener = getContext().get(TaskListener.class);
			} catch (IOException | InterruptedException e) {
				printDebug("displayPipelineChangeRequestInfo", new String[]{"IOException"},
					new String[]{e.getMessage()}, Level.SEVERE);
			}

			String changeRequestId = model.getChangeRequestInfo(info);
	
			//Getting config information
			JSONObject configStatus = model.getConfigInfo(info);
			String message = "";
			if(configStatus != null)
				message = configStatus.getString("message");
			printDebug("displayPipelineChangeRequestInfo", new String[]{"changeRequestId"}, new String[]{changeRequestId}, Level.FINE);
			if (!GenericUtils.isEmpty(changeRequestId)) {
				if(GenericUtils.isEmpty(message)) {
					listener.getLogger().println("[ServiceNow DevOps] Change Request Id : " + changeRequestId);
					try {
						Run<?, ?> run = getContext().get(Run.class);
						DevOpsRunStatusAction action =
								run.getAction(DevOpsRunStatusAction.class);
						String currentStageName = DevOpsRunListener.DevOpsStageListener.getCurrentStageName(getContext(), action.getPipelineGraph());
						action.changeRequestInfo.put(currentStageName, changeRequestId);
					}catch (Exception ignore) {
						printDebug("displayPipelineChangeRequestInfo", new String[]{"Exception"},
							new String[]{ignore.getMessage()}, Level.SEVERE);
					}
				}
				else 
					listener.getLogger().println("[ServiceNow DevOps] "+message);		
			}	
		}
	}

	private void printDebug(String methodName, String[] variables, String[] values,
	                        Level logLevel) {
		GenericUtils
				.printDebug(DevOpsPipelineChangeStepExecution.class.getName(), methodName,
						variables, values, logLevel);
	}
}