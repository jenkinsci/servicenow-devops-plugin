package io.jenkins.plugins.pipeline.steps.executions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.model.CDMSnapshot;
import io.jenkins.plugins.model.DevOpsModel;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.AbortException;
import hudson.model.Run;

import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jenkins.plugins.pipeline.steps.DevOpsConfigStep;
import io.jenkins.plugins.pipeline.steps.DevOpsConfigUploadStep;
import io.jenkins.plugins.pipeline.steps.DevOpsConfigGetSnapshotsStep;
import io.jenkins.plugins.pipeline.steps.DevOpsConfigRegisterPipelineStep;

public class DevOpsConfigStepExecution extends SynchronousNonBlockingStepExecution<String> {
	private static final long serialVersionUID = 1L;
	private DevOpsConfigStep step;

	StepContext uploadContext = null;
	StepContext getSnapshotContext = null;
	StepContext registerContext = null;
	String changesetId;
	String snapshotObj;

	private ObjectMapper mapper = new ObjectMapper();

	public DevOpsConfigStepExecution(StepContext context, DevOpsConfigStep step) {
		super(context);
		this.step = step;
		uploadContext = context;
		getSnapshotContext = context;
		registerContext = context;
	}

	@Override
	protected String run() throws Exception {
		Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);
		DevOpsModel model = new DevOpsModel();
		DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());

		GenericUtils.printConsoleLog(listener, "Config step execution starts");
		try {
			boolean publishFlag = false;
			boolean valiateFlag = false;
			boolean commitFlag = false;

			if (this.step.getAutoPublish() == null || this.step.getAutoPublish().equalsIgnoreCase("true"))
				publishFlag = true;

			if (this.step.getAutoValidate() == null || this.step.getAutoValidate().equalsIgnoreCase("true"))
				valiateFlag = true;

			if (this.step.getAutoCommit() == null || this.step.getAutoCommit().equalsIgnoreCase("true"))
				commitFlag = true;

			DevOpsConfigUploadStep uploadStep = new DevOpsConfigUploadStep(this.step.getApplicationName(),
					this.step.getTarget(), this.step.getNamePath(), this.step.getConfigFile(), commitFlag, valiateFlag,
					this.step.getDataFormat(), publishFlag);
			uploadStep.setChangesetNumber(this.step.getChangesetNumber());
			uploadStep.setDeployableName(this.step.getDeployableName());
			uploadStep.setCollectionName(this.step.getCollectionName());
			uploadStep.setConvertPath(this.step.getConvertPath());
			uploadStep.setMarkFailed(this.step.getMarkFailed());
			uploadStep.setShowResults(this.step.getShowResults());

			DevOpsConfigUploadStepExecution uploadExec = new DevOpsConfigUploadStepExecution(uploadContext, uploadStep);
			changesetId = uploadExec.run();

			if (!changesetId.isEmpty()) {
				if (!commitFlag)
					throw new AbortException(
							"If you want to validate and publish your configuration data changes, please ensure autoCommit, autoValidate, and autoPublish flags are all set to 'true' before running your pipeline");
				else {
					if (!valiateFlag) {
						DevOpsConfigGetSnapshotsStep getStepForStatus = new DevOpsConfigGetSnapshotsStep(
								this.step.getApplicationName(), null, changesetId);
						DevOpsConfigGetSnapshotsStepExecution getExecForStatus = new DevOpsConfigGetSnapshotsStepExecution(
								getSnapshotContext, getStepForStatus);
						List<CDMSnapshot> snapshotStatusList = new ArrayList<>();

						JSONObject changesetDetails = model.getChangesetId(changesetId);
						JSONArray changesetResult = changesetDetails.getJSONArray("result");
						JSONObject changesetObj = changesetResult.getJSONObject(0);
						String changesetSysId = changesetObj.getString("sys_id");

						List<String> deployNames = getExecForStatus.getDeployableNames(model, changesetSysId, listener);
						if (deployNames.size() == 0) {
							throw new AbortException("No deployables got impacted");
						} else {
							JSONObject appDetails = model.checkForValidApp(this.step.getApplicationName());
							JSONArray appResult = appDetails.getJSONArray("result");
							String appId = appResult.getJSONObject(0).getString("sys_id");

							JSONObject updatedSnapshotObj = model.snapShotExists(appId, deployNames, changesetId);
							if (updatedSnapshotObj != null) {
								JSONArray snapshotArray = updatedSnapshotObj.getJSONArray("result");
								snapshotStatusList = getExecForStatus.getSnapshotList(snapshotArray);
							}
							getExecForStatus.addDeployableDetails(snapshotStatusList);

							return mapper.writeValueAsString(snapshotStatusList);
						}
					} else {
						GenericUtils.printConsoleLog(listener, "--------------------------------------");

						DevOpsConfigGetSnapshotsStep getStep = new DevOpsConfigGetSnapshotsStep(
								this.step.getApplicationName(), null, changesetId);
						getStep.setMarkFailed(this.step.getMarkFailed());
						getStep.setShowResults(this.step.getShowResults());
						getStep.setOutputFormat(this.step.getTestResultFormat());
						getStep.setIsValidated(this.step.getIsValidated());

						DevOpsConfigGetSnapshotsStepExecution getExec = new DevOpsConfigGetSnapshotsStepExecution(
								getSnapshotContext, getStep);
						snapshotObj = getExec.run();

						GenericUtils.printConsoleLog(listener, "--------------------------------------");

						List<CDMSnapshot> snapshotList = mapper.readValue(snapshotObj,
								new TypeReference<List<CDMSnapshot>>() {
								});
						List<String> deploybaleNames = new ArrayList<>();
						for (CDMSnapshot cdmSnapshot : snapshotList) {
							deploybaleNames.add(cdmSnapshot.getDeployableName());
						}

						GenericUtils.printConsoleLog(listener, "--------------------------------------");

						DevOpsConfigRegisterPipelineStep registerStep = new DevOpsConfigRegisterPipelineStep();
						registerStep.setApplicationName(this.step.getApplicationName());
						registerStep.setChangesetNumber(changesetId);

						DevOpsConfigRegisterPipelineStepExecution registerExec = new DevOpsConfigRegisterPipelineStepExecution(
								registerContext, registerStep);
						registerExec.run();

						Thread.sleep(3000);

						// Fetching latest snapshot status
						List<CDMSnapshot> updatedSnapshotList = new ArrayList<>();
						JSONObject updatedSnapshotObj = model.snapShotExists(getExec.appSysId, deploybaleNames,
								changesetId);
						if (updatedSnapshotObj != null) {
							JSONArray snapshotArray = updatedSnapshotObj.getJSONArray("result");
							updatedSnapshotList = getExec.getSnapshotList(snapshotArray);
						}
						getExec.addDeployableDetails(updatedSnapshotList);
						for (CDMSnapshot cdmSnapshot : updatedSnapshotList) {
							JSONObject validationResults = model.getValidationResults(cdmSnapshot.getSys_id(), "", "");
							if (validationResults != null) {
								JSONArray validationArray = validationResults.getJSONArray("result");
								if (!validationArray.isEmpty()) {
									cdmSnapshot.setValidationResults(validationResults);
								}
							}
						}
						List<String> snapshotsFailed = new ArrayList<>();
						List<String> snapshotExecError = new ArrayList<>();
						for (CDMSnapshot cdmSnapshot : updatedSnapshotList) {
							if (cdmSnapshot.getValidation().equals("failed"))
								snapshotsFailed.add(cdmSnapshot.getName());
							else if (cdmSnapshot.getValidation().equals("execution_error"))
								snapshotExecError.add(cdmSnapshot.getName());
						}

						if (snapshotsFailed.size() != 0 || snapshotExecError.size() != 0) {
							if (!this.step.getMarkFailed() && snapshotExecError.size() != 0)
								GenericUtils.printConsoleLog(listener,
										"Upload succeeded, but validation failed to execute on one or more snapshots. Snapshots where validation executed successfully and have passed have been published."
												+ "Please check errors section for more details.");
							else
								GenericUtils.printConsoleLog(listener,
										"Not all snapshots passed validation. If this was not expected, make sure to review validation results before proceeding."
												+ "Alternatively, you could use the 'markFailed' argument next time to fail the action in the event a snapshot fails validation.");

							for (String snapshotName : snapshotsFailed) {
								GenericUtils.printConsoleLog(listener, snapshotName
										+ " failed validation. Please review validation results file for more information.");
							}

							for (String snapshotName : snapshotExecError) {
								GenericUtils.printConsoleLog(listener, snapshotName
										+ " failed to execute validation. Please retry validation before proceeding.");
							}
							if (this.step.getMarkFailed())
								throw new AbortException(
										"Stopping pipeline execution since one or more snapshots failed in validation");
						}
						return mapper.writeValueAsString(updatedSnapshotList);
					}
				}
			} else {
				throw new AbortException("Changeset not found, stopping pipeline execution");
			}

		} catch (IOException | InterruptedException | JSONException | IndexOutOfBoundsException
				| ParserConfigurationException | TransformerException | NullPointerException e) {
			if (!jobProperties.isIgnoreSNErrors() || this.step.getMarkFailed()) {
				run.setResult(Result.FAILURE);
				throw e;
			}
			GenericUtils.printConsoleLog(listener, e.getMessage());
			GenericUtils.printConsoleLog(listener, "Step execution failed, continuing the pipeline");
			return "[]";
		}
	}
}
