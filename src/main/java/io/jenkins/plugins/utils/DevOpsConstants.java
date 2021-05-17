package io.jenkins.plugins.utils;

public enum DevOpsConstants {
	MAX_LOG_LINES,

	PIPELINE_PRONOUN,
	FREESTYLE_PRONOUN,
	FREESTYLE_MAVEN_PRONOUN,
	BITBUCKET_MULTI_BRANCH_PIPELINE_PRONOUN,

	TOOL_TYPE,
	TOOL_TYPE_ATTR,

	CALLBACK_URL_IDENTIFIER,
	CALLBACK_TOKEN_SEPARATOR,

	TRACKING_KEY_SEPARATOR,
	TRACKING_RESPONSE_ATTR,

	CHANGE_FUNCTION_NAME,
	CHANGE_DISPLAY_NAME,

	MAP_FUNCTION_NAME,
	MAP_DISPLAY_NAME,
	
	ARTIFACT_REGISTER_STEP_FUNCTION_NAME,
	ARTIFACT_REGISTER_STEP_DISPLAY_NAME,
	ARTIFACT_NAME_ATTR,
	ARTIFACT_PIPELINE_NAME,
	ARTIFACT_PROJECT_NAME,
	ARTIFACT_TASK_EXEC_NUM,
	ARTIFACT_STAGE_NAME,
	ARTIFACT_BRANCH_NAME,
	ARTIFACT_ARTIFACTS_ATTR,
	ARTIFACT_REGISTER_STATUS_ATTR,
	ARTIFACT_CURRENT_BUILD_INFO,
	
	ARTIFACT_PACKAGE_STEP_FUNCTION_NAME,
	ARTIFACT_PACKAGE_STEP_DISPLAY_NAME,
	ARTIFACT_PACKAGE_STEP_RESPONSE,

	COMMON_RESPONSE_CHANGE_CTRL,
	COMMON_RESPONSE_VALUE_UNKNOWN,
	COMMON_RESPONSE_VALUE_TRUE,
	COMMON_RESPONSE_VALUE_FALSE,
	COMMON_RESPONSE_SUCCESS,
	COMMON_RESPONSE_RESULT,
	COMMON_RESPONSE_STATUS,
	COMMON_RESULT_FAILURE,
	COMMON_RESULT_ERROR,
	COMMON_RESPONSE_DETAILS,
	COMMON_RESPONSE_ERRORS,
	CR_ATTRS,

	FAILURE_REASON_CONN_REFUSED,
	FAILURE_REASON_CONN_REFUSED_UI,
	FAILURE_REASON_USER_NOAUTH,
	FAILURE_REASON_USER_NOAUTH_UI,
	FAILURE_REASON_GENERIC_UI,
	FAILURE_REASON_PIPELINE_DETAILS_NOT_FOUND,
	FAILURE_REASON_INVALID_CONFIGURATION_UI,
	
	STEP_MAPPING_RESPONSE_ATTR,

	TOOL_ID_ATTR,
	ORCHESTRATION_TOOL_ID_ATTR,

	UPSTREAM_BUILD_URL_ATTR,
	LAST_BUILD_URL_ATTR,
	MESSAGE_ATTR,
	TRIGGER_TYPE_ATTR,
	USERNAME_ATTR,

	SCM_CHANGES_ATTR,
	SCM_TYPE_ATTR,
	SCM_LOG_ATTR,
	SCM_COMMIT_ID_ATTR,
	DECLARATIVE_STAGE,

	STEP_SYSID_ATTR,
	BUILD_URL_ATTR,
	PARENT_BUILD_URL_ATTR,
	IS_MULTI_BRANCH_ATTR,
	JOB_URL_ATTR,
	JOB_NAME_ATTR,
	JOB_PARENT_STAGE_NAME,
	JOB_PARENT_STAGE_URL,
	JOB_PARENT_STAGE_DATA,
	REST_GET_METHOD,
	REST_POST_METHOD,
	REST_PUT_METHOD,
	JOB_DETAILS_ATTR,
	JOB_STAGE_SEPARATOR,
	STAGE_RUN_IN_PROGRESS,
	STAGE_RUN_COMPLETED,
	STAGE_RUN_FAILURE,

	CALLBACK_URL_ATTR,
	CALLBACK_TRIGGERED_APPROVED,
	TEST_CONNECTION_ATTR,
	TEST_CONNECTION_RESPONSE_ATTR,
	CALLBACK_TRIGGERED_ATTR,
	CALLBACK_CANCELED_ATTR,
	CALLBACK_RESULT_ATTR,
	CALLBACK_RESULT_PENDING,
	CALLBACK_RESULT_SUCCESS,
	CALLBACK_RESULT_CANCELED,
	CALLBACK_RESULT_COMM_FAILURE,

	GIT_REMOTE_UPDATE_CMD,
	GIT_REV_LIST_CMD,
	GIT_SHOW_REF_CMD,
	GIT_SHOW_REF_ALL_CMD,
	GIT_REV_PARSE_CMD,
	GIT_BRANCH_CMD,
	GIT_STATUS_CMD,
	GIT_NAME_REV_CMD,
	SCM_BRANCH_NAME,

	GIT_PLUGIN_BRANCH_CLASS,
	GIT_PLUGIN_SCM_CLASS,

	FREESTYLE_STEP_CLASS,

	NOTIFICATION_STARTED,
	NOTIFICATION_COMPLETED,

	FREESTYLE_CALLBACK_URL_IDENTIFIER,
	MULTI_BRANCH_PROJECT_CLASS,
	PIPELINE_CALLBACK_URL_IDENTIFIER,

	TEST_INFO_RESPONSE;

	@Override
	public String toString() {
		switch (this) {
			case MAX_LOG_LINES: return "100";

			case PIPELINE_PRONOUN: return "Pipeline";
			case FREESTYLE_PRONOUN: return "Project";
			case FREESTYLE_MAVEN_PRONOUN: return "Maven project";
			case TOOL_TYPE: return "jenkins";
			case TOOL_TYPE_ATTR: return "toolType";
			case BITBUCKET_MULTI_BRANCH_PIPELINE_PRONOUN: return "Branch";

			case FREESTYLE_CALLBACK_URL_IDENTIFIER: return "freestyle";
			case PIPELINE_CALLBACK_URL_IDENTIFIER: return "pipeline";
			case CALLBACK_TOKEN_SEPARATOR: return "_";
			case CALLBACK_URL_IDENTIFIER: return "sn-devops";

			case TRACKING_KEY_SEPARATOR: return "_";
			case TRACKING_RESPONSE_ATTR: return "track";
			case TEST_INFO_RESPONSE: return "testInfo";

			case CHANGE_FUNCTION_NAME: return "snDevOpsChange";
			case CHANGE_DISPLAY_NAME: return "ServiceNow DevOps - Change Control step";
			
			case ARTIFACT_REGISTER_STEP_FUNCTION_NAME: return "snDevOpsArtifact";
			case ARTIFACT_REGISTER_STEP_DISPLAY_NAME: return "ServiceNow DevOps - Register Artifact step";
			case ARTIFACT_NAME_ATTR: return "name";
			case ARTIFACT_PIPELINE_NAME: return "pipelineName";
			case ARTIFACT_PROJECT_NAME: return "projectName";
			case ARTIFACT_TASK_EXEC_NUM: return "taskExecutionNumber"; 
			case ARTIFACT_STAGE_NAME: return "stageName"; 
			case ARTIFACT_BRANCH_NAME: return "branchName";
			case ARTIFACT_ARTIFACTS_ATTR: return "artifacts";
			case ARTIFACT_REGISTER_STATUS_ATTR: return "response";
			case ARTIFACT_CURRENT_BUILD_INFO: return "currentBuildInfo";
			
			case ARTIFACT_PACKAGE_STEP_FUNCTION_NAME: return "snDevOpsPackage";
			case ARTIFACT_PACKAGE_STEP_DISPLAY_NAME: return "ServiceNow DevOps - Register Package step";
			case ARTIFACT_PACKAGE_STEP_RESPONSE: return "responseCode";

			case MAP_FUNCTION_NAME: return "snDevOpsStep";
			case MAP_DISPLAY_NAME: return "ServiceNow DevOps - Mapping step";
			case STEP_MAPPING_RESPONSE_ATTR: return "stepValid";
			case STEP_SYSID_ATTR: return "stepSysId";

			case COMMON_RESPONSE_CHANGE_CTRL: return "changeControl";
			case COMMON_RESPONSE_VALUE_UNKNOWN: return "unknown";
			case COMMON_RESPONSE_VALUE_TRUE: return "true";
			case COMMON_RESPONSE_VALUE_FALSE: return "false";
			case COMMON_RESPONSE_SUCCESS: return "success";
			case COMMON_RESPONSE_RESULT: return "result";
			case COMMON_RESPONSE_STATUS: return "status";
			case COMMON_RESULT_FAILURE: return "failureReason";
			case COMMON_RESULT_ERROR: return "error";
			case COMMON_RESPONSE_DETAILS: return "details";
			case COMMON_RESPONSE_ERRORS: return "errors";
			case TOOL_ID_ATTR: return "toolId";
			case ORCHESTRATION_TOOL_ID_ATTR: return "orchestrationToolId";
			case IS_MULTI_BRANCH_ATTR: return "isMultiBranch";

			case FAILURE_REASON_CONN_REFUSED: return "IOException: Connection refused";
			case FAILURE_REASON_CONN_REFUSED_UI: return "ServiceNow instance is down or not reacheable";
			case FAILURE_REASON_INVALID_CONFIGURATION_UI: return "ServiceNow Configuration is not valid";
			case FAILURE_REASON_USER_NOAUTH: return "User Not Authenticated";
			case FAILURE_REASON_USER_NOAUTH_UI: return "ServiceNow credentials are invalid";
			case FAILURE_REASON_GENERIC_UI: return "An error has occurred";
			case FAILURE_REASON_PIPELINE_DETAILS_NOT_FOUND: return "Pipeline Details not found. Check logs for more detail.";

			case UPSTREAM_BUILD_URL_ATTR: return "upstreamTaskExecutionURL";
			case LAST_BUILD_URL_ATTR: return "lastTaskExecutionURL";
			case MESSAGE_ATTR: return "message";
			case TRIGGER_TYPE_ATTR: return "triggerType";
			case USERNAME_ATTR: return "userName";

			case SCM_COMMIT_ID_ATTR: return "scmCommitId";
			case SCM_CHANGES_ATTR: return "scmChanges";
			case SCM_TYPE_ATTR: return "scmType";
			case SCM_LOG_ATTR: return "scmLog";
			case DECLARATIVE_STAGE: return "Declarative:";

			case BUILD_URL_ATTR: return "taskExecutionURL";
			case PARENT_BUILD_URL_ATTR: return "parentTaskExecutionURL";
			case JOB_URL_ATTR: return "orchestrationTaskURL";
			case JOB_NAME_ATTR: return "orchestrationTaskName";
			case JOB_PARENT_STAGE_NAME: return "parentStageName";
			case JOB_PARENT_STAGE_URL: return "parentStageURL";
			case JOB_PARENT_STAGE_DATA: return "parentNode";
			case REST_GET_METHOD: return "GET";
			case REST_POST_METHOD: return "POST";
			case REST_PUT_METHOD: return "PUT";
			case JOB_DETAILS_ATTR: return "orchestrationTaskDetails";
			case CALLBACK_URL_ATTR: return "callbackURL";
			//case JOB_STAGE_SEPARATOR: return "_-_";
			case JOB_STAGE_SEPARATOR: return "#";
			case STAGE_RUN_IN_PROGRESS: return "IN-PROGRESS";
			case STAGE_RUN_COMPLETED: return "COMPLETED";
			case STAGE_RUN_FAILURE: return "FAILURE";

			case CALLBACK_TRIGGERED_APPROVED: return "Approved";
			case TEST_CONNECTION_ATTR: return "testConnection";
			case TEST_CONNECTION_RESPONSE_ATTR: return "connectionStatus";
			case CALLBACK_TRIGGERED_ATTR: return "change_decision";
			case CALLBACK_CANCELED_ATTR: return "callback_canceled";
			case CALLBACK_RESULT_ATTR: return "result";
			case CALLBACK_RESULT_PENDING: return "pending";
			case CALLBACK_RESULT_SUCCESS: return "succeeded";
			case CALLBACK_RESULT_CANCELED: return "canceled";
			case CALLBACK_RESULT_COMM_FAILURE: return "comm_failure";

			case GIT_REMOTE_UPDATE_CMD: return "git remote update";
			case GIT_REV_LIST_CMD: return "git rev-list %s...%s"; // "full" flag will also give commiter's address
			case GIT_SHOW_REF_CMD: return "git show-ref %s";
			case GIT_SHOW_REF_ALL_CMD: return "git show-ref";

			case GIT_BRANCH_CMD: return "git branch";
			case GIT_STATUS_CMD: return "git status";
			case GIT_NAME_REV_CMD: return "git name-rev --name-only %s";
			case GIT_REV_PARSE_CMD: return "git rev-parse %s";

			case GIT_PLUGIN_BRANCH_CLASS: return "hudson.plugins.git.BranchSpec";
			case GIT_PLUGIN_SCM_CLASS: return "hudson.plugins.git.GitSCM";

			case FREESTYLE_STEP_CLASS: return "io.jenkins.plugins.DevOpsFreestyleStep";

			case MULTI_BRANCH_PROJECT_CLASS: return "org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject";

			case NOTIFICATION_STARTED: return "STARTED";
			case NOTIFICATION_COMPLETED: return "COMPLETED";
			case SCM_BRANCH_NAME: return "branchName";

			case CR_ATTRS: return "changeRequestDetails";

			default: throw new IllegalArgumentException();
		}
	}
}
