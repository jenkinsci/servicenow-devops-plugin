package io.jenkins.plugins.pipeline.steps.executions;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.evanlennick.retry4j.CallExecutorBuilder;
import com.evanlennick.retry4j.config.RetryConfig;
import com.evanlennick.retry4j.config.RetryConfigBuilder;
import com.evanlennick.retry4j.exception.RetriesExhaustedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import hudson.AbortException;
import hudson.model.Result;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.model.CDMSnapshot;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.DevOpsConfigGetSnapshotsStep;
import io.jenkins.plugins.utils.GenericUtils;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import io.jenkins.plugins.utils.DevOpsConstants;

public class DevOpsConfigGetSnapshotsStepExecution extends SynchronousStepExecution<String> {

	private static final long serialVersionUID = 1L;
	private static final String RETRY = "retry";
	private static final long durationBetweenRetries = 200l;
	private static final int maxNumberOfRetries = 25;

	private DevOpsConfigGetSnapshotsStep step;
	private ObjectMapper mapper = new ObjectMapper();

	public DevOpsConfigGetSnapshotsStepExecution(StepContext context, DevOpsConfigGetSnapshotsStep step) {
		super(context);
		this.step = step;
	}

	@Override
	protected String run() throws Exception {
		Run<?, ?> run = getContext().get(Run.class);
		TaskListener listener = getContext().get(TaskListener.class);
		DevOpsModel model = new DevOpsModel();
		DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());
		List<CDMSnapshot> result = new ArrayList<>();
		try {
			GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - ConfigStatus Step Execution starts");
			if (validateStepInputs()) {
				List<String> deployableNames = getDeployableNames(model, step.getApplicationName());
				GenericUtils.printConsoleLog(listener,
						"snDevOpsConfigGetSnapshots - Deployable Names : " + deployableNames);

				if (StringUtils.isEmpty(step.getChangeSetId())) {
					result = processSnapshotsByPollingValidationStatus(step.getApplicationName(), deployableNames);
				} else {
					result = processSnapshotsByPollingCreationAndValidationStatus(step.getApplicationName(),
							deployableNames, step.getChangeSetId());
				}
				run.setResult(Result.SUCCESS);
			} else {
				if (!jobProperties.isIgnoreSNErrors()) {
					run.setResult(Result.FAILURE);
					throw new AbortException("snDevOpsConfigGetSnapshots  - Missing Parameters.");
				} else {
					GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Validation ofStep Inputs Failed");
				}
			}
		} catch (IOException | InterruptedException | JSONException e) {

			if ((e instanceof AbortException) && !jobProperties.isIgnoreSNErrors()) {
				run.setResult(Result.FAILURE);
				throw e;
			}
			GenericUtils.printConsoleLog(listener,
					"snDevOpsConfigGetSnapshots - Exception in run method" + e.getMessage());
		}
		String resultString = null;
		// return only validated snapshots
		if (result.size() != 0) {
			result = result.stream().filter(snapshot -> snapshot.getValidation().equalsIgnoreCase("passed"))
					.collect(Collectors.toList());
			resultString = mapper.writeValueAsString(result);
			GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots Result " + resultString);
			return resultString;
		} else {
			if (!jobProperties.isIgnoreSNErrors()) {
				run.setResult(Result.FAILURE);
				throw new AbortException("snDevOpsConfigGetSnapshots  - Status Step Failed - No Result");
			}
			GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Status Step Failed - No Result");
			return result.toString();
		}
	}

	public List<CDMSnapshot> processSnapshotsByPollingCreationAndValidationStatus(String applicationName,
			List<String> deployableNames, String changeSetId) throws IOException, InterruptedException, JSONException {
		// poll for snapshot creation first
		DevOpsModel model = new DevOpsModel();
		TaskListener listener = getContext().get(TaskListener.class);
		GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Polling for creation");
		pollForSnapshotCreation(applicationName, deployableNames, changeSetId, model);
		JSONObject snapShotStatus = model.snapShotExists(applicationName, deployableNames, changeSetId);
		checkErrorInResponse(snapShotStatus,
				"snDevOpsConfigGetSnapshots - Exception occurred while polling for snapshot creation");
		JSONArray result = snapShotStatus.getJSONArray(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
		List<CDMSnapshot> snapshotListAfterpoll = getSnapshotList(result);

		if (snapshotListAfterpoll.size() == 0 || snapshotListAfterpoll.size() < deployableNames.size()) {
			// no polling
			snapshotListAfterpoll.clear();
			return snapshotListAfterpoll;
		}
		// poll for validation.
        GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Polling for Validation : "+snapshotListAfterpoll.size());
		pollForSnapshotValidation(snapshotListAfterpoll, model);
		// querying db again to get latest status of the snapshots.
		List<CDMSnapshot> snapshotList = new ArrayList<>();
		getSnapShotListAfterQuery(applicationName, deployableNames, model, snapshotList, changeSetId);
		return snapshotList;
	}

	private List<CDMSnapshot> processSnapshotsByPollingValidationStatus(String applicationName,
			List<String> deployableNames) throws IOException, InterruptedException, JSONException {
		DevOpsModel devOpsModel = new DevOpsModel();
		List<CDMSnapshot> snapshotList = new ArrayList<>();
		// get latest published and validated snapshot for each app, deployable
		getSnapShotListAfterQuery(applicationName, deployableNames, devOpsModel, snapshotList, null);

		// remove already validated snapshots.
		Stream<String> validationStates = Stream.of("in_progress", "requested", "not_validated");
		List<CDMSnapshot> filteredSnapshots = snapshotList.stream()
				.filter(snapshot -> validationStates.anyMatch(s -> s.contains(snapshot.getValidation())))
				.collect(Collectors.toList());
		// if all snapshots are validated retrun the snapshot object list without
		// polling
		if (filteredSnapshots.size() == 0) {
			// no polling
			return snapshotList;
		}

		pollForSnapshotValidation(filteredSnapshots, devOpsModel); // poll all snapshots for 7 minutes.
		// querying db again to get latest status of the snapshots.
		snapshotList = new ArrayList<>();
		getSnapShotListAfterQuery(applicationName, deployableNames, devOpsModel, snapshotList, null);
		return snapshotList;
	}

	private void getSnapShotListAfterQuery(String applicationName, List<String> deployableNames,
			DevOpsModel devOpsModel, List<CDMSnapshot> snapshotList, String changeSetId)
			throws IOException, InterruptedException, JSONException {
		TaskListener listener = getContext().get(TaskListener.class);
		deployableNames.forEach(deployableName -> {
			JSONObject snapshots = devOpsModel.getSnapshotsByDeployables(applicationName, deployableName, changeSetId);
			try {
				checkErrorInResponse(snapshots,
						"Unable to fetch snapshots for " + step.getApplicationName() + ":" + step.getDeployableName());
			} catch (IOException | InterruptedException e) {
				GenericUtils.printConsoleLog(listener,
						"snDevOpsConfigGetSnapshots - Exception in getSnapShotListAfterQuery" + e);
			}
			JSONArray result = snapshots.getJSONArray(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
			try {
				snapshotList.addAll(getSnapshotList(result));
			} catch (IOException e) {
				GenericUtils.printConsoleLog(listener,
						"snDevOpsConfigGetSnapshots - Exception in getSnapShotListAfterQuery" + e);
			}
		});
	}

	public void pollForSnapshotCreation(String applicationName, List<String> deployableNames, String changeSetId,
			DevOpsModel model) throws IOException, InterruptedException, JSONException {
		TaskListener listener = getContext().get(TaskListener.class);
		final List<CDMSnapshot> snapshotListAfterpoll = new ArrayList<CDMSnapshot>();
		Callable<String> callable = () -> {
			String retryStatus = "success";
			GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Checking for Snapshot");
			JSONObject snapShotStatus = model.snapShotExists(applicationName, deployableNames, changeSetId);
			checkErrorInResponse(snapShotStatus, "Exception occurred while polling for snapshot creation");
			JSONArray result = null;
			result = snapShotStatus.getJSONArray(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
			
			if (result == null || result.size() < 1) {
				retryStatus = RETRY;
				return retryStatus;
			} else {
				snapshotListAfterpoll.add(getSnapshotList(result).get(0));
				if (snapshotListAfterpoll.size() < deployableNames.size()) {
                    retryStatus = RETRY;
                    GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Snapshots Found : " + snapshotListAfterpoll.size());
				}
				return retryStatus;
			}
		};
		pollWithCallable(listener, callable, model);
	}

	private void pollForSnapshotValidation(List<CDMSnapshot> filteredSnapshots, DevOpsModel model)
			throws IOException, InterruptedException, JSONException {
		TaskListener listener = getContext().get(TaskListener.class);
		Callable<String> callable = () -> {
			String retryStatus = "success";
			JSONObject snapShotStatus = model
					.querySnapShotStatus(filteredSnapshots.stream().map(s -> s.getName()).collect(Collectors.toList()));
			checkErrorInResponse(snapShotStatus, "Exception occurred while polling snapshot for validation");
			JSONArray result = snapShotStatus.getJSONArray(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
			List<CDMSnapshot> snapshotListAfterpoll = getSnapshotList(result);
			if (snapshotListAfterpoll.size() > 0) {
				retryStatus = RETRY;
			}
			return retryStatus;
		};
		pollWithCallable(listener, callable, model);
	}

	private void pollWithCallable(TaskListener listener, Callable<String> callable, DevOpsModel model)
			throws AbortException {
		// 1=2 sec,2=4 sec,3 6 sec,5 10 sec,8 16 sec,13 26 sec,21 42 sec,34 66 sec,55
		// 110 sec,89 178 sec = total 7 mins retry
		try {
			RetryConfig config = new RetryConfigBuilder().retryOnSpecificExceptions(ConnectException.class)
					.retryOnReturnValue(RETRY).withDelayBetweenTries(Duration.ofMillis(durationBetweenRetries))
					.withFibonacciBackoff().withMaxNumberOfTries(maxNumberOfRetries).build();
			new CallExecutorBuilder<String>().config(config).onSuccessListener(s -> {
				GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots Poll Success ");
			}).build().execute(callable);
		} catch (RetriesExhaustedException e) {
			try {
				Run<?, ?> run = getContext().get(Run.class);
				;
				DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());
				if (jobProperties.isIgnoreSNErrors()) {
					GenericUtils.printConsoleLog(listener,
							"snDevOpsConfigGetSnapshots - Retry exhausted  " + e.getMessage());
				} else {
					throw new AbortException(e.getMessage());
				}
			} catch (InterruptedException | IOException io) {
				GenericUtils.printConsoleLog(listener,
						"snDevOpsConfigGetSnapshots - Exception in pollWithCallable " + e.getMessage());
			}
		}
	}

	private List<CDMSnapshot> getSnapshotList(JSONArray result) throws IOException {
		Iterator iterator = result.iterator();
		List<CDMSnapshot> snapshotList = new ArrayList<>();
		while (iterator.hasNext()) {
			Map<String, String> snapShotObjectMap = (Map) iterator.next();
			CDMSnapshot snapshotObject = mapper.convertValue(snapShotObjectMap, CDMSnapshot.class);
			snapshotList.add(snapshotObject);
		}
		return snapshotList;
	}

	private List<String> getDeployableNames(DevOpsModel model, String applicationName)
			throws IOException, InterruptedException, JSONException {
		JSONObject snapshotStatus;
		List<String> deployableNames = Lists.newArrayList();
		if (StringUtils.isEmpty(this.step.getDeployableName())) {
			snapshotStatus = model.getDeployablesForApp(applicationName);
			checkErrorInResponse(snapshotStatus,
					"Unable to fetch deployable names for App" + this.step.getApplicationName());
			JSONArray result = snapshotStatus.getJSONArray(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
			for (Object item : result) {
				Map<String, String> map = (Map<String, String>) item;
				for (Map.Entry<String, String> entry : map.entrySet()) {
					deployableNames.add((String) entry.getValue());
				}
			}
		} else {
			deployableNames.add(this.step.getDeployableName());
		}
		return deployableNames;
	}

	private void checkErrorInResponse(JSONObject snapshotStatus, String errorMessage)
			throws IOException, InterruptedException, JSONException {
		Run<?, ?> run = null;
		TaskListener listener = null;

		run = getContext().get(Run.class);
		listener = getContext().get(TaskListener.class);

		if (snapshotStatus.containsKey(DevOpsConstants.COMMON_RESULT_ERROR.toString())) {
			JSONObject error = snapshotStatus.getJSONObject(DevOpsConstants.COMMON_RESULT_ERROR.toString());
			String errorFromAPI = error.getString(DevOpsConstants.COMMON_RESPONSE_MESSAGE.toString());
			GenericUtils.printConsoleLog(listener,
					"snDevOpsConfigGetSnapshots - " + errorMessage + ": " + errorFromAPI);
			run.setResult(Result.FAILURE);
			throw new AbortException(errorMessage);
		}
	}

	private Boolean validateStepInputs() throws Exception {
		TaskListener listener = getContext().get(TaskListener.class);
		if (StringUtils.isEmpty(step.getApplicationName())) {
			GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Application Name is missing");
			GenericUtils.printConsoleLog(listener,
					"snDevOpsConfigGetSnapshots - Mandatory Parameter is ApplicationName");
			return false;
		}
		if (StringUtils.isEmpty(step.getDeployableName()) && StringUtils.isEmpty(step.getChangeSetId())) {
			GenericUtils.printConsoleLog(listener,
					"snDevOpsConfigGetSnapshots - DeployableName or ChangesetId  is Mandatory");
			return false;
		}
		return true;
	}
}