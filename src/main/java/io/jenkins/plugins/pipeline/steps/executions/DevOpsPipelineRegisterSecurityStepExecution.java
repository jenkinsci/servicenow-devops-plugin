package io.jenkins.plugins.pipeline.steps.executions;

import static io.jenkins.plugins.DevOpsRunListener.DevOpsStageListener.getCurrentStageName;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.DevOpsRunStatusAction;
import io.jenkins.plugins.actions.RegisterSecurityAction;
import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsPipelineRegisterSecurityStep;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;


public class DevOpsPipelineRegisterSecurityStepExecution extends SynchronousStepExecution<String> {
	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Logger.getLogger(DevOpsPipelineRegisterSecurityStepExecution.class.getName());
	private DevOpsPipelineRegisterSecurityStep step;

	protected DevOpsPipelineRegisterSecurityStepExecution(@NonNull StepContext context) {
		super(context);
	}

	public DevOpsPipelineRegisterSecurityStepExecution(@Nonnull StepContext context, DevOpsPipelineRegisterSecurityStep step) {
		super(context);
		this.step = step;
	}

	public DevOpsPipelineRegisterSecurityStep getStep() {
		return step;
	}

	public void setStep(DevOpsPipelineRegisterSecurityStep step) {
		this.step = step;
	}


	@Override
	protected String run() throws Exception {
		try {
			EnvVars envVars = getContext().get(EnvVars.class);
			Run<?, ?> run = getContext().get(Run.class);
			TaskListener listener = getContext().get(TaskListener.class);
			String attributes = this.step.getSecurityResultAttributes();
			JSONObject registerResponse;
			try {
				DevOpsModel model = new DevOpsModel();
				boolean pipelineTrack = model.checkIsTrackingCache(run.getParent(), run.getId());
				if (!pipelineTrack) {
					LOGGER.log(Level.INFO, "Pipeline not tracked");
					return null;
				}

				JSONObject pipelineInfo = new JSONObject();
				String stageName = envVars.get("STAGE_NAME");

				DevOpsRunStatusAction action = run.getAction(DevOpsRunStatusAction.class);
				if (action != null) {
					stageName = getCurrentStageName(getContext(), action.getPipelineGraph());
				}

				pipelineInfo.put(DevOpsConstants.SEC_TOOL_BUILD_NUMBER.toString(), envVars.get("BUILD_NUMBER"));
				pipelineInfo.put(DevOpsConstants.SEC_TOOL_STAGE_NAME.toString(), stageName);
				String jobName = envVars.get("JOB_BASE_NAME");
				String jobFullName = envVars.get("JOB_NAME");
				if (jobFullName == null || jobFullName.equals("")) {
					jobFullName = jobName;
				}
				pipelineInfo.put(DevOpsConstants.SEC_TOOL_JOB_FULL_NAME.toString(), jobFullName);
				pipelineInfo.put(DevOpsConstants.SEC_TOOL_JOB_NAME.toString(), jobName);
				if (null != envVars.get("BRANCH_NAME")) {
					pipelineInfo.put(DevOpsConstants.SEC_TOOL_BRANCH_NAME.toString(), envVars.get("BRANCH_NAME"));
				}

				JSONObject securityParams = null;

				securityParams = JSONObject.fromObject(attributes);
				JSONObject payload = new JSONObject();

				payload.put(DevOpsConstants.SEC_TOOL_JSON_ATTR_RESULT_META_DATA.toString(), securityParams);
				payload.put(DevOpsConstants.SEC_TOOL_JSON_ATTR_TASK_INFO.toString(), pipelineInfo);

				RegisterSecurityAction rs = new RegisterSecurityAction(securityParams.toString());
				run.addAction(rs);

				registerResponse = model.registerSecurityResult(payload);

			} catch (JSONException jsonException) {
				return handleException("securityResultAttributes should be in stringified JSON format : " + jsonException.getMessage());
			} catch (Exception exception) {
				return handleException("Error while registering security result to ServiceNow DevOps : " + exception.getMessage());
			}

			JSONObject resultStatus = registerResponse.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
			String status = "";
			try {
				status = resultStatus.getString(DevOpsConstants.COMMON_RESPONSE_STATUS.toString());
			} catch (JSONException j) {
				return handleException("Register step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
			}

			String errorMessage = "";
			if (status.equalsIgnoreCase("Failure")) {
				try {
					errorMessage = resultStatus.getString(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
				} catch (JSONException j) {
					return handleException(
							"Register step failed : " + DevOpsConstants.FAILURE_REASON_CONN_ISSUE.toString());
				}
				return handleException(errorMessage);
			}
			GenericUtils.printConsoleLog(listener, DevOpsConstants.SECURITY_RESULT_STEP_DISPLAY_NAME.toString()
					+ " - Security step information is successfully sent to ServiceNow");
			return DevOpsConstants.COMMON_RESPONSE_SUCCESS.toString();
		} catch (Exception e) {
			TaskListener listener = getContext().get(TaskListener.class);
			listener.getLogger().println("[ServiceNow DevOps] Error occurred while registering the Security scan results,Exception: " + e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	private String handleException(String exceptionMessage) throws Exception {
		Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);
		DevOpsModel model = new DevOpsModel();
		DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());

		if (!jobProperties.isIgnoreSNErrors()) {
			run.setResult(Result.FAILURE);
			throw new AbortException(
					DevOpsConstants.SECURITY_RESULT_STEP_FUNCTION_NAME.toString() + " - " + exceptionMessage);
		}
		GenericUtils.printConsoleLog(listener, DevOpsConstants.SECURITY_RESULT_STEP_FUNCTION_NAME.toString()
				+ " - " + exceptionMessage + " - Ignoring SN Errors");
		return DevOpsConstants.COMMON_RESULT_ERROR.name();
	}
}
