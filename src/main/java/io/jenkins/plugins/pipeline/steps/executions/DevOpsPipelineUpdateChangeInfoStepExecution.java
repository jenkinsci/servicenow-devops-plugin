package io.jenkins.plugins.pipeline.steps.executions;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.DevOpsRunStatusAction;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsPipelineUpdateChangeInfoStep;
import io.jenkins.plugins.utils.CommUtils;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONObject;
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
		Run<?, ?> run = getContext().get(Run.class);
		String pronoun = run.getParent().getPronoun();
		boolean isPullRequestPipeline = pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString());
		DevOpsModel model = new DevOpsModel();
		boolean pipelineTrack = model.checkIsTrackingCache(run.getParent(), run.getId());
		DevOpsConfiguration devopsConfig = DevOpsConfiguration.get();
		TaskListener listener = getContext().get(TaskListener.class);
		return updateChangeRequestDetails(run,isPullRequestPipeline,pipelineTrack,listener,devopsConfig);

	}

	private boolean updateChangeRequestDetails(Run<?, ?> run, boolean isPullRequestPipeline, boolean pipelineTrack, TaskListener listener, DevOpsConfiguration devopsConfig){
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
			JSONObject responseJSON = CommUtils.call(DevOpsConstants.REST_PUT_METHOD.toString(), devopsConfig.getChangeInfoUrl(), params, changeRequestDetailsJSON.toString(),
					devopsConfig.getUser(), devopsConfig.getPwd(), null, null);
			String parsedResponse = GenericUtils.parseResponseResult(responseJSON, DevOpsConstants.COMMON_RESPONSE_STATUS.toString());
			if (parsedResponse!=null && parsedResponse.equalsIgnoreCase(DevOpsConstants.COMMON_RESPONSE_SUCCESS.toString())) {
				listener.getLogger().println(currentJenkinsStepName + " Update Successful for 'Change Request Number' => " + changeRequestNumber + ", with given 'Change Request Details'");
				return true;
			} else {
				String errorMessage = GenericUtils.parseResponseResult(responseJSON, "");//To fetch Error message from response.
				listener.getLogger().println(currentJenkinsStepName + " Couldn't Update 'Change Request' with provided details, " + errorMessage +". Please provide Valid inputs");
			}
		}catch(Exception exception){
			listener.getLogger().println(currentJenkinsStepName + " Couldn't Update 'Change Request' with provided details, " + exception +". Please provide Valid inputs");
		}
		return false;
	}
}
