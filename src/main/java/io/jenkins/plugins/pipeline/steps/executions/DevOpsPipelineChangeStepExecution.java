package io.jenkins.plugins.pipeline.steps.executions;

import java.io.IOException;

import io.jenkins.plugins.DevOpsRunListener;
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
import io.jenkins.plugins.utils.GenericUtils;

public class DevOpsPipelineChangeStepExecution extends AbstractStepExecutionImpl {

	private static final long serialVersionUID = 1L;

	String callbackUrl;
	String token;
	DevOpsPipelineChangeStep step;

	public DevOpsPipelineChangeStepExecution(StepContext context,
											 DevOpsPipelineChangeStep step) {
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
		printDebug("start", null, null, true);
		step.getDescriptor();
		DevOpsModel model = new DevOpsModel();
		Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);
		DevOpsModel.DevOpsPipelineInfo pipelineInfo = model.getPipelineInfo(run.getParent(), run.getId());
		DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());

		if (!this.step.isEnabled()){
			getContext().onSuccess("[ServiceNow DevOps] Change control for step is disabled");
			return true;
		}

		if (model.checkIsTrackingCache(run.getParent(), run.getId())) {

			// check if this step is already under change control
			boolean isChangeStepInProgress = model.isChangeStepInProgress(run);
			if(isChangeStepInProgress) {
				getContext().onSuccess("[ServiceNow DevOps] A Change is already in progress");
				listener.getLogger().println("[ServiceNow DevOps] A Change is already in progress");
				return true;
			}
			// mark change begin status
			model.markChangeStepToProgress(run);

			DevOpsModel.PipelineChangeResponse changeResponse=model.handlePipeline(run, run.getParent(), this);
			//boolean[] result = changeResponse.getResult();

			// result[0]: shouldAbort, result[1]: shouldWait
			if (changeResponse.getAction() == DevOpsModel.PipelineChangeAction.WAIT) {
				listener.getLogger().println(
						"[ServiceNow DevOps] Job is under change control"); // result will be set once callback is received on onTriggered
				return false;
			}

			if (changeResponse.getAction() == DevOpsModel.PipelineChangeAction.ABORT) {
				if (jobProperties.isIgnoreSNErrors() || this.step.isIgnoreErrors()) {
					listener.getLogger()
							.println("[ServiceNow DevOps] Error registering the job. Ignoring " +
									"error");
					getContext()
							.onSuccess("[ServiceNow DevOps] Error registering the job. Ignoring " +
									"error");
				} else {
					evaluateResultForPipeline(null, model.getAbortResult(), pipelineInfo, changeResponse.getErrorMessage());
				}
			} else {
				listener.getLogger()
						.println("[ServiceNow DevOps] Job is not under change control");
				getContext()
						.onSuccess("[ServiceNow DevOps] Job is not under change control");
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
			}
			else
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

		TaskListener listener = null;
		try {
			run = getContext().get(Run.class);
			fn = getContext().get(FlowNode.class);
			vars = getContext().get(EnvVars.class);
			listener = getContext().get(TaskListener.class);
			this.log(listener, "[ServiceNow DevOps] Job restarted");
			// After an unsafe restart, all caches are cleared so we need to re-establish those

			if (run != null && vars != null) {
				DevOpsModel.DevOpsPipelineInfo pipelineInfo = model.checkIsTracking(run.getParent(),
						run.getId(), vars.get("BRANCH_NAME"));
				if (pipelineInfo != null) {
					model.addToPipelineInfoCache(run.getParent().getFullName(), run.getId(), pipelineInfo);
					if (pipelineInfo.isTrack())
						model.addToTrackingCache(run.getParent().getFullName(), run.getId(), pipelineInfo);
				}
				// Also need to re attach the GraphListener to the run, so we can get the failure event
				FlowExecution ex = ((WorkflowRun) run).getExecution();
				if(null != ex)
					ex.addListener(new DevOpsRunListener.DevOpsStageListener(run, vars, new DevOpsNotificationModel(), model.isDebug()));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		super.onResume();

		boolean isChangeStepInProgress = model.isChangeStepInProgress(run);
		if(isChangeStepInProgress) {
			this.log(listener, "[ServiceNow DevOps] A Change is already in progress");

			String jenkinsUrl = model.getJenkinsUrl();
			String stageName = model.getStageNameFromAction(run);
			DevOpsPipelineNode rootNode = model.getRootNode(run, stageName);
			String buildUrl = model.getBuildUrl(fn, vars, run, jenkinsUrl, stageName, rootNode);

			DevOpsConfiguration devopsConfig = GenericUtils.getDevOpsConfiguration();
			DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());
			JSONObject params = new JSONObject();
			params.put(DevOpsConstants.BUILD_URL_ATTR.toString(), buildUrl);
			JSONObject infoAPIResponse = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(),
					devopsConfig.getCallbackUrl(), params, null,
					devopsConfig.getUser(), devopsConfig.getPwd(), model.isDebug());
			JSONObject result = (null != infoAPIResponse && !infoAPIResponse.isNullObject()) ? infoAPIResponse.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString()) : null;
			if (null != result && !result.isNullObject()) {
				String apiResult = result.getString("result");
				String token = result.getString("token");

				if (DevOpsConstants.CALLBACK_RESULT_PENDING.toString().equals(apiResult)) {
					this.setToken(token);
					DevOpsRootAction.registerPipelineWebhook(this);
					printDebug("onResume", new String[]{"message"},
							new String[]{"Job waiting for change callback"}, model.isDebug());
					this.log(listener, "[ServiceNow DevOps] Job waiting for change callback");

				} else {
					Job<?, ?> job = run.getParent();
					if (job != null) {
						String jobUrl = job.getAbsoluteUrl();
						String jobName = job.getName();
						if (jobUrl != null && jenkinsUrl != null && jobName != null) {
							model.sendBuildAndToken(token, jenkinsUrl, buildUrl, jobUrl,
									jobName, stageName, rootNode, GenericUtils.isMultiBranch(job),
									vars != null ? vars.get("BRANCH_NAME") : null, true);
						}

					}
					if (!DevOpsConstants.CALLBACK_RESULT_SUCCESS.toString().equals(apiResult)) {
						String message = "";
						// Check if it was canceled by user
						if (DevOpsConstants.CALLBACK_RESULT_CANCELED.toString().equals(apiResult)) {
							message = "Canceled";
							printDebug("onResume", new String[]{"message"},
									new String[]{"Job was canceled"}, model.isDebug());
							this.log(listener, "[ServiceNow DevOps] Job was canceled");
						}

						// Not canceled and not approved
						else {
							message = "Not approved";
							printDebug("onResume", new String[]{"message"},
									new String[]{"Job was not approved for execution"},
									model.isDebug());
							this.log(listener,
									"[ServiceNow DevOps] Job was not approved for execution");
						}
						run.setResult(Result.FAILURE);
						getContext().onFailure(new AbortException(message));


					} else {

						printDebug("onResume", new String[]{"message"},
								new String[]{"Job approved for execution"}, model.isDebug());
						this.log(listener,
								"[ServiceNow DevOps] Job has been approved for execution");
						getContext().onSuccess(
								"[ServiceNow DevOps] Job has been approved for execution");
					}
				}
			} else {
				if (this.step.isIgnoreErrors() || jobProperties.isIgnoreSNErrors()) {
					this.log(listener, "[ServiceNow DevOps] ServiceNow instance not contactable, but will ignore");
					getContext().onSuccess("[ServiceNow DevOps] ServiceNow instance not contactable, but will ignore");
				}
				else {
					run.setResult(Result.FAILURE);
					getContext().onFailure(new AbortException("[ServiceNow DevOps] ServiceNow instance not contactable"));
				}
			}
		} else {
			this.log(listener, "[ServiceNow DevOps] Job is not under change control");
			getContext()
					.onSuccess("[ServiceNow DevOps] Job is not under change control");
		}


	}

	private void log(TaskListener listener, String message) {
		if(null != listener)
			listener.getLogger().println(message);
	}

	public void evaluateResultForPipeline(String token, String result, DevOpsModel.DevOpsPipelineInfo pipelineInfo,
	                                      String errorMessage){
		DevOpsModel model = new DevOpsModel();
		TaskListener listener = null;
		Run<?, ?> run = null;
		try {
			run = getContext().get(Run.class);
			listener = getContext().get(TaskListener.class);
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		if (run != null && result != null) {
			// If there's a token, this function was called by onTriggered callback handler
			// If there's no token, this function was called by model.handlePipeline and we should probably fail the job as something didn't go as expected
			if (token != null) {
				EnvVars vars = null;
				FlowNode fn = null;
				try {
					fn = getContext().get(FlowNode.class);
					vars = getContext().get(EnvVars.class);
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
				Job<?, ?> job = run.getParent();
				if (job != null) {

					String jobUrl = job.getAbsoluteUrl();
					String jobName = job.getName();
					String jenkinsUrl = model.getJenkinsUrl();

					if (jobUrl != null && jenkinsUrl != null && jobName != null) {
						String stageName = model.getStageNameFromAction(run);
						DevOpsPipelineNode rootNode = model.getRootNode(run, stageName);
						String buildUrl = model.getBuildUrl(fn, vars, run, jenkinsUrl, stageName, rootNode);
						model.sendBuildAndToken(token, jenkinsUrl, buildUrl, jobUrl,
								jobName, stageName, rootNode, GenericUtils.isMultiBranch(job),
								vars!=null? vars.get("BRANCH_NAME"):null, true);
					}

				}
			}
			if (!model.isApproved(result)) {
				String message = "";
				// Check if it was canceled by user
				if (model.isCanceled(result)) {
					message = "Canceled";
					printDebug("evaluateResultForPipeline", new String[]{"message"},
							new String[]{"Job was canceled"}, model.isDebug());
					listener.getLogger().println("[ServiceNow DevOps] Job was canceled");
				}
				else if (model.isCommFailure(result) && pipelineInfo != null) {
					message = pipelineInfo.getErrorMessage();
					printDebug("evaluateResultForPipeline", new String[]{"message"},
							new String[]{message}, model.isDebug());
					listener.getLogger().println("[ServiceNow DevOps] " + message);
				}
				// Not canceled and not approved
				else {
					message = "Not approved";
					String displayMessage = GenericUtils.isEmpty(errorMessage)? "Job was not approved for execution"
							:errorMessage;
					printDebug("evaluateResultForPipeline", new String[]{"message"},
							new String[]{displayMessage},
							model.isDebug());
					listener.getLogger().println(
							"[ServiceNow DevOps] " + displayMessage);
				}
				run.setResult(Result.FAILURE);
				getContext().onFailure(new AbortException(message));

			} else {
				printDebug("evaluateResultForPipeline", new String[]{"message"},
						new String[]{"Job approved for execution"}, model.isDebug());
				listener.getLogger().println(
						"[ServiceNow DevOps] Job has been approved for execution");
				getContext().onSuccess(
						"[ServiceNow DevOps] Job has been approved for execution");
			}
		}
	}

	// called from DevOpsRootAction _handlePipelineCallback
	public void onTriggered(String token, String result) {
		if (token != null && result != null) {
			DevOpsRootAction.deregisterPipelineWebhook(this);
			evaluateResultForPipeline(token, result, null, null);
		}
	}

	private void printDebug(String methodName, String[] variables, String[] values,
							boolean debug) {
		GenericUtils
				.printDebug(DevOpsPipelineChangeStepExecution.class.getName(), methodName,
						variables, values, debug);
	}
}