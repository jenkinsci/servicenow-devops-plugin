package io.jenkins.plugins.pipeline.steps.executions;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
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
import hudson.FilePath;
import hudson.EnvVars;
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

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.File;
import java.io.FileOutputStream;

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
		FilePath workspace = getContext().get(FilePath.class);
		EnvVars envVars = getContext().get(EnvVars.class);
		DevOpsModel model = new DevOpsModel();
		DevOpsJobProperty jobProperties = model.getJobProperty(run.getParent());
		List<CDMSnapshot> result = new ArrayList<>();

		try {
			GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Config status step execution starts");
			if (validateStepInputs()) {
				// Checking if app is valid
				JSONObject appDetails = null;
				appDetails = model.checkForValidApp(this.step.getApplicationName());

				JSONArray appResult = appDetails.getJSONArray(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
				if (appResult.size() == 0) {
					GenericUtils.printConsoleLog(listener,
							"snDevOpsConfigGetSnapshots - Failed to find application with given name");
					throw new AbortException("Failed to find application with given name");
				}
				String appSysId = appResult.getJSONObject(0).getString("sys_id");
				if(appSysId == null) {
					GenericUtils.printConsoleLog(listener,
							"snDevOpsConfigGetSnapshots - Failed to find application with given name");
					throw new AbortException("Failed to find application with given name");
				}
				// Polling for changeset to commit
				String appName = "";
				String changesetSysId = "";
				if (!StringUtils.isEmpty(this.step.getChangesetNumber())) {

					Callable<String> callable = () -> {
						String retryStatus = "success";
						JSONObject changesetDetails = model.getChangesetId(this.step.getChangesetNumber());
						JSONArray details = changesetDetails
								.getJSONArray(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
						if (details.size() == 0) {
							return retryStatus;
						}
						JSONObject changesetSysIdObj = details.getJSONObject(0);
						String changesetState = changesetSysIdObj
								.getString(DevOpsConstants.COMMON_RESPONSE_STATE.toString());

						if (changesetState.equals("open") || changesetState.equals("commit_failed")
								|| changesetState.equals("blocked") || changesetState.equals("committed")) {
							return retryStatus;
						} else {
							GenericUtils.printConsoleLog(listener,
									"snDevOpsConfigGetSnapshots - Waiting for changeset to get committed");
							retryStatus = RETRY;
							return retryStatus;
						}
					};
					pollWithCallable(listener, callable, model);

					JSONObject committedChangeset = model.getChangesetId(this.step.getChangesetNumber());
					JSONArray changesetDetail = committedChangeset
							.getJSONArray(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());

					if (changesetDetail.size() == 0) {
						GenericUtils.printConsoleLog(listener,
								"snDevOpsConfigGetSnapshots - No changeset record found for input parameters");
						throw new AbortException("No changeset record found for input parameters");
					}

					JSONObject changesetObj = changesetDetail.getJSONObject(0);
					String state = changesetObj.getString("state");
					appName = changesetObj.getString("cdm_application.node.name");
					changesetSysId = changesetObj.getString("sys_id");

					if (!state.equalsIgnoreCase("committed")) {
						if (state.equalsIgnoreCase("open")) {
							GenericUtils.printConsoleLog(listener,
									"snDevOpsConfigGetSnapshots - ERROR: Unable to return snapshots for an open changeset."
											+ " Please ensure the changeset is committed before trying to get status of possibly created snapshots");
							run.setResult(Result.FAILURE);
							throw new AbortException("");
						} else if (state.equalsIgnoreCase("blocked")) {
							GenericUtils.printConsoleLog(listener,
									"snDevOpsConfigGetSnapshots - ERROR: unable to return snapshots since the changeset is currently blocked."
											+ " Please review the conflict details in DevOps Config and retry upload of your config data");
							run.setResult(Result.FAILURE);
							throw new AbortException("");
						} else if (state.equalsIgnoreCase("commit_failed")) {
							GenericUtils.printConsoleLog(listener,
									"snDevOpsConfigGetSnapshots - ERROR: unable to return snapshots since the changeset failed to commit."
											+ " Please review the details in DevOps Config and retry upload of your configuration data");
							run.setResult(Result.FAILURE);
							throw new AbortException("");
						} else {
							GenericUtils.printConsoleLog(listener,
									"snDevOpsConfigGetSnapshots - Changeset provided is not yet commited - State of changeset - "
											+ state);
							throw new AbortException("");
						}
					}

					if (!this.step.getApplicationName().equalsIgnoreCase(appName)) {
						GenericUtils.printConsoleLog(listener,
								"snDevOpsConfigGetSnapshots - Changeset provided is not associated with application : "
										+ this.step.getApplicationName());
						throw new AbortException("Changeset provided is not associated with application");
					}
				}

				List<String> deployableNames = getDeployableNames(model, changesetSysId, listener);
				if (deployableNames.size() == 0) {
					GenericUtils.printConsoleLog(listener,
							"snDevOpsConfigGetSnapshots - No deployables are impacted. Ignoring polling for snapshot");
					throw new AbortException("No deployables are impacted. Ignoring polling for snapshot");
				}

				if (!StringUtils.isEmpty(this.step.getDeployableName())) {
					if (!deployableNames.contains(this.step.getDeployableName().toLowerCase())) {
						GenericUtils.printConsoleLog(listener,
								"snDevOpsConfigGetSnapshots - Deployable provided is not impacted under given changeset. Ignoring polling for snapshot");
						throw new AbortException("Deployable provided is not impacted under given changeset");
					} else {
						deployableNames.clear();
						deployableNames.add(this.step.getDeployableName());
					}
				}

				if (StringUtils.isEmpty(step.getChangesetNumber())) {
					result = processSnapshotsByPollingValidationStatus(appSysId, deployableNames,
							listener);
				} else {
					result = processSnapshotsByPollingCreationAndValidationStatus(appSysId,
							deployableNames, step.getChangesetNumber());
				}
				run.setResult(Result.SUCCESS);
			} else {
				if (!jobProperties.isIgnoreSNErrors() || this.step.getMarkFailed()) {
					run.setResult(Result.FAILURE);
					throw new AbortException("snDevOpsConfigGetSnapshots  - Missing parameters.");
				} else {
					GenericUtils.printConsoleLog(listener,
							"snDevOpsConfigGetSnapshots - Validation of step inputs failed");
				}
			}
		} catch (IOException | InterruptedException | JSONException | IndexOutOfBoundsException
				| ParserConfigurationException | TransformerException e) {

			if ((e instanceof AbortException) && (!jobProperties.isIgnoreSNErrors() || this.step.getMarkFailed())) {
				run.setResult(Result.FAILURE);
				throw e;
			}
			GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Exception in run method");
		}
		String resultString = null;
		if (this.step.getIsValidated())
			result = result.stream().filter(snapshot -> snapshot.getValidation().equals("passed")
					|| snapshot.getValidation().equals("passed_with_exception")).collect(Collectors.toList());

		if (result.size() != 0) {
			List<CDMSnapshot> resultWithValidation = null;
			GenericUtils.printConsoleLog(listener,
					"snDevOpsConfigGetSnapshots - Preparing to fetch policy validation results for snapshots");
			resultWithValidation = generateTestResults(result, workspace, listener, envVars);
			resultString = mapper.writeValueAsString(resultWithValidation);
			GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots -  Result " + resultString);
			return resultString;
		} else {
			if (!jobProperties.isIgnoreSNErrors() || this.step.getMarkFailed()) {
				run.setResult(Result.FAILURE);
				throw new AbortException("snDevOpsConfigGetSnapshots  - No snapshot found for input parameters");
			}
			GenericUtils.printConsoleLog(listener,
					"snDevOpsConfigGetSnapshots  - No snapshot found for input parameters");
			return null;
		}
	}

	public List<CDMSnapshot> generateTestResults(List<CDMSnapshot> results, FilePath workspace, TaskListener listener,
			EnvVars envVars) throws IOException, InterruptedException, JSONException, IndexOutOfBoundsException,
			ParserConfigurationException, TransformerException {
		DevOpsModel model = new DevOpsModel();
		int noOfSnapshots = results.size();

		List<String> snapshotNames = new ArrayList<String>();
		List<String> validationStates = new ArrayList<String>();
		List<String> snapshotSysIds = new ArrayList<String>();
		List<CDMSnapshot> updatedSnapshotObj = new ArrayList<CDMSnapshot>();

		for (int i = 0; i < noOfSnapshots; i++) {
			snapshotNames = results.stream().map(s -> s.getName()).collect(Collectors.toList());
			validationStates = results.stream().map(s -> s.getValidation()).collect(Collectors.toList());
			snapshotSysIds = results.stream().map(s -> s.getSys_id()).collect(Collectors.toList());
		}

		JSONObject validationResults = null;
		List<JSONObject> processedResults = new ArrayList<>();

		for (int j = 0; j < noOfSnapshots; j++) {
			String snapshotName = snapshotNames.get(j);
			String validationState = validationStates.get(j);
			String snapshotSysId = snapshotSysIds.get(j);

			if (validationState.equalsIgnoreCase("passed") || validationState.equalsIgnoreCase("failed")
					|| validationState.equalsIgnoreCase("execution_error")
					|| validationState.equalsIgnoreCase("not_validated")
					|| validationState.equalsIgnoreCase("passed_with_exception")) {
				validationResults = model.getValidationResults(snapshotSysId, "", "");
				JSONArray result = validationResults.getJSONArray("result");
				if (result.isEmpty()) {
					GenericUtils.printConsoleLog(listener,
							"snDevOpsConfigGetSnapshots - Failed to get the validation results or no results found for the snapshot : "
									+ snapshotName);
					processValidationResults(validationResults, snapshotName, snapshotSysId, validationState, workspace,
							listener, envVars);
					processedResults.add(null);
					continue;
				}
				GenericUtils.printConsoleLog(listener,
						"snDevOpsConfigGetSnapshots - Fetching validation results for the snapshot : " + snapshotName);
				processValidationResults(validationResults, snapshotName, snapshotSysId, validationState, workspace,
						listener, envVars);
				processedResults.add(validationResults);
			} else {
				GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Validation of snapshot "
						+ snapshotName + " is not yet complete, skipping validation results");
				processedResults.add(null);
			}
		}
		for (int k = 0; k < processedResults.size(); k++) {
			JSONObject policyValidations = processedResults.get(k);
			CDMSnapshot intialResult = results.get(k);
			intialResult.setValidationResults(policyValidations);
			updatedSnapshotObj.add(intialResult);
		}
		return updatedSnapshotObj;
	}

	public void processValidationResults(JSONObject validationResult, String snapshotName, String snapshotSysId,
			String validationState, FilePath workspace, TaskListener listener, EnvVars envVars)
			throws IOException, InterruptedException, JSONException, IndexOutOfBoundsException,
			ParserConfigurationException, TransformerException {

		JSONArray results = validationResult.getJSONArray("result");
		String format = "";
		if (this.step.getOutputFormat() == null || !(this.step.getOutputFormat().equalsIgnoreCase("json")))
			format = "xml";
		else
			format = "json";
		List<JSONObject> processedResults = new ArrayList<>();

		String appName = "";

		if (results.size() != 0) {
			int noOfPolicies = results.size();
			List<String> policyNames = new ArrayList<>();
			List<String> impactedNodes = new ArrayList<>();
			List<String> nodePaths = new ArrayList<>();

			JSONObject getApp = results.getJSONObject(0);
			appName = getApp.getString("snapshot.application_id.name");

			JSONObject outputJSON = null;
			JSONObject dummyJSON = new JSONObject();
			for (int i = 0; i < noOfPolicies; i++) {
				JSONObject policyOutput = results.getJSONObject(i);
				String output = policyOutput.getString("policy_execution.output");
				String policyName = policyOutput.getString("policy.name");
				String impactedNode = policyOutput.getString("impacted_node.name");
				String nodePath = policyOutput.getString("node_path");

				if (!policyNames.contains(policyName)) {
					policyNames.add(policyName);
					impactedNodes.add(impactedNode);
					nodePaths.add(nodePath);
					if (!output.isEmpty()) {
						outputJSON = JSONObject.fromObject(output);
						processedResults.add(outputJSON);
					} else
						processedResults.add(dummyJSON);
				}
			}
			GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Generating test results");
			generateTestResults(processedResults, policyNames, impactedNodes, nodePaths, snapshotName, appName,
					snapshotSysId, format, validationState, workspace, envVars, listener);
		} else {
			String path = workspace.getRemote();
			String pipeline = envVars.get(DevOpsConstants.PIPELINE_JOB_NAME.toString());

			if (pipeline.contains("/"))
				pipeline = pipeline.replaceAll("/", "_");

			String fName = snapshotName + "_" + pipeline + "_"
					+ envVars.get(DevOpsConstants.PIPELINE_BUILD_NUMBER.toString());

			String fileName = "";
			StringBuilder filePath = new StringBuilder();
			if (format.equalsIgnoreCase("json")) {
				fileName = fName + ".json";

				filePath.append(path);
				filePath.append(File.separator);
				filePath.append(fileName);

				FileOutputStream file = new FileOutputStream(filePath.toString());
				OutputStreamWriter out = new OutputStreamWriter(file, StandardCharsets.UTF_8);
				out.write("");
				out.close();
			} else {
				fileName = fName + ".xml";

				filePath.append(path);
				filePath.append(File.separator);
				filePath.append(fileName);

				DocumentBuilderFactory documentFactoryElement = DocumentBuilderFactory.newInstance();
				DocumentBuilder documentBuilderElement = documentFactoryElement.newDocumentBuilder();
				Document documentElement = documentBuilderElement.newDocument();

				Element testSuitesRootElement = documentElement.createElement("testsuites");
				documentElement.appendChild(testSuitesRootElement);
				String name = appName + "/" + snapshotName;

				Element testSuiteRootElement = documentElement.createElement("testsuite");
				testSuitesRootElement.appendChild(testSuiteRootElement);

				Attr nameElement = documentElement.createAttribute("name");
				nameElement.setValue(name);
				testSuiteRootElement.setAttributeNode(nameElement);

				TransformerFactory transformerFactory = TransformerFactory.newInstance();
				Transformer transformer = transformerFactory.newTransformer();
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
				DOMSource source = new DOMSource(documentElement);
				StreamResult result = new StreamResult(new File(filePath.toString()));
				transformer.transform(source, result);
			}
		}
	}

	public void generateTestResults(List<JSONObject> validationResults, List<String> pNames, List<String> impactedNodes,
			List<String> nodePaths, String snapshotName, String applicationName, String snapshotSysId, String format,
			String validationState, FilePath workspace, EnvVars envVars, TaskListener listener)
			throws IOException, InterruptedException, JSONException, IndexOutOfBoundsException,
			ParserConfigurationException, TransformerException {

		DevOpsModel model = new DevOpsModel();
		String path = workspace.getRemote();
		String pipeline = envVars.get(DevOpsConstants.PIPELINE_JOB_NAME.toString());
		if (pipeline.contains("/"))
			pipeline = pipeline.replaceAll("/", "_");

		String fName = snapshotName + "_" + pipeline + "_"
				+ envVars.get(DevOpsConstants.PIPELINE_BUILD_NUMBER.toString());
		String fileName = "";

		StringBuilder filePath = new StringBuilder();

		if (!(format.equalsIgnoreCase("json"))) {

			fileName = fName + ".xml";

			filePath.append(path);
			filePath.append(File.separator);
			filePath.append(fileName);

			int noOfPolicies = validationResults.size();
			int failureCount = 0;
			int nonComplaintDecisions = 0;
			for (int k = 0; k < validationResults.size(); k++) {
				JSONObject processedResult = validationResults.get(k);
				if (!(processedResult.toString().equals("{}"))) {
					String decision = processedResult.getString("decision");
					JSONArray f = processedResult.getJSONArray("failures");
					failureCount = failureCount + f.size();
					if (decision.equals("non_compliant"))
						nonComplaintDecisions++;
				}
			}

			DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
			Document document = documentBuilder.newDocument();

			String test = noOfPolicies + "";
			String noOfFailure = nonComplaintDecisions + "";

			Element root = document.createElement("testsuites");
			Attr tests = document.createAttribute("tests");
			tests.setValue(test);
			root.setAttributeNode(tests);
			Attr failures = document.createAttribute("failures");
			failures.setValue(noOfFailure);
			root.setAttributeNode(failures);
			document.appendChild(root);

			Element testsuite = document.createElement("testsuite");
			root.appendChild(testsuite);
			String name1 = applicationName + "/" + snapshotName;
			Attr name = document.createAttribute("name");
			name.setValue(name1);
			testsuite.setAttributeNode(name);

			Element[] testcases = new Element[noOfPolicies];
			Attr[] decisions = new Attr[noOfPolicies];
			Attr[] policyNames = new Attr[noOfPolicies];

			for (int pc = 0; pc < noOfPolicies; pc++) {
				testcases[pc] = document.createElement("testcase");
				decisions[pc] = document.createAttribute("decision");
				policyNames[pc] = document.createAttribute("name");
			}

			Element[] failure = new Element[failureCount + noOfPolicies];

			for (int f = 0; f < (failureCount + noOfPolicies); f++) {
				failure[f] = document.createElement("failure");
			}

			int h = 0;
			for (int j = 0; j < noOfPolicies; j++) {
				JSONObject policyResult = validationResults.get(j);
				String n = "";

				if (policyResult.toString().equals("{}") && (validationState.equalsIgnoreCase("execution_error")
						|| validationState.equalsIgnoreCase("failed"))) {
					decisions[j].setValue("non_complaint");
					policyNames[j].setValue(pNames.get(j));

					testcases[j].setAttributeNode(decisions[j]);
					testcases[j].setAttributeNode(policyNames[j]);
					testcases[j].appendChild(failure[h]);
					testsuite.appendChild(testcases[j]);
					h++;
				} else {
					JSONObject r = model.getValidationResults(snapshotSysId, pNames.get(j), "xml");
					JSONArray info = r.getJSONArray("result");
					if (!info.isEmpty()) {
						for (int b = 0; b < info.size(); b++) {
							n = n.concat(info.get(b).toString()) + "  ";
						}
					}
					String decision = policyResult.getString("decision");

					decisions[j].setValue(decision);
					policyNames[j].setValue(pNames.get(j));
					testcases[j].setAttributeNode(decisions[j]);
					testcases[j].setAttributeNode(policyNames[j]);

					JSONArray f = policyResult.getJSONArray("failures");
					if (f.size() == 0) {
						if (decision.equalsIgnoreCase("non_complaint")) {
							testcases[j].appendChild(failure[h]);
							testsuite.appendChild(testcases[j]);
							h++;
						} else
							testsuite.appendChild(testcases[j]);
					} else {
						failure[h].appendChild(document.createTextNode(n));
						testcases[j].appendChild(failure[h]);
						testsuite.appendChild(testcases[j]);
						h++;
					}
				}
			}
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			DOMSource source = new DOMSource(document);
			StreamResult result = new StreamResult(new File(filePath.toString()));
			transformer.transform(source, result);
		} else {
			fileName = fName + ".json";

			filePath.append(path);
			filePath.append(File.separator);
			filePath.append(fileName);

			List<JSONObject> modifiedList = new ArrayList<>();
			for (int p = 0; p < validationResults.size(); p++) {
				JSONObject newObj = new JSONObject();
				List<JSONObject> results = new ArrayList<>();
				List<JSONObject> failures = new ArrayList<>();
				List<JSONObject> warnings = new ArrayList<>();
				int nonComplaintCount = 0;

				newObj.put("PolicyName", pNames.get(p));
				JSONObject o = model.getValidationResults(snapshotSysId, pNames.get(p), "json");
				JSONArray info = o.getJSONArray("result");
				if (!info.isEmpty()) {
					for (int i = 0; i < info.size(); i++) {
						JSONObject f = info.getJSONObject(i);
						String type = f.getString("type");
						if (f.getString("policy_execution.decision").equalsIgnoreCase("non_compliant"))
							nonComplaintCount++;
						if (type.equalsIgnoreCase("Information"))
							results.add(f);
						else if (type.equalsIgnoreCase("Warning"))
							warnings.add(f);
						else
							failures.add(f);
					}
				}
				if (nonComplaintCount == 0)
					newObj.put("Decision", "compliant");
				else
					newObj.put("Decision", "non_compliant");

				newObj.put("Results", results);
				newObj.put("Warnings", warnings);
				newObj.put("Failures", failures);

				modifiedList.add(newObj);

			}
			FileOutputStream file = new FileOutputStream(filePath.toString());
			OutputStreamWriter out = new OutputStreamWriter(file, StandardCharsets.UTF_8);
			JSONObject jsonResult = new JSONObject();
			jsonResult.put("Application", applicationName);
			jsonResult.put("Snapshot", snapshotName);
			jsonResult.put("ValidationResults", modifiedList);
			out.write(jsonResult.toString());
			out.close();
		}
	}

	public List<CDMSnapshot> processSnapshotsByPollingCreationAndValidationStatus(String appSysId,
			List<String> deployableNames, String changesetNumber)
			throws IOException, InterruptedException, JSONException, IndexOutOfBoundsException {
		// poll for snapshot creation first
		DevOpsModel model = new DevOpsModel();
		TaskListener listener = getContext().get(TaskListener.class);

		GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Polling for creation");
		pollForSnapshotCreation(appSysId, deployableNames, changesetNumber, model);
		JSONObject snapShotStatus = model.snapShotExists(appSysId, deployableNames, changesetNumber);

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
		GenericUtils.printConsoleLog(listener,
				"snDevOpsConfigGetSnapshots - Polling for validation : " + snapshotListAfterpoll.size());
		pollForSnapshotValidation(snapshotListAfterpoll, model);
		// querying db again to get latest status of the snapshots.
		List<CDMSnapshot> snapshotList = new ArrayList<>();
		getSnapShotListAfterQuery(appSysId, deployableNames, model, snapshotList, changesetNumber);
		return snapshotList;
	}

	private List<CDMSnapshot> processSnapshotsByPollingValidationStatus(String appSysId,
			List<String> deployableNames, TaskListener listener)
			throws IOException, InterruptedException, JSONException, IndexOutOfBoundsException {
		DevOpsModel devOpsModel = new DevOpsModel();
		List<CDMSnapshot> snapshotList = new ArrayList<>();
		// get latest published and validated snapshot for each app, deployable
		getSnapShotListAfterQuery(appSysId, deployableNames, devOpsModel, snapshotList, null);
		// change
		if (snapshotList.size() == 0) {
			return snapshotList;
		}

		// remove already validated snapshots.
		Stream<String> validationStates = Stream.of("in_progress", "requested");
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
		getSnapShotListAfterQuery(appSysId, deployableNames, devOpsModel, snapshotList, null);
		return snapshotList;
	}

	private void getSnapShotListAfterQuery(String appSysId, List<String> deployableNames,
			DevOpsModel devOpsModel, List<CDMSnapshot> snapshotList, String changesetNumber)
			throws IOException, InterruptedException, JSONException, IndexOutOfBoundsException {
		TaskListener listener = getContext().get(TaskListener.class);
		deployableNames.forEach(deployableName -> {
			JSONObject snapshots = devOpsModel.getSnapshotsByDeployables(appSysId, deployableName,
					changesetNumber, this.step.getIsValidated());
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

	public void pollForSnapshotCreation(String appSysId, List<String> deployableNames, String changesetNumber,
			DevOpsModel model) throws IOException, InterruptedException, JSONException, IndexOutOfBoundsException {
		TaskListener listener = getContext().get(TaskListener.class);
		final List<CDMSnapshot> snapshotListAfterpoll = new ArrayList<CDMSnapshot>();
		Callable<String> callable = () -> {
			String retryStatus = "success";
			GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Checking for snapshot");
			JSONObject snapShotStatus = model.snapShotExists(appSysId, deployableNames, changesetNumber);
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
					GenericUtils.printConsoleLog(listener,
							"snDevOpsConfigGetSnapshots - Snapshots found : " + snapshotListAfterpoll.size());
				}
				return retryStatus;
			}
		};
		pollWithCallable(listener, callable, model);
	}

	private void pollForSnapshotValidation(List<CDMSnapshot> filteredSnapshots, DevOpsModel model)
			throws IOException, InterruptedException, JSONException, IndexOutOfBoundsException {
		TaskListener listener = getContext().get(TaskListener.class);
		Callable<String> callable = () -> {
			String retryStatus = "success";
			Thread.sleep(500);
			GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Waiting for validation to complete");
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
				if (jobProperties.isIgnoreSNErrors() && !this.step.getMarkFailed()) {
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

	private List<String> getDeployableNames(DevOpsModel model, String changesetId, TaskListener listener)
			throws IOException, InterruptedException, JSONException, IndexOutOfBoundsException {
		JSONObject impactedDeployables;

		List<String> deployableNames = Lists.newArrayList();
		if (!StringUtils.isEmpty(changesetId)) {

			impactedDeployables = model.getImpactedDeployables(changesetId);
			if (this.step.getShowResults())
				GenericUtils.printConsoleLog(listener,
						"snDevOpsConfigGetSnapshots - Response from impacted deployables api : " + impactedDeployables);
			checkErrorInResponse(impactedDeployables,
					"Unable to fetch deployable names for App" + this.step.getApplicationName());
			JSONArray result = impactedDeployables.getJSONArray(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());

			for (int i = 0; i < result.size(); i++) {
				JSONObject deployable = result.getJSONObject(i);
				String deployableName = deployable.getString("name");
				deployableNames.add(deployableName.toLowerCase());
			}
			GenericUtils.printConsoleLog(listener,
					"snDevOpsConfigGetSnapshots - Deployables which got impacted are : " + deployableNames);
		} else {
			deployableNames.add(this.step.getDeployableName().toLowerCase());
		}
		return deployableNames;
	}

	private void checkErrorInResponse(JSONObject snapshotStatus, String errorMessage)
			throws IOException, InterruptedException, JSONException, IndexOutOfBoundsException {
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
			GenericUtils.printConsoleLog(listener, "snDevOpsConfigGetSnapshots - Application name is missing");
			GenericUtils.printConsoleLog(listener,
					"snDevOpsConfigGetSnapshots - Application name is mandatory parameter");
			return false;
		}
		if (StringUtils.isEmpty(step.getDeployableName()) && StringUtils.isEmpty(step.getChangesetNumber())) {
			GenericUtils.printConsoleLog(listener,
					"snDevOpsConfigGetSnapshots - Deployable name or Changeset number  is mandatory");
			return false;
		}
		return true;
	}
}