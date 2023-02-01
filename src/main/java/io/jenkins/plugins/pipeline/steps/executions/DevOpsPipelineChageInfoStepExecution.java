package io.jenkins.plugins.pipeline.steps.executions;

import hudson.model.TaskListener;
import io.jenkins.plugins.pipeline.steps.DevOpsPipelineChangeInfoStep;
import io.jenkins.plugins.utils.CommUtils;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.model.Run;
import io.jenkins.plugins.DevOpsRunListener;
import io.jenkins.plugins.DevOpsRunStatusAction;
import io.jenkins.plugins.config.DevOpsConfiguration;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.model.DevOpsPipelineGraph;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;

public class DevOpsPipelineChageInfoStepExecution extends SynchronousStepExecution<String> {

	private static final long serialVersionUID = 1L;
	private String currentJenkinsStepName = "[ServiceNow DevOps] snDevOpsGetChangeNumber, ";

	private DevOpsPipelineChangeInfoStep step;

	public DevOpsPipelineChageInfoStepExecution(StepContext context, DevOpsPipelineChangeInfoStep step) {
		super(context);
		this.step = step;
	}

	public DevOpsPipelineChangeInfoStep getStep() {
		return step;
	}

	@Override
	protected String run() throws Exception {
		Run<?, ?> run = getContext().get(Run.class);
		String pronoun = run.getParent().getPronoun();
		boolean isPullRequestPipeline = pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString());
		DevOpsModel model = new DevOpsModel();
		boolean pipelineTrack = model.checkIsTrackingCache(run.getParent(), run.getId());
		TaskListener listener = getContext().get(TaskListener.class);
		DevOpsConfiguration devopsConfig = DevOpsConfiguration.get();
		return getChangeRequestNumber(run, isPullRequestPipeline, pipelineTrack, listener, devopsConfig, this.getStep().getChangeDetails());

	}

	private String getChangeRequestNumber(Run<?, ?> run, boolean isPullRequestPipeline, boolean pipelineTrack, TaskListener listener, DevOpsConfiguration devopsConfig, String changeDetails) {
		listener.getLogger().println(currentJenkinsStepName+" ChangeDetails => "+changeDetails);
		String changeRequestNumber;
		if (GenericUtils.isNotEmpty(changeDetails)) {
			changeRequestNumber = getChangeRequestNumberFromChangeDetails(run, isPullRequestPipeline, pipelineTrack, listener, devopsConfig, changeDetails);
		}else {
			listener.getLogger().println(currentJenkinsStepName+" No changeDetails provided, checking for change in current stage");
			changeRequestNumber = getChangeRequestNumberFromStageName(run, isPullRequestPipeline, pipelineTrack, listener, devopsConfig, "");
		}
		if(GenericUtils.isNotEmpty(changeRequestNumber)){
			listener.getLogger().println(currentJenkinsStepName+" 'Change Request Number' => "+ changeRequestNumber);
		}
		return changeRequestNumber;
	}

	private String getChangeRequestNumberFromChangeDetails(Run<?, ?> run, boolean isPullRequestPipeline, boolean pipelineTrack, TaskListener listener, DevOpsConfiguration devopsConfig, String changeDetails) {
		JSONObject changeDetailsJSON = JSONObject.fromObject(changeDetails);
		JSONObject params = new JSONObject();

		String buildNumber = (String) changeDetailsJSON.get("build_number");
		String stageName = (String) changeDetailsJSON.get("stage_name");
		String pipelineName = (String) changeDetailsJSON.get("pipeline_name");
		//Change Details validation
		if(GenericUtils.isEmpty(buildNumber) || GenericUtils.isEmpty(pipelineName) || GenericUtils.isEmpty(stageName)) {
			if(GenericUtils.isEmpty(buildNumber) && GenericUtils.isNotEmpty(stageName)) {//To Fetch ChangeRequestNumber from same pipeline but mentioned stage name
				return getChangeRequestNumberFromStageName(run, isPullRequestPipeline, pipelineTrack, listener, devopsConfig, stageName);
			}

			listener.getLogger().print(currentJenkinsStepName+" Couldn't get 'Change Request Number'. Please provide");

			if(GenericUtils.isEmpty(pipelineName))
				listener.getLogger().print(" Pipeline Name,");

			if(GenericUtils.isEmpty(buildNumber))
				listener.getLogger().print(" Build Number,");

			if(GenericUtils.isEmpty(stageName))
				listener.getLogger().print(" Stage Name");

			listener.getLogger().println(".");

			return null;
		}
		String branchName = (String) changeDetailsJSON.get("branch_name");
		params.put(DevOpsConstants.CONFIG_BUILD_NUMBER.toString(), buildNumber);
		params.put(DevOpsConstants.ARTIFACT_STAGE_NAME.toString(), stageName);
		params.put(DevOpsConstants.ARTIFACT_PIPELINE_NAME.toString(), pipelineName);
		params.put(DevOpsConstants.SCM_BRANCH_NAME.toString(), branchName);
		params.put(DevOpsConstants.TOOL_ID_ATTR.toString(), devopsConfig.getToolId());
		JSONObject responseJSON = CommUtils.call(DevOpsConstants.REST_GET_METHOD.toString(), devopsConfig.getChangeInfoUrl(), params, null,
				devopsConfig.getUser(), devopsConfig.getPwd(), null, null);
		return parseResponseResult(listener, responseJSON);
	}

	private String parseResponseResult(TaskListener listener, JSONObject responseJSON) {
		if (null!= responseJSON) {
			if (responseJSON.containsKey(DevOpsConstants.COMMON_RESPONSE_RESULT.toString())) {
				JSONObject resultJSON = responseJSON.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
				if (resultJSON.containsKey(DevOpsConstants.COMMON_RESPONSE_NUMBER.toString())) {
					return resultJSON.getString(DevOpsConstants.COMMON_RESPONSE_NUMBER.toString());
				} else if (resultJSON.containsKey(DevOpsConstants.COMMON_RESPONSE_ERROR_MESSAGE.toString())) {
					listener.getLogger().println(currentJenkinsStepName+" Couldn't get 'Change Request Number', " + resultJSON.getString(DevOpsConstants.COMMON_RESPONSE_ERROR_MESSAGE.toString()));
				}
			} else if (responseJSON.containsKey(DevOpsConstants.COMMON_RESULT_ERROR.toString())) {
				listener.getLogger().println(currentJenkinsStepName+" Couldn't get 'Change Request Number', " + responseJSON.getJSONObject(DevOpsConstants.COMMON_RESULT_ERROR.toString()).getString(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString()));
			}
		}
		return null;
	}

	/**
	 * Returns ChangeRequestNumber from currentStage of the Pipeline if 'stageName' is empty, else returns ChangeRequestNumber pointing to the appropriate Stage
	 *
	 * @param run
	 * @param isPullRequestPipeline
	 * @param pipelineTrack
	 * @param listener
	 * @param devopsConfig
	 * @param stageName
	 * @return
	 */
	private String getChangeRequestNumberFromStageName(Run<?, ?> run, boolean isPullRequestPipeline, boolean pipelineTrack, TaskListener listener, DevOpsConfiguration devopsConfig, String stageName) {
		if (pipelineTrack && ((isPullRequestPipeline && devopsConfig.isTrackPullRequestPipelinesCheck()) || (!isPullRequestPipeline))) {
			DevOpsRunStatusAction runStatusAction = run.getAction(DevOpsRunStatusAction.class);
			if(!stageName.isEmpty()){
				return runStatusAction.changeRequestInfo.get(stageName);
			}
			if (runStatusAction != null) {
				DevOpsPipelineGraph pipelineGraph = runStatusAction.getPipelineGraph();
				if (pipelineGraph != null) {
					String currentStageName = DevOpsRunListener.DevOpsStageListener.getCurrentStageName(getContext(), pipelineGraph);
					if (currentStageName != null) {
						return runStatusAction.changeRequestInfo.get(currentStageName);
					}
				}
			}
		}else{
			listener.getLogger().println(currentJenkinsStepName+" Please enable 'Pipeline Track' to fetch 'Change Request Number' using 'Stage Name'.");
		}
		return null;

	}
}
