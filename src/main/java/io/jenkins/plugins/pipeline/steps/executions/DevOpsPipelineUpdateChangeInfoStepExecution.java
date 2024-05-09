package io.jenkins.plugins.pipeline.steps.executions;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.DevOpsRunStatusAction;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.config.DevOpsConfigurationEntry;
import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.model.DevOpsPipelineInfoConfig;
import io.jenkins.plugins.pipeline.steps.DevOpsPipelineUpdateChangeInfoStep;
import io.jenkins.plugins.utils.CommUtils;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

public class DevOpsPipelineUpdateChangeInfoStepExecution extends SynchronousStepExecution<Boolean> {

	private DevOpsPipelineUpdateChangeInfoStep step;

	private static final long serialVersionUID = 1L;

	private String currentJenkinsStepName = "[ServiceNow DevOps] snDevOpsUpdateChangeInfo, ";

	public DevOpsPipelineUpdateChangeInfoStepExecution(StepContext context, DevOpsPipelineUpdateChangeInfoStep step) {
		super(context);
		this.step = step;

	}

	@Override
	protected Boolean run() throws Exception {
		try {
			Run<?, ?> run = getContext().get(Run.class);
			String pronoun = run.getParent().getPronoun();
			boolean isPullRequestPipeline = pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString());
			DevOpsModel model = new DevOpsModel();
			DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());
			boolean pipelineTrack = model.checkIsTrackingCache(run.getParent(), run.getId());
			TaskListener listener = getContext().get(TaskListener.class);
			EnvVars envVars = getContext().get(EnvVars.class);
			DevOpsConfigurationEntry devopsConfig = GenericUtils.getDevOpsConfigurationEntryOrDefault(this.step.getConfigurationName());
			if (devopsConfig == null)
				return GenericUtils.handleConfigurationNotFound(this.step, jobProperties, listener, getContext(), false, true);
			String devopsConfigMessage = String.format("[ServiceNow DevOps] Using DevOps configuration %s", devopsConfig.getName());
			listener.getLogger().println(devopsConfigMessage);
			GenericUtils.printDebug(DevOpsPipelineUpdateChangeInfoStepExecution.class.getName(), "run", new String[] { "configurationName" }, new String[] { devopsConfig.getName() }, Level.FINE);

			DevOpsModel.DevOpsPipelineInfo pipelineInfo = model.checkIsTracking(run.getParent(), run.getId(), envVars.get("BRANCH_NAME"));
			DevOpsPipelineInfoConfig pipelineInfoConfig = GenericUtils.getPipelineInfoConfigFromConfigEntry(pipelineInfo, devopsConfig);
			if (!pipelineTrack || (pipelineInfoConfig != null && !pipelineInfoConfig.isTrack())) {
				listener.getLogger().println("[ServiceNow DevOps] Pipeline is not tracked");
				return true;
			}

			return updateChangeRequestDetails(run, isPullRequestPipeline, pipelineTrack, listener, devopsConfig);
		} catch (Exception e) {
			TaskListener listener = getContext().get(TaskListener.class);
			listener.getLogger().println("[ServiceNow DevOps] Error occurred while updating the change request,Exception: " + e.getMessage());
			throw e;
		}
	}

	private boolean updateChangeRequestDetails(Run<?, ?> run, boolean isPullRequestPipeline, boolean pipelineTrack, TaskListener listener, DevOpsConfigurationEntry devopsConfig) {
		String changeRequestNumber = this.step.getChangeRequestNumber();
		JSONObject changeRequestDetailsJSON;
		JSONObject params = new JSONObject();
		try {
			if (GenericUtils.isEmpty(changeRequestNumber)) {
				listener.getLogger().println(currentJenkinsStepName + " UPDATE Failed, Please provide a valid 'Change Request Number' to proceed.");
				return false;
			}
			if (GenericUtils.isEmpty(this.step.getChangeRequestDetails())) {
				listener.getLogger().println(currentJenkinsStepName + " UPDATE Failed, Please provide a valid 'Change Request Details' to proceed.");
				return false;
			} else {
				changeRequestDetailsJSON = JSONObject.fromObject(this.step.getChangeRequestDetails());
			}
			params.put("changeRequestNumber", changeRequestNumber);
			JSONObject responseJSON = null;

			if (!GenericUtils.isEmptyOrDefault(devopsConfig.getSecretCredentialId())) {
				Map<String, String> tokenDetails = new HashMap<String, String>();
				tokenDetails.put(DevOpsConstants.TOKEN_VALUE.toString(),
						DevOpsConfigurationEntry.getTokenText(devopsConfig.getSecretCredentialId()));
				tokenDetails.put(DevOpsConstants.TOOL_ID_ATTR.toString(), devopsConfig.getToolId());
				responseJSON = CommUtils.callV2Support(DevOpsConstants.REST_PUT_METHOD.toString(),
						devopsConfig.getChangeInfoUrl(), params, changeRequestDetailsJSON.toString(),
						DevOpsConfigurationEntry.getUser(devopsConfig.getCredentialsId()), DevOpsConfigurationEntry.getPwd(devopsConfig.getCredentialsId()), null, null, tokenDetails);
			} else {
				responseJSON = CommUtils.call(DevOpsConstants.REST_PUT_METHOD.toString(), devopsConfig.getChangeInfoUrl(), params, changeRequestDetailsJSON.toString(),
						DevOpsConfigurationEntry.getUser(devopsConfig.getCredentialsId()), DevOpsConfigurationEntry.getPwd(devopsConfig.getCredentialsId()), null, null);
			}

			String parsedResponse = GenericUtils.parseResponseResult(responseJSON, DevOpsConstants.COMMON_RESPONSE_STATUS.toString());
			if (parsedResponse != null && parsedResponse.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_SUCCESS.toString())) {
				listener.getLogger().println(currentJenkinsStepName + " Update Successful for 'Change Request Number' => " + changeRequestNumber + ", with given 'Change Request Details'");
				return true;
			} else {
				String errorMessage = GenericUtils.parseResponseResult(responseJSON, "");//To fetch Error message from response.
				listener.getLogger().println(currentJenkinsStepName + " Couldn't Update 'Change Request' with provided details, " + errorMessage + ". Please provide Valid inputs");
			}
		} catch (Exception exception) {
			listener.getLogger().println(currentJenkinsStepName + " Couldn't Update 'Change Request' with provided details, " + exception + ". Please provide Valid inputs");
		}
		return false;
	}
}
