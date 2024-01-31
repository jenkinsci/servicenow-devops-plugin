package io.jenkins.plugins.utils;

public enum DevOpsConstants {
	MAX_LOG_LINES,

	LOGGER_NAME,

	PIPELINE_PRONOUN,
	FREESTYLE_PRONOUN,
	FREESTYLE_MAVEN_PRONOUN,
	BITBUCKET_MULTI_BRANCH_PIPELINE_PRONOUN,
	PULL_REQUEST_PRONOUN,

	TOOL_TYPE,
	TOOL_TYPE_ATTR,

	CALLBACK_URL_IDENTIFIER,
	CALLBACK_TOKEN_SEPARATOR,

	TRACKING_KEY_SEPARATOR,
	TRACKING_RESPONSE_ATTR,

	CHANGE_FUNCTION_NAME,
	CHANGE_DISPLAY_NAME,
	CHANGE_REQUEST_INFO_FUNCTION_NAME,
	CHANGE_REQUEST_INFO_DISPLAY_NAME,

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


	CONFIG_UPLOAD_STEP_FUNCTION_NAME,
	CONFIG_UPLOAD_STEP_DISPLAY_NAME,
	CONFIG_STATUS_STEP_FUNCTION_NAME,
	CONFIG_STATUS_STEP_DISPLAY_NAME,
	CONFIG_PUBLISH_STEP_FUNCTION_NAME,
	CONFIG_PUBLISH_STEP_DISPLAY_NAME,
	CONFIG_REGISTER_PIPELINE_STEP_FUNCTION_NAME,
	CONFIG_REGISTER_PIPELINE_STEP_DISPLAY_NAME,
	CONFIG_VALIDATE_STEP_FUNCTION_NAME,
	CONFIG_VALIDATE_STEP_DISPLAY_NAME,
	CHANGE_REQUEST_UPDATE_INFO_FUNCTION_NAME,
	CHANGE_REQUEST_UPDATE_INFO_DISPLAY_NAME,
	CONFIG_PIPELINE_STEP_FUNCTION_NAME,
	CONFIG_PIPELINE_STEP_DISPLAY_NAME,

	CONFIG_SNAPSHOT_SYS_ID,
	CONFIG_ENVIRONMENT_TYPE,

	TABLE_API_QUERY,
	TABLE_API_FIELDS,
	TABLE_API_LIMIT,
	CONFIG_EXPORT_STEP_FUNCTION_NAME,
	CONFIG_EXPORT_STEP_DISPLAY_NAME,

	CONFIG_EXPORTER_NAME,
	CONFIG_EXPORTER_FORMAT,
	CONFIG_EXPORTER_ARGUMENTS,
	CONFIG_JSON_FORMAT,
	CONFIG_TEXT_FORMAT,
	CONFIG_RAW_FORMAT,
	CONFIG_SNAPSHOT_NAME,

	PIPELINE_JOB_NAME,
	PIPELINE_BRANCH_NAME,
	PIPELINE_BUILD_NUMBER,
	GET_SNAPSHOTS_STEP_FUNCTION_NAME,
	GET_SNAPSHOTS_STEP_DISPLAY_NAME,
	GET_PUBLISHED_BUILDS_DETAILS,
	STARTED_TIMESTAMP,

	CONFIG_APPLICATION_NAME,
	CONFIG_CHANGESET_NUMBER,
	CONFIG_NAME_PATH,
	CONFIG_DATA_FORMAT,
	CONFIG_AUTO_COMMIT,
	CONFIG_AUTO_DELETE,
	CONFIG_AUTO_VALIDATE,
	CONFIG_DEPLOYABLE_NAME,
	CONFIG_COLLECTION_NAME,
	CONFIG_FILE_CONTENT,
	CONFIG_COMPONENT_TYPE,
	CONFIG_DEPLOYABLE_TYPE,
	CONFIG_COLLECTION_TYPE,
	CONFIG_UPLOAD_ID,
	CONFIG_BUILD_NUMBER,
	CONFIG_APP_NAME,
	CHANGE_FOUND,
	CHANGE_ASSIGNMENT_GROUP,
	CHANGE_APPROVERS,
	CHANGE_STATE_DISPLAY_VALUE,
	CHANGE_DETAILS,
	CHANGE_START_DATE,
	CHANGE_END_DATE,
	COMMON_RESPONSE_CHANGE_CTRL,
	COMMON_RESPONSE_VALUE_UNKNOWN,
	COMMON_RESPONSE_VALUE_TRUE,
	COMMON_RESPONSE_VALUE_FALSE,
	COMMON_RESPONSE_SUCCESS,
	COMMON_RESPONSE_RESULT,
	COMMON_RESPONSE_EXPORTER_RESULT,
	COMMON_RESPONSE_OUTPUT,
	COMMON_RESPONSE_STATUS,
	COMMON_RESPONSE_STATE,
	COMMON_RESPONSE_NEW,
	COMMON_RESPONSE_IN_PROGRESS,
	COMMON_RESPONSE_READY,
	COMMON_RESPONSE_INITIALIZING,
	COMMON_RESPONSE_FAILURE,
	COMMON_RESULT_FAILURE,
	COMMON_RESULT_ERROR,
	COMMON_RESPONSE_NUMBER,
	COMMON_RESPONSE_MESSAGE,
	COMMON_RESPONSE_EXPORT_ID,
	COMMON_RESPONSE_DETAILS,
	COMMON_RESPONSE_ERRORS,
	COMMON_RESPONSE_COMPLETED,
	CR_ATTRS,
	COMMON_RESPONSE_OPEN,
	COMMON_RESPONSE_ERROR_MESSAGE,

	FAILURE_REASON_CONN_REFUSED,
	FAILURE_REASON_CONN_REFUSED_UI,
	FAILURE_REASON_USER_NOAUTH,
	FAILURE_REASON_USER_NOAUTH_UI,
	FAILURE_REASON_GENERIC_UI,
	FAILURE_REASON_PIPELINE_DETAILS_NOT_FOUND,
	FAILURE_REASON_INVALID_CONFIGURATION_UI,
	FAILURE_REASON_CONN_ISSUE,

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
	JOB_PARENT_STAGE_EXECUTION_URL,
	JOB_PARENT_STAGE_DATA,
	REST_GET_METHOD,
	REST_POST_METHOD,
	REST_PUT_METHOD,
	REST_DELETE_METHOD,
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
	CALLBACK_RESULT_COMMENTS,
	CHANGE_REQUEST_ID,

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
	PIPLINE_EXECUTION_URL,

	TEST_INFO_RESPONSE,

	SERVICENOW_PIPELINE_INFO_FILE_NAME,
	JOBS_PATH,
	JOBNAME_ATTR,
	STAGENAME_ATTR,
	PATH_SEPARATOR,
	MULTIBRANCH_PATH_SEPARATOR,
	PIPELINE_INFO_UPDATE_IDENTIFIER,
	PIPELINE_INFO_DELETE_IDENTIFIER,

	SN_DEVOPS_DISCOVER_API_BASE_URL,

	
	VERSION_V1,
	VERSION_V2,
	BASIC,
	SECRET_TOKEN,


	SECURITY_RESULT_STEP_DISPLAY_NAME,

	SECURITY_RESULT_STEP_FUNCTION_NAME,

	SEC_TOOL_STAGE_NAME,
	SEC_TOOL_BRANCH_NAME,
	SEC_TOOL_JOB_NAME,
	SEC_TOOL_JOB_FULL_NAME,
	SEC_TOOL_BUILD_NUMBER,
	SEC_TOOL_TASK_EXEC_URL,
	SEC_TOOL_TASK_URL,
	SEC_TOOL_JSON_ATTR_TOOL_ID,
	SEC_TOOL_JSON_ATTR_RESULT_META_DATA,
	SEC_TOOL_JSON_ATTR_TASK_INFO,

	SEC_TOOL_SCANNER,
	VERACODE_APP_ID,
	VERACODE_BUILD_ID,
	VERACODE,
	CHECKMARX_ONE,
	CHECKMARX_SCAN_ID,
	CHECKMARX_PROJECT_ID,
	CREATE_IBE,

	TOKEN_VALUE,
	SN_DEFAULT_KEY,
	BASIC_AUTHENCIATION_SUCCUSS,
	TOKEN_AUTHENTICATION_SUCCUSS,
	BASIC_AUTHENCIATION_FAILURE,
	TOKEN_AUTHENTICATION_FAILURE,
	SN_DEFUALT,
	CHECK_CONFIGURATION,
	JENKINS_CONFIGURATION_SAVE_FAILURE,
	JENKINS_CONFIGURATION_TEST_CONNECTION_FAILURE,
	JENKINS_CONFIGURATION_CRED_CREATION_FAILURE,
	JENKINS_CONFIGURATION_SUCCESS,
	JENKINS_DUMMY_EVENT_PRONOUN,
	ARTIFACT_REGISTER_REQUESTS,
	CREATE,
	UPDATE,
	FOUND,
	DESCRIPTION,
	LINK,
	STAGING_TYPE,
	CREATE_PACKAGE_ASSOCIATION;


	@Override
	public String toString() {
		switch (this) {
			case MAX_LOG_LINES: return "100";

			case LOGGER_NAME: return "ServiceNowDevops";

			case PIPELINE_PRONOUN: return "Pipeline";
			case FREESTYLE_PRONOUN: return "Project";
			case FREESTYLE_MAVEN_PRONOUN: return "Maven project";
			case PULL_REQUEST_PRONOUN : return  "Pull Request";
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
			case CHANGE_REQUEST_INFO_FUNCTION_NAME: return "snDevOpsGetChangeNumber";
			case CHANGE_REQUEST_INFO_DISPLAY_NAME: return "ServiceNow DevOps - get Change Number step";

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

			case CONFIG_UPLOAD_STEP_FUNCTION_NAME: return "snDevOpsConfigUpload";
			case CONFIG_UPLOAD_STEP_DISPLAY_NAME : return "ServiceNow DevOps - DevOps Configuration Upload";
			case CONFIG_STATUS_STEP_FUNCTION_NAME: return "snDevOpsConfigSnapshot";
			case CONFIG_STATUS_STEP_DISPLAY_NAME : return "ServiceNow DevOps - DevOps Configuration Status";
			case CONFIG_PUBLISH_STEP_FUNCTION_NAME: return "snDevOpsConfigPublish";
			case CONFIG_PUBLISH_STEP_DISPLAY_NAME: return "ServiceNow DevOps - DevOps Configuration Publish";
			case CONFIG_REGISTER_PIPELINE_STEP_FUNCTION_NAME: return "snDevOpsConfigRegisterPipeline";
			case CONFIG_REGISTER_PIPELINE_STEP_DISPLAY_NAME: return "ServiceNow DevOps - DevOps Configuration Register Pipeline";
			case CONFIG_VALIDATE_STEP_FUNCTION_NAME: return "snDevOpsConfigValidate";
			case CONFIG_VALIDATE_STEP_DISPLAY_NAME: return "ServiceNow DevOps - DevOps Configuration Validate";
			case CHANGE_REQUEST_UPDATE_INFO_FUNCTION_NAME: return "snDevOpsUpdateChangeInfo";
			case CHANGE_REQUEST_UPDATE_INFO_DISPLAY_NAME: return "ServiceNow DevOps - Update Change Request Info";
			case CONFIG_PIPELINE_STEP_FUNCTION_NAME: return "snDevOpsConfig";
			case CONFIG_PIPELINE_STEP_DISPLAY_NAME: return "ServiceNow DevOps - DevOps Configuration Pipeline";

			case CHANGE_FOUND: return "changeFound";
			case CHANGE_ASSIGNMENT_GROUP: return "changeAssignmentGroup";
			case CHANGE_APPROVERS: return "changeApprovers";
			case CHANGE_STATE_DISPLAY_VALUE: return "stateDisplayValue";
			case CHANGE_START_DATE: return "plannedStartDate";
			case CHANGE_END_DATE: return "plannedEndDate";
			case CHANGE_DETAILS: return "details";

			case CONFIG_SNAPSHOT_SYS_ID: return "sys_id";
			case CONFIG_ENVIRONMENT_TYPE: return "cdm_deployable_id.environment_type";

			case TABLE_API_QUERY: return "sysparm_query";
			case TABLE_API_FIELDS: return "sysparm_fields";
			case TABLE_API_LIMIT: return "sysparm_limit";
			case CONFIG_EXPORT_STEP_FUNCTION_NAME: return "snDevOpsConfigExport";
			case CONFIG_EXPORT_STEP_DISPLAY_NAME: return "ServiceNow DevOps - DevOps Configuration Export";
			case CONFIG_DEPLOYABLE_NAME: return "deployableName";
			case CONFIG_COLLECTION_NAME: return "collectionName";
			case CONFIG_APPLICATION_NAME: return "appName";
			case CONFIG_EXPORTER_NAME: return "exporterName";
			case CONFIG_EXPORTER_FORMAT: return "dataFormat";
			case CONFIG_EXPORTER_ARGUMENTS: return "args";
			case CONFIG_JSON_FORMAT: return "json";
			case CONFIG_TEXT_FORMAT: return "txt";
			case CONFIG_RAW_FORMAT: return "raw";
			case CONFIG_SNAPSHOT_NAME: return "snapshotName";

			case PIPELINE_JOB_NAME: return "JOB_NAME";
			case PIPELINE_BRANCH_NAME: return "BRANCH_NAME";
			case PIPELINE_BUILD_NUMBER: return "BUILD_NUMBER";
			case PIPLINE_EXECUTION_URL: return "pipelineExecutionUrl";

			case GET_SNAPSHOTS_STEP_FUNCTION_NAME: return "snDevOpsConfigGetSnapshots";
			case GET_SNAPSHOTS_STEP_DISPLAY_NAME : return "ServiceNow DevOps - Get latest and validated snapshots";

			case GET_PUBLISHED_BUILDS_DETAILS : return "getPublishedBuildsDetails";
			case STARTED_TIMESTAMP: return "startedTimeStamp";


			case CONFIG_CHANGESET_NUMBER: return "changesetNumber";
			case CONFIG_NAME_PATH: return "namePath";
			case CONFIG_DATA_FORMAT: return "dataFormat";
			case CONFIG_AUTO_COMMIT: return "autoCommit";
			case CONFIG_AUTO_DELETE: return "autoDelete";
			case CONFIG_AUTO_VALIDATE: return "autoValidate";
			case CONFIG_FILE_CONTENT: return "dataStream";
			case CONFIG_COMPONENT_TYPE: return "component";
			case CONFIG_DEPLOYABLE_TYPE: return "deployable";
			case CONFIG_COLLECTION_TYPE: return "collection";
			case CONFIG_UPLOAD_ID: return "upload_id";
			case CONFIG_BUILD_NUMBER: return "buildNumber";
			case CONFIG_APP_NAME: return "applicationName";

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
			case COMMON_RESPONSE_EXPORTER_RESULT: return "exporter_result";
			case COMMON_RESPONSE_OUTPUT: return "output";
			case COMMON_RESPONSE_STATUS: return "status";
			case COMMON_RESPONSE_STATE: return "state";
			case COMMON_RESPONSE_NEW: return "new";
			case COMMON_RESPONSE_IN_PROGRESS: return "in_progress";
			case COMMON_RESPONSE_READY: return "ready";
			case COMMON_RESPONSE_INITIALIZING: return "initializing";
			case COMMON_RESPONSE_FAILURE: return "failure";
			case COMMON_RESULT_FAILURE: return "failureReason";
			case COMMON_RESULT_ERROR: return "error";
			case COMMON_RESPONSE_DETAILS: return "details";
			case COMMON_RESPONSE_NUMBER: return "number";
			case COMMON_RESPONSE_MESSAGE: return "message";
			case COMMON_RESPONSE_EXPORT_ID: return "export_id";
			case COMMON_RESPONSE_ERRORS: return "errors";
			case COMMON_RESPONSE_OPEN: return "open";
			case COMMON_RESPONSE_COMPLETED: return "completed";
			case COMMON_RESPONSE_ERROR_MESSAGE: return "errorMessage";

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
			case FAILURE_REASON_CONN_ISSUE: return "No Response From the Server";

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
			case JOB_PARENT_STAGE_EXECUTION_URL: return "parentStageExecutionURL";
			case JOB_PARENT_STAGE_DATA: return "parentNode";
			case REST_GET_METHOD: return "GET";
			case REST_POST_METHOD: return "POST";
			case REST_PUT_METHOD: return "PUT";
			case REST_DELETE_METHOD: return "DELETE";
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
			case CALLBACK_RESULT_COMMENTS: return "changeComments";
			case CHANGE_REQUEST_ID: return "changeRequestId";

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

			case PATH_SEPARATOR: return GenericUtils.isWindows() ? "\\" : "/";
			case JOBS_PATH: return GenericUtils.isWindows() ? "\\jobs\\" : "/jobs/";
			case MULTIBRANCH_PATH_SEPARATOR: return GenericUtils.isWindows() ? "\\branches\\" : "/branches/";
			case JOBNAME_ATTR: return "jobName";
			case STAGENAME_ATTR: return "stageName";
			case SERVICENOW_PIPELINE_INFO_FILE_NAME: return "snPipelineInfo.json";
			case PIPELINE_INFO_UPDATE_IDENTIFIER: return "snupdate";
			case PIPELINE_INFO_DELETE_IDENTIFIER: return "sndelete";

			case SN_DEVOPS_DISCOVER_API_BASE_URL: return "sn-devops-discover-api";
			case VERSION_V1: return "v1";
			case VERSION_V2: return "v2";

			case SECURITY_RESULT_STEP_DISPLAY_NAME: return  "Servicenow Register Security Step";
			case SECURITY_RESULT_STEP_FUNCTION_NAME: return "snDevOpsSecurityResult";

			case SEC_TOOL_STAGE_NAME: return "stageName";
			case SEC_TOOL_BRANCH_NAME: return "branch";
			case SEC_TOOL_JOB_NAME: return "pipelineName";
			case SEC_TOOL_JOB_FULL_NAME: return "pipelineFullName";
			case SEC_TOOL_BUILD_NUMBER: return "buildNumber";
			case SEC_TOOL_TASK_EXEC_URL: return "taskExecutionUrl";
			case SEC_TOOL_TASK_URL: return "taskUrl";
			case SEC_TOOL_JSON_ATTR_TOOL_ID: return "securityToolId";
			case SEC_TOOL_JSON_ATTR_RESULT_META_DATA: return "securityResultAttributes";
			case SEC_TOOL_JSON_ATTR_TASK_INFO: return "pipelineInfo";

			case SEC_TOOL_SCANNER: return "scanner";
			case VERACODE_APP_ID: return "applicationId";
			case VERACODE_BUILD_ID: return "buildId";
			case VERACODE: return "Veracode";
			case CHECKMARX_ONE: return "Checkmarx One";
			case CHECKMARX_SCAN_ID: return "scanId";
			case CHECKMARX_PROJECT_ID: return "projectId";
			case CREATE_IBE: return "CREATE_IBE";

			case BASIC: return "Basic";
			case SECRET_TOKEN: return "Secret Token";
			case TOKEN_VALUE: return "token";
			case BASIC_AUTHENCIATION_SUCCUSS:
				return "Connection using 'Credentials' is successful!";
			case BASIC_AUTHENCIATION_FAILURE:
				return "Connection using 'Credentials' failed!";
			case TOKEN_AUTHENTICATION_FAILURE:
				return "Connection using 'Secret Credentials' failed!";
			case TOKEN_AUTHENTICATION_SUCCUSS:
				return "Connection using 'Secret Credentials' is successful!";
			case SN_DEFUALT:
				return "SNDefualt";
			case SN_DEFAULT_KEY: return "- none -";
			case CHECK_CONFIGURATION: return "checkconfiguration";
			case JENKINS_CONFIGURATION_SAVE_FAILURE: return "Jenkins configuration failed while saving";
			case JENKINS_CONFIGURATION_TEST_CONNECTION_FAILURE: return "Jenkins configuration failed due to test connection failure";
			case JENKINS_CONFIGURATION_CRED_CREATION_FAILURE: return "Jenkins configuration failed due to secret credential creation issue";
			case JENKINS_CONFIGURATION_SUCCESS: return "Jenkins configured successfully";
			case JENKINS_DUMMY_EVENT_PRONOUN: return "SnDevopsDummyEventPronoun";
			case ARTIFACT_REGISTER_REQUESTS: return "artifact_register_requests";
			case CREATE: return "create";
			case UPDATE: return "update";
			case FOUND: return "found";
			case DESCRIPTION: return "description";
			case LINK: return "link";
			case STAGING_TYPE: return "stagingType";
			case CREATE_PACKAGE_ASSOCIATION: return "create_package_association";

			default: throw new IllegalArgumentException();
		}
	}
}