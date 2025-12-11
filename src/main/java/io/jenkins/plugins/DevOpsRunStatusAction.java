package io.jenkins.plugins;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.FreeStyleBuild;
import hudson.model.InvisibleAction;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction;
import hudson.tasks.test.PipelineBlockWithTests;
import hudson.tasks.test.TabulatedResult;
import io.jenkins.plugins.actions.RegisterSecurityAction;
import io.jenkins.plugins.model.DevOpsJFrogModel;
import io.jenkins.plugins.model.DevOpsPipelineGraph;
import io.jenkins.plugins.model.DevOpsPipelineNode;
import io.jenkins.plugins.model.DevOpsPullRequestModel;
import io.jenkins.plugins.model.DevOpsRunStatusJobModel;
import io.jenkins.plugins.model.DevOpsRunStatusModel;
import io.jenkins.plugins.model.DevOpsRunStatusSCMModel;
import io.jenkins.plugins.model.DevOpsRunStatusStageModel;
import io.jenkins.plugins.model.DevOpsRunStatusTestCaseModel;
import io.jenkins.plugins.model.DevOpsRunStatusTestModel;
import io.jenkins.plugins.model.DevOpsRunStatusTestSuiteModel;
import io.jenkins.plugins.model.DevOpsSecurityResultModel;
import io.jenkins.plugins.model.DevOpsSonarQubeModel;
import io.jenkins.plugins.model.DevOpsTestSummary;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

public class DevOpsRunStatusAction extends InvisibleAction {
	private static final Logger LOGGER = Logger.getLogger(DevOpsRunStatusAction.class.getName());
	private DevOpsRunStatusModel model;
	private Set<String> seenIds = new HashSet<String>();
	private DevOpsPipelineGraph pipelineGraph = new DevOpsPipelineGraph();
	private static final int LOG_SIZE_LIMIT = 1024 * 500;
	public Map<String, String> changeRequestInfo = new HashMap<String, String>();


	public void setModel(DevOpsRunStatusModel model) {
		this.model = model;
	}

	public DevOpsRunStatusModel getModel() {
		return model;
	}

	public Set<String> getSeenIds() {
		return seenIds;
	}

	public void setSeenId(String nodeId) {
		if (nodeId != null && !nodeId.isEmpty())
			this.seenIds.add(nodeId);
	}

	// Called from onStarted/onCompleted RunListener, or from onNewHead GraphListener
	public DevOpsRunStatusModel createRunStatus(FlowNode fn, Run<?, ?> run, EnvVars vars,
	                                            String runPhase, String stagePhase,
	                                            boolean isStageStart,
	                                            DevOpsPipelineNode devOpsPipelineNode) {
		// pass in the upstream execution url
		Jenkins jenkins = Jenkins.getInstanceOrNull();
		DevOpsRunStatusModel status = new DevOpsRunStatusModel();
		if (run != null && jenkins != null) {
			String jenkinsUrl = jenkins.getRootUrl();
			status.setNumber(run.getNumber());
			status.setUrl(jenkinsUrl + run.getUrl());
			status.setTimestamp(run.getTimeInMillis() +
					run.getDuration()); //adding duration to address DEF0069821
			hudson.model.Result result = run.getResult();
			if (result != null) {
				status.setResult(result.toString());
			}

			if (runPhase != null)
				status.setPhase(runPhase);

			Job<?, ?> job = run.getParent();
			if (job != null) {
				status.setPronoun(job.getPronoun());
				status.setMultiBranch(Boolean.toString(GenericUtils.isMultiBranch(job)));
			}
			DevOpsRunStatusJobModel jobModel = createRunStatusJob(run);
			status.setJobModel(jobModel);
			DevOpsRunStatusStageModel stageModel =
					createRunStatusStage(fn, run, stagePhase, isStageStart, devOpsPipelineNode);// pass in the upstreamExecutionURL

			status.setStageModel(stageModel);
			DevOpsRunStatusSCMModel scmModel = createRunStatusSCM(run, vars);
			status.setSCMModel(scmModel);
			String stageModelName = stageModel.getName();

			// Build test summaries
			DevOpsRunStatusTestModel testModel = createRunStatusTest(run, status.getPronoun(), stageModel.getId(), stageModelName);
			status.setTestModel(testModel);

            /*if (testModel.getTotal() > 0){
				//long stageStartTime =
				//		(((StepEndNode) fn).getStartNode()).getAction(TimingAction.class)
				//		.getStartTime();
				long stageEndtime = ((StepEndNode) fn).getAction(TimingAction.class).getStartTime();
				DevOpsTestSummary testSummary = new DevOpsTestSummary.Builder(testModel.getName())
						.duration(testModel.getDuration())
						.total(testModel.getTotal())
						.passed(testModel.getPassed())
						.failed(testModel.getFailed())
						.skipped(testModel.getSkipped())
						.inStage(stageModel.getName())
						.inPipeline(jobModel.getName())
						.buildNumber(status.getNumber())
						.start(stageEndtime-(long)(testModel.getDuration()*1000))
						.finish(stageEndtime)
						.reportUrl(status.getUrl()+ "testReport/")
						.build();
				status.setTestSummary(testSummary);
			} else {
				//long stageStartTime =
				//		(((StepEndNode) fn).getStartNode()).getAction(TimingAction.class)
				//		.getStartTime();
				if (run!=null && fn!=null && !isStageStart) {
					long stageEndtime =
							((StepEndNode) fn).getAction(TimingAction.class).getStartTime();
					List<DevOpsTestSummary> testSummaryList = createTestSummary(run,
							stageModel.getName(), jobModel.getName(), status.getNumber(),
							stageEndtime);
					if (testSummaryList != null && testSummaryList.size() > 0) {
						DevOpsTestSummary testSummary = testSummaryList.get(0);
						if (testSummary != null)
							status.setTestSummary(testSummary);
					}
				}
			}*/

			// Populating pull request details
			if (DevOpsConstants.PULL_REQUEST_PRONOUN.toString().equals(job.getPronoun())) {

				// pull request Job name is in pattern PR-<PR-NUMBER>  EX: PR-15
				String jobName = job.getName();
				String pullRequestNumber = jobName.replaceAll("[^0-9]", "");

				try {
					for (Action runAction : run.getAllActions()) {
						if (runAction.getClass().getName().equalsIgnoreCase("hudson.plugins.git.util.BuildData")) {

							Method[] methods = runAction.getClass().getMethods();
							Set<String> remoteUrls = null;
							List<String> pullRequestRepoUrls = new ArrayList<String>();


							Map<String, Method> methodMap = new HashMap<String, Method>();
							for (Method m : methods) {
								methodMap.put(m.getName(), m);
							}

							if (methodMap.containsKey("getRemoteUrls")) {
								Method m = methodMap.get("getRemoteUrls");
								remoteUrls = (HashSet<String>) m.invoke(runAction);
								if (remoteUrls.size() > 0) {
									String url = (String) remoteUrls.toArray()[0];
									String repoUrl = url.replaceAll(".git$", "");
									// replacing .git suffix in repoUrl
									// Format-1: "http://bitbucket2.sndevops.xyz/scm/bal/test_devops"
									// Format-2: "http://bitbucket2.sndevops.xyz/projects/bal/repos/test_devops"

									String pullRequestRepoUrlFormat1 = repoUrl;
									pullRequestRepoUrls.add(pullRequestRepoUrlFormat1);

									String[] parts = repoUrl.split("/");
									if (parts.length > 2) {
										String projectName = parts[parts.length - 2];
										String repoName = parts[parts.length - 1];
										String pullRequestRepoUrlFormat2 = repoUrl.replaceAll("/scm/" + projectName + "/.*$", "/projects/" + projectName + "/repos/" + repoName);
										if (!pullRequestRepoUrlFormat1.equalsIgnoreCase(pullRequestRepoUrlFormat2))
											pullRequestRepoUrls.add(pullRequestRepoUrlFormat2);
									}
								}
							}
							DevOpsPullRequestModel pullRequestModel = new DevOpsPullRequestModel(pullRequestRepoUrls, pullRequestNumber);
							status.setPullRequestModel(pullRequestModel);

						}
					}
				} catch (RuntimeException ignore) {
					LOGGER.log(Level.WARNING, " DevOpsRunStatusAction.createRunStatus()- RunTime Exception :  "
							+ ignore.getMessage());
				} catch (Exception ignore) {
					LOGGER.log(Level.WARNING, " DevOpsRunStatusAction.createRunStatus()- Exception occured :  "
							+ ignore.getMessage());
				}
			}


			// populate test summary and sonar details
			if (!isStageStart) {

				long stageEndtime = 0;
				long stageStartime = 0;
				if (fn != null && fn instanceof StepEndNode) {
					TimingAction timingAction = ((StepEndNode) fn).getAction(TimingAction.class);
					if (timingAction != null)
						stageEndtime = timingAction.getStartTime();
				} else {
					stageEndtime = run.getTimestamp().getTimeInMillis() + run.getDuration();
				}


				if (fn != null && fn instanceof StepEndNode) {
					StepStartNode stageStartNode = (StepStartNode) ((StepEndNode) fn).getStartNode();
					TimingAction timingAction = stageStartNode.getAction(TimingAction.class);
					if (timingAction != null)
						stageStartime = timingAction.getStartTime();
				} else {
					stageStartime = run.getTimestamp().getTimeInMillis();
				}

				status.setStageCompleteTimeStamp(stageEndtime);
				status.setStageStartTimeStamp(stageStartime);


				String pipelineNameForPayload = jobModel.getName();
				if (GenericUtils.isMultiBranch(job)) {
					pipelineNameForPayload = vars.get("JOB_NAME");
				}

				String stageName = stageModel.getName();

				// START : Add Jfrog details
				if (((DevOpsConstants.FREESTYLE_PRONOUN.toString().equals(job.getPronoun()) ||
						DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString().equals(job.getPronoun())) &&
						DevOpsConstants.NOTIFICATION_COMPLETED.toString().equals(runPhase)) || (
						stageModel != null && GenericUtils.isNotEmpty(stageModel.getId()))) {
					String currentStageNodeId = stageModel.getId();
					List<DevOpsJFrogModel> jfrogModelsList = getJfrogBuildDetails(run, currentStageNodeId);

					for (DevOpsJFrogModel jfrogModel : jfrogModelsList) {
						if (!this.pipelineGraph.isJFrogModelResultPublished(jfrogModel)) {
							status.addToJfrogBuildModels(jfrogModel);
							this.pipelineGraph.addToJobJFrogModelResults(jfrogModel);
						}
					}
				}

				// END: Jfrog

				// START : Add sonar details to status and pipeline graph
				if (((DevOpsConstants.FREESTYLE_PRONOUN.toString().equals(job.getPronoun()) ||
						DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString().equals(job.getPronoun())) &&
						DevOpsConstants.NOTIFICATION_COMPLETED.toString().equals(runPhase)) || (
						stageModel != null && GenericUtils.isNotEmpty(stageModel.getId()))) {
					List<DevOpsSonarQubeModel> sonarQubeModelList = getSonarQubeAnalysis(run,
							stageName, pipelineNameForPayload, status.getNumber(),
							stageEndtime, stageModel.getId(), status.getPronoun(), status.isMultiBranch(), scmModel.getBranch());

					try {

						if (sonarQubeModelList != null && sonarQubeModelList.size() > 0) {
							List<DevOpsSonarQubeModel> finalList = new ArrayList<>();

							for (DevOpsSonarQubeModel sonarQubeModel : sonarQubeModelList) {
								if (!this.pipelineGraph.isSonarQubeModelResultPublished(sonarQubeModel)) {
									finalList.add(sonarQubeModel);
									this.pipelineGraph.addToJobSonarQubeModelResults(sonarQubeModel);
								}
							}

							if (finalList.size() > 0)
								status.setSonarQubeAnalysisModels(finalList);

						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				//END : Sonar block

				if (((DevOpsConstants.FREESTYLE_PRONOUN.toString().equals(job.getPronoun()) ||
						DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString().equals(job.getPronoun())) &&
						DevOpsConstants.NOTIFICATION_COMPLETED.toString().equals(runPhase)) || (
						stageModel != null && GenericUtils.isNotEmpty(stageModel.getId()))) {
					List<DevOpsSecurityResultModel> veracodeModels = getVeracodeModels(run,
							stageName, pipelineNameForPayload, status.getNumber(),
							stageEndtime, stageModel.getId(), status.getPronoun(), status.isMultiBranch(), scmModel.getBranch());

					try {

						if (veracodeModels != null && veracodeModels.size() > 0) {
							List<DevOpsSecurityResultModel> finalList = new ArrayList<>();

							for (DevOpsSecurityResultModel veracodeModel : veracodeModels) {
								if (!this.pipelineGraph.isJobSecurityResultsPublished(veracodeModel)) {
									finalList.add(veracodeModel);
									this.pipelineGraph.addToJobSecurityResults(veracodeModel);
								}
							}

							if (finalList.size() > 0)
								status.addToSecurityResults(finalList);

						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				if (((DevOpsConstants.FREESTYLE_PRONOUN.toString().equals(job.getPronoun()) ||
						DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString().equals(job.getPronoun())) &&
						DevOpsConstants.NOTIFICATION_COMPLETED.toString().equals(runPhase)) || (
						stageModel != null && GenericUtils.isNotEmpty(stageModel.getId()))) {
					List<DevOpsSecurityResultModel> checkmarxModels = getCheckmarxModels(run,
							stageName, pipelineNameForPayload, status.getNumber(),
							stageEndtime, stageModel.getId(), status.getPronoun(), status.isMultiBranch(), scmModel.getBranch());

					try {

						if (checkmarxModels != null && checkmarxModels.size() > 0) {
							List<DevOpsSecurityResultModel> finalList = new ArrayList<>();

							for (DevOpsSecurityResultModel checkmarxModel : checkmarxModels) {
								if (!this.pipelineGraph.isJobSecurityResultsPublished(checkmarxModel)) {
									finalList.add(checkmarxModel);
									this.pipelineGraph.addToJobSecurityResults(checkmarxModel);
								}
							}

							if (finalList.size() > 0)
								status.addToSecurityResults(finalList);

						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}


				// START: Security result block
				if (((DevOpsConstants.FREESTYLE_PRONOUN.toString().equals(job.getPronoun()) ||
						DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString().equals(job.getPronoun())) &&
						DevOpsConstants.NOTIFICATION_COMPLETED.toString().equals(runPhase)) || (
						stageModel != null && GenericUtils.isNotEmpty(stageModel.getId()))) {
					List<DevOpsSecurityResultModel> securityResults = getSecurityResultSteps(run,
							stageName, pipelineNameForPayload, status.getNumber(),
							stageEndtime, stageModel.getId(), status.getPronoun(), status.isMultiBranch(), scmModel.getBranch());

					try {

						if (securityResults != null && securityResults.size() > 0) {
							List<DevOpsSecurityResultModel> finalList = new ArrayList<>();

							for (DevOpsSecurityResultModel model : securityResults) {
								if (!this.pipelineGraph.isJobSecurityResultsPublished(model)) {
									finalList.add(model);
									this.pipelineGraph.addToJobSecurityResults(model);
								}
							}

							if (finalList.size() > 0) {
								status.addToSecurityResults(finalList);
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				// END: Security result block

				List<DevOpsTestSummary> testSummaryList = createTestSummary(run,
						stageName, pipelineNameForPayload, status.getNumber(),
						stageEndtime, stageModel.getId(), status.getPronoun(), status.isMultiBranch(), scmModel.getBranch());
				if (testSummaryList != null && testSummaryList.size() > 0) {
					List<DevOpsTestSummary> finalList = new ArrayList<>();

					for (DevOpsTestSummary testSummary : testSummaryList) {
						if (!this.pipelineGraph.isTestResultPublished(testSummary)) {
							finalList.add(testSummary);
							this.pipelineGraph.addToJobTestResults(testSummary);
						}
					}

					if (finalList.size() > 0)
						status.setTestSummaries(finalList);
				}
				// Test type mappings will be injected onto the notification payload depending on which configuration is used
			}

			//get log for free style if it is completed phas or
			// for pipeline if status.log is null and this is not start stage
			if (((DevOpsConstants.FREESTYLE_PRONOUN.toString().equals(job.getPronoun()) ||
					DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString().equals(job.getPronoun())) &&
					DevOpsConstants.NOTIFICATION_COMPLETED.toString().equals(runPhase)) || (
					stageModel != null && GenericUtils.isNotEmpty(stageModel.getId()) &&
							(stageModel.getLog() == null || stageModel.getLog().size() == 0) &&
							!isStageStart &&
							!(DevOpsConstants.FREESTYLE_PRONOUN.toString().equals(job.getPronoun()) &&
									DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString()
											.equals(job.getPronoun()))
			)) {
				try {
					List<String> rlogList = new ArrayList<>();
					String logText = extractLog(run.getLogText());
					rlogList.add(getLastChunk(logText, LOG_SIZE_LIMIT));
					status.setLog(rlogList);
				} catch (UnsupportedEncodingException ignore) {
					LOGGER.log(Level.WARNING,
							" Error when extracting log | " + ignore.getMessage());
				}
			}
		}
		return status;
	}

	public int addTestSummariesForTestTypeMappings(JSONObject testInfo, boolean isStageStart, Run<?, ?> run, EnvVars vars) {
		int testsAdded = 0;
		if (!isStageStart && testInfo != null && this.model != null && run != null) {
			Job<?, ?> job = run.getParent();
			DevOpsRunStatusJobModel jobModel = this.model.getJobModel();
			DevOpsRunStatusStageModel stageModel = this.model.getStageModel();
			DevOpsRunStatusSCMModel scmModel = this.model.getSCMModel();
			StringBuilder testResultFiles = new StringBuilder();
			if (jobModel != null && stageModel != null && scmModel != null) {
				if (testInfo.containsKey("tool")) {
					testResultFiles.append(testInfo.getString("tool"));
				}
				if (testInfo.containsKey("pipeline")) {
					testResultFiles.append(",");
					testResultFiles.append(testInfo.getString("pipeline"));
				}
				if (testInfo.containsKey("stages")) {
					JSONObject stageObj = testInfo.getJSONObject("stages");
					String stagename = null;
					if (this.model.getPronoun().equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) ||
							this.model.getPronoun().equalsIgnoreCase(DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString())) {
						stagename = jobModel.getName();
					} else {
						stagename = stageModel.getName();
					}

					if (stageObj.containsKey(stagename)) {
						testResultFiles.append(",");
						testResultFiles
								.append(stageObj.getString(stagename));
					}
				}

				LOGGER.log(Level.INFO,
						"DevOpsRunStatusAction.addTestSummariesForTestTypeMappings: test Result Files -" + testResultFiles.toString());

				String pipelineNameForPayload = jobModel.getName();
				if (GenericUtils.isMultiBranch(job)) {
					pipelineNameForPayload = vars.get("JOB_NAME");
				}

				DevOpsPipelineNode nodeById =
						this.pipelineGraph.getNodeById(stageModel.getId());

				FilePath workspace = null;
				WorkspaceAction action = null;
				if (nodeById != null)
					action = nodeById.getWsAction();

				if (action != null) {
					workspace = action.getWorkspace();
				} else if (run instanceof FreeStyleBuild) {
					workspace = ((FreeStyleBuild) run).getWorkspace();
				}
				if (workspace != null && testResultFiles.toString().length() > 0) {
					long startTime = 0L;
					if (nodeById != null)
						startTime = nodeById.getStartTime();
					else
						startTime = run.getTimestamp().getTimeInMillis();

					List<FilePath> testFileList = new ArrayList<>();
					getTestFiles(workspace, testResultFiles.toString(), testFileList, startTime);

					for (FilePath testFile : testFileList) {
						DevOpsTestSummary testSummary = createTestSummaryFromFile(testFile,
								stageModel.getName(),
								pipelineNameForPayload, this.model.getNumber(), startTime, this.model.getPronoun(), this.model.isMultiBranch(), scmModel.getBranch());

						if (testSummary != null) {
							this.model.addToTestSummaries(testSummary);
							testsAdded++;
						}
					}
				}
			}
		}
		return testsAdded;
	}

	public void removeTestSummariesForTestTypeMappings(int testsAdded) {
		this.model.removeFromTestSummaries(testsAdded);
	}

	private DevOpsTestSummary createTestSummaryFromFile(FilePath testFile, String stageName, String pipelineName,
	                                                    int buildNumber, long stageStarttime, String pronoun, String isMultiBranch, String branchName) {
		try {
			if (testFile == null)
				return null;

			LOGGER.log(Level.INFO, "DevOpsRunStatusAction.createTestSummaryFromFile(): Creating test summary from " +
					"file -" + testFile.getName());
			String fileString = testFile.readToString();

			String projectName = null;
			if (pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) ||
					pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString())) {
				projectName = pipelineName;
				pipelineName = null;
				stageName = projectName;
			}

			DevOpsTestSummary testSummary =
					new DevOpsTestSummary.Builder(testFile.getName())
							.inStage(stageName)
							.inPipeline(pipelineName)
							.inProject(projectName)
							.buildNumber(buildNumber)
							.start(stageStarttime)
							.finish(testFile.lastModified())
							.branchName(branchName)
							.multiBranch(isMultiBranch)
							.fileContent(fileString)
							.build();

			return testSummary;
		} catch (Exception ignore) {
			LOGGER.log(Level.WARNING, "DevOpsRunStatusAction.createTestSummaryFromFile()- Error getting test " +
					"report content - " + ignore.getMessage());
		}
		return null;
	}


	private void getTestFiles(FilePath ws, String testResults, List<FilePath> fileList,
	                          long stageStartTime) {
		try {
			LOGGER.log(Level.INFO, "DevOpsRunStatusAction.getTestFiles: testResults-" + testResults);

			FileSet fs = Util.createFileSet(new File(ws.getRemote()), testResults);
			DirectoryScanner ds = fs.getDirectoryScanner();
			String[] files = ds.getIncludedFiles();
			LOGGER.log(Level.INFO, "DevOpsRunStatusAction.getTestFiles: files-" + files.length);
			for (String relPath : files) {
				FilePath reportFile = new FilePath(ws, relPath);
				if (reportFile.lastModified() > stageStartTime) {
					fileList.add(reportFile);
				}
			}
			LOGGER.log(Level.INFO, "DevOpsRunStatusAction.getTestFiles: fileList-" + fileList.size());


		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static boolean stringContainsItemFromList(String inputStr, String[] items) {
		return Arrays.stream(items).anyMatch(inputStr::contains);
	}

	private WorkspaceAction getWorkspaceAction(final Run<?, ?> run) {
		WorkspaceAction wsAction = null;
		if (run instanceof WorkflowRun) {
			FlowExecution exec = ((WorkflowRun) run).getExecution();
			if (exec != null) {
				FlowGraphWalker w = new FlowGraphWalker(exec);
				for (FlowNode n : w) {
					if (n instanceof StepStartNode) {
						wsAction = n.getAction(WorkspaceAction.class);
						if (wsAction != null) return wsAction;
					}
				}
			}
		}
		return wsAction;
	}

	public DevOpsRunStatusJobModel createRunStatusJob(final Run<?, ?> run) {
		DevOpsRunStatusJobModel status = new DevOpsRunStatusJobModel();
		if (run != null) {
			Job<?, ?> job = run.getParent();
			if (job != null) {
				status.setName(job.getName());
				status.setUrl(job.getAbsoluteUrl());
				status.setFullName(job.getFullName());
			}
		}
		return status;
	}

    /*
API (wfapi) for individual stage execution example (build #6, stageId #6)
http://localhost:8090/jenkins/job/felipe-pipeline/6/execution/node/6/wfapi/describe
 */

	public DevOpsRunStatusStageModel createRunStatusStage(FlowNode fn, final Run<?, ?> run,
	                                                      String phase, boolean isStageStart,
	                                                      DevOpsPipelineNode devOpsPipelineNode) {
		DevOpsRunStatusStageModel stageModel = new DevOpsRunStatusStageModel();
		if (run != null && fn != null && phase != null && devOpsPipelineNode != null) {
			stageModel.setPhase(phase);
			if (isStageStart) {
				setSeenId(((StepStartNode) fn).getId());
				stageModel.setId(((StepStartNode) fn).getId());

				stageModel.setDuration(0);
				stageModel.setTimestamp(System.currentTimeMillis());

				setStageModelDetailsFromPipelineNode(devOpsPipelineNode, stageModel);

				WorkspaceAction wsAction = getWorkspaceAction(run);
				devOpsPipelineNode.setWsAction(wsAction);
				devOpsPipelineNode.setStartTime(stageModel.getTimestamp());

			} else {
				StepStartNode startNode = ((StepEndNode) fn).getStartNode();
				if (startNode != null) {
					stageModel.setId(startNode.getId());
					long duration = getTime(startNode, (StepEndNode) fn);
					stageModel.setDuration(duration);

					// Set duration directly on the pipeline node
					// Note: devOpsPipelineNode is known to be non-null in this context
					devOpsPipelineNode.setDuration(duration);
					LOGGER.log(Level.FINE, "Set duration " + duration + "ms on pipeline node " + devOpsPipelineNode.getId());

					setStageModelDetailsFromPipelineNode(devOpsPipelineNode, stageModel);
					stageModel.setWaitForChildExecutions(pipelineGraph.getWaitForChildExecutions(devOpsPipelineNode.getId()));
				}
				stageModel.setTimestamp(System.currentTimeMillis());//run.getTimeInMillis());

				//get log for stage
				List<FlowNode> parents = fn.getParents();
				if (parents != null) {
					try {
						String logPreamble = "[Stage:" + stageModel.getName() + "] \n\n";
						int logPreambleLen = logPreamble.getBytes(StandardCharsets.UTF_8).length;

						Deque<FlowNode> nodeStack = new LinkedList<>();
						for (FlowNode fnode : parents) {
							nodeStack.addFirst(fnode);
						}

						Deque<String> logStack = new LinkedList<>();
						getLogForStage(nodeStack, startNode, logStack, new int[]{0},
								LOG_SIZE_LIMIT - logPreambleLen);
						logStack.addFirst(logPreamble);

						List logList = new ArrayList<String>(logStack);
						stageModel.setLog(logList);

					} catch (UnsupportedEncodingException ignore) {
						LOGGER.log(Level.WARNING,
								" createRunStatusStage - Error when extracting log | " + ignore.getMessage());
					}
				}

				ErrorAction error = fn.getError();
				DevOpsPipelineNode nodeById =
						this.pipelineGraph.getNodeById(stageModel.getId());
				if (error != null) // && !error.getDisplayName().isEmpty()
				{
					stageModel.setResult(Result.FAILURE.toString());
					if (null != nodeById)
						nodeById.setStageExecStatus(DevOpsConstants.STAGE_RUN_FAILURE.toString());
				} else {
					stageModel.setResult(Result.SUCCESS.toString());
					if (null != nodeById)
						nodeById.setStageExecStatus(DevOpsConstants.STAGE_RUN_COMPLETED.toString());
				}

			}
		}
		return stageModel;
	}

	private void setStageModelDetailsFromPipelineNode(DevOpsPipelineNode devOpsPipelineNode, DevOpsRunStatusStageModel stageModel) {
		if (devOpsPipelineNode != null && stageModel != null) {

			stageModel.setName(devOpsPipelineNode.getName());
			stageModel.setUrl(DevOpsPipelineGraph.getStageExecutionUrl(devOpsPipelineNode.getPipelineExecutionUrl(), devOpsPipelineNode.getId()));

			if (devOpsPipelineNode.getUpstreamTaskExecURL() != null)
				stageModel.setUpstreamTaskExecutionURL(devOpsPipelineNode.getUpstreamTaskExecURL());
			if (devOpsPipelineNode.getUpstreamStageName() != null)
				stageModel.setUpstreamStageName(devOpsPipelineNode.getUpstreamStageName());
			if (devOpsPipelineNode.getParentExecutionUrl() != null)
				stageModel.setParentExecutionUrl(devOpsPipelineNode.getParentExecutionUrl());
			if (devOpsPipelineNode.getPipelineExecutionUrl() != null)
				stageModel.setPipelineExecutionUrl(devOpsPipelineNode.getPipelineExecutionUrl());
			if (devOpsPipelineNode.getParentName() != null)
				stageModel.setParentStageName(devOpsPipelineNode.getParentName());
		}
	}


	private void getLogForStage(Deque<FlowNode> nodeStack, StepStartNode startNode,
	                            Deque<String> logQueue,
	                            int[] currSize, int sizeLimit) throws UnsupportedEncodingException {

		while (!nodeStack.isEmpty() && currSize[0] < sizeLimit) {

			FlowNode stageNode = nodeStack.removeFirst();

			extractAndAddLog(stageNode, logQueue, currSize, sizeLimit);

			List<FlowNode> stageNodeParents = stageNode.getParents();
			if (!CollectionUtils.isEmpty(stageNodeParents)) {

				if (stageNodeParents.size() > 1) {
					class FlowNodeEnd {
						Long endTime;
						FlowNode fnode;

						FlowNodeEnd(Long et, FlowNode fn) {
							this.endTime = et;
							this.fnode = fn;
						}

					}
					Comparator<FlowNodeEnd> endTimeComparator = (s1, s2) -> {
						return s2.endTime.compareTo(s1.endTime);
					};
					PriorityQueue<FlowNodeEnd> fnPriorityQueue =
							new PriorityQueue<>(endTimeComparator);
					for (FlowNode fnode : stageNodeParents) {
						long endTime = 0;
						if (fnode instanceof StepEndNode) {
							TimingAction timeAction = fnode.getAction(TimingAction.class);
							endTime = timeAction != null ? timeAction.getStartTime() : 0;
						}
						fnPriorityQueue.add(new FlowNodeEnd(endTime, fnode));
					}

					List<FlowNode> orderedParents = new ArrayList<>();
					while (!fnPriorityQueue.isEmpty()) {
						orderedParents.add(fnPriorityQueue.remove().fnode);
					}
					stageNodeParents = orderedParents;
				}

				FlowNode parentNode = stageNodeParents.get(0);

				// depth-first to go through all child nodes and add to lof
				while (parentNode != null && !parentNode.equals(startNode) &&
						!CollectionUtils.isEmpty(parentNode.getParents()) &&
						currSize[0] < sizeLimit) {
					extractAndAddLog(parentNode, logQueue, currSize, sizeLimit);
					parentNode = parentNode.getParents().get(0);
				}

				// add remaining chidren of stageNode to stack
				if (stageNodeParents.size() > 1 && currSize[0] < sizeLimit) {

					for (int i = 1; i < stageNodeParents.size(); i++) {
						nodeStack.push(stageNodeParents.get(i));
					}
				}
			}
		}
	}

	private void extractAndAddLog(FlowNode fn, Deque<String> logQueue, int[] currSize,
	                              int sizeLimit) throws UnsupportedEncodingException {

		if (fn == null || currSize[0] >= sizeLimit)
			return;

		if (fn instanceof StepStartNode && ((StepStartNode) fn).getStepName().equals("Stage")) {
			String stageName = getNodeName(fn);
			if (!"Stage : Start".equals(stageName)) {
				addLogEntry("\n[[" + getNodeName(fn) + "]]\n", logQueue, currSize, sizeLimit);
			}
		}

		LogAction logAction = fn.getAction(LogAction.class);
		if (logAction != null) {
			AnnotatedLargeText<? extends FlowNode> logText = logAction.getLogText();
			String logStr = extractLog(logText);
			addLogEntry(logStr, logQueue, currSize, sizeLimit);
		}
	}


	private void addLogEntry(String logStr, Deque<String> logQueue, int[] currSize, int sizeLimit)
			throws
			UnsupportedEncodingException {
		int logLineLen = logStr.getBytes(StandardCharsets.UTF_8).length;
		if (currSize[0] + logLineLen > sizeLimit) {
			logStr = getLastChunk(logStr, sizeLimit - currSize[0]);
			logLineLen = logStr.getBytes(StandardCharsets.UTF_8).length;
		}

		if (logLineLen > 0)
			logQueue.addFirst(logStr);

		currSize[0] = currSize[0] + logLineLen;
	}

	private String extractLog(AnnotatedLargeText largeText) throws UnsupportedEncodingException {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			largeText.writeLogTo(0, baos);
		} catch (FileNotFoundException x) {
			LOGGER.log(Level.INFO, " log file not created yet");
			return ""; // log file not yet created, OK
		} catch (IOException e) {
			LOGGER.log(Level.INFO, " error reading the log");
			return "";
		}

		String logEntry = new String(baos.toByteArray(), StandardCharsets.UTF_8);
		LOGGER.log(Level.INFO, " logEntry :" + logEntry);

		return logEntry;
	}


	public static String getLastChunk(String original, int chunkSize)
			throws UnsupportedEncodingException {
		final int end = original.length();
		int from = 0, to = end;

		from = end - chunkSize < 0 ? 0 : end - chunkSize;
		String chunk = original.substring(from, to); // get chunk
		while (chunk.getBytes(StandardCharsets.UTF_8).length >
				chunkSize) { // adjust chunk to proper byte size if necessary
			chunk = original.substring(from++, to);
		}

		return chunk;
	}

	public DevOpsRunStatusSCMModel createRunStatusSCM(final Run<?, ?> run, EnvVars vars) {
		DevOpsRunStatusSCMModel status = new DevOpsRunStatusSCMModel();
		if (run != null && vars != null) {
			if (vars.get("GIT_URL") != null)
				status.setUrl(vars.get("GIT_URL"));

			if (vars.get("BRANCH_NAME") != null)
				status.setBranch(vars.get("BRANCH_NAME"));
			else if (vars.get("GIT_BRANCH") != null)
				status.setBranch(vars.get("GIT_BRANCH"));

			if (vars.get("GIT_COMMIT") != null)
				status.setCommit(vars.get("GIT_COMMIT"));
			
			// If we're missing repository URL and this is a multibranch pipeline job, try to get it from Git BuildData
			if (GenericUtils.isEmpty(status.getUrl()) && GenericUtils.isMultiBranch(run.getParent())) {
				populateSCMInfoFromBuildData(run, status);
			}
			
			status.setChanges(getChangedFiles(run));
			status.setCulprits(getCulprits(run));
		}
		return status;
	}
	
	/**
	 * Helper method to extract SCM information from Git BuildData action when environment variables
	 * don't provide the necessary information.
	 * 
	 * @param run The build run
	 * @param status The SCM model to populate
	 */
	private void populateSCMInfoFromBuildData(final Run<?, ?> run, DevOpsRunStatusSCMModel status) {
		try {
			for (Action runAction : run.getAllActions()) {
				if (runAction.getClass().getName().equalsIgnoreCase("hudson.plugins.git.util.BuildData")) {
					// Get repository URL if not already set
					if (GenericUtils.isEmpty(status.getUrl())) {
						try {
							Method[] methods = runAction.getClass().getMethods();
							Map<String, Method> methodMap = new HashMap<String, Method>();
							for (Method m : methods) {
								methodMap.put(m.getName(), m);
							}
							
							if (methodMap.containsKey("getRemoteUrls")) {
								Method m = methodMap.get("getRemoteUrls");
								Object remoteUrls = m.invoke(runAction);
								if (remoteUrls instanceof Set && !((Set<?>) remoteUrls).isEmpty()) {
									String url = ((Set<?>) remoteUrls).iterator().next().toString();
									status.setUrl(url);
								}
							}
						} catch (Exception e) {
							LOGGER.log(Level.FINE, "Failed to get remote URLs: " + e.getMessage());
						}
					}
					break; // Found the BuildData action, no need to continue
				}
			}
		} catch (RuntimeException ignore) {
			LOGGER.log(Level.WARNING, "Error extracting SCM info from BuildData: " + ignore.getMessage());
		} catch (Exception ignore) {
			LOGGER.log(Level.WARNING, "Exception while extracting SCM info from BuildData: " + ignore.getMessage());
		}
	}

	public List<DevOpsJFrogModel> getJfrogBuildDetails(final Run<?, ?> run, String stageNodeId) {
		List<DevOpsJFrogModel> finalJfrogModelList = new ArrayList<>();

		try {
			for (Action runAction : run.getAllActions()) {
				if (runAction.getClass().getName().equalsIgnoreCase("org.jfrog.hudson.BuildInfoResultAction")) {

					Method[] methods = runAction.getClass().getMethods();
					List<Object> publishedBuildDetailsList = null;

					Map<String, Method> methodMap = new HashMap<String, Method>();
					for (Method m : methods) {
						methodMap.put(m.getName(), m);
					}

					if (methodMap.containsKey(DevOpsConstants.GET_PUBLISHED_BUILDS_DETAILS.toString())) {
						Method m = methodMap.get(DevOpsConstants.GET_PUBLISHED_BUILDS_DETAILS.toString());
						publishedBuildDetailsList = (List<Object>) m.invoke(runAction);
					}

					if (CollectionUtils.isNotEmpty(publishedBuildDetailsList)) {

						for (Object publishedBuildDetails : publishedBuildDetailsList) {
							String artifactoryUrl;
							Field artifactoryUrlPrivateField = publishedBuildDetails.getClass().getDeclaredField("artifactoryUrl");
							artifactoryUrlPrivateField.setAccessible(true);
							artifactoryUrl = (String) artifactoryUrlPrivateField.get(publishedBuildDetails);
							//Removing  suffix form url https://clouldinstnace.jfrog.io/artifactory
							artifactoryUrl = artifactoryUrl.replaceAll("/artifactory$", "");

							String buildName;
							Field buildNamePrivateField = publishedBuildDetails.getClass().getDeclaredField("buildName");
							buildNamePrivateField.setAccessible(true);
							buildName = (String) buildNamePrivateField.get(publishedBuildDetails);

							String buildNumber;
							Field buildNumberPrivateField = publishedBuildDetails.getClass().getDeclaredField("buildNumber");
							buildNumberPrivateField.setAccessible(true);
							buildNumber = (String) buildNumberPrivateField.get(publishedBuildDetails);

							String startedTimeStamp = "";
							Field[] fields = publishedBuildDetails.getClass().getDeclaredFields();
							if (Arrays.stream(fields).anyMatch(field -> field.getName().equals(DevOpsConstants.STARTED_TIMESTAMP.toString()))) {
								Field startedTimeStampPrivateField = publishedBuildDetails.getClass().getDeclaredField(DevOpsConstants.STARTED_TIMESTAMP.toString());
								startedTimeStampPrivateField.setAccessible(true);
								startedTimeStamp = (String) startedTimeStampPrivateField.get(publishedBuildDetails);
							}

							DevOpsJFrogModel jFrogModel = new DevOpsJFrogModel(buildName, buildNumber, startedTimeStamp, artifactoryUrl, stageNodeId);
							finalJfrogModelList.add(jFrogModel);
						}
					}
				}
			}
		} catch (RuntimeException ignore) {
			LOGGER.log(Level.WARNING, " DevOpsRunStatusAction.getJfrogBuildDetails()- RunTime Exception :  "
					+ ignore.getMessage());
		} catch (Exception ignore) {
			LOGGER.log(Level.WARNING, " DevOpsRunStatusAction.getJfrogBuildDetails()- Exception occured :  "
					+ ignore.getMessage());
		}
		return finalJfrogModelList;
	}

	public List<DevOpsSonarQubeModel> getSonarQubeAnalysis(final Run<?, ?> run, String stageName, String pipelineName,
	                                                       int buildNumber, long stageEndTime, String blockId, String pronoun, String isMultiBranch,
	                                                       String branchName) {
		List<DevOpsSonarQubeModel> finalSonarQubeModelList = new ArrayList<>();
		try {
			for (Action sonarAction : run.getAllActions()) {
				if (sonarAction.getClass().getName().equalsIgnoreCase("hudson.plugins.sonar.action.SonarAnalysisAction")) {
					DevOpsSonarQubeModel sonarModel = new DevOpsSonarQubeModel();
					Method[] methods = sonarAction.getClass().getMethods();
					String ceTaskId = null;
					// Using StringBuffer directly for URL building
					StringBuffer urlBuilder = new StringBuffer();

					Map<String, Method> methodMap = new HashMap<String, Method>();
					for (Method m : methods) {
						methodMap.put(m.getName(), m);
					}

					if (methodMap.containsKey("getCeTaskId")) {
						Method m = methodMap.get("getCeTaskId");
						ceTaskId = m.invoke(sonarAction).toString();
					}

					if (methodMap.containsKey("getServerUrl")) {
						Method m = methodMap.get("getServerUrl");
						urlBuilder = urlBuilder.append(m.invoke(sonarAction).toString());
					} else if (methodMap.containsKey("getInstallationUrl")) {
						Method m = methodMap.get("getInstallationUrl");
						urlBuilder = urlBuilder.append(m.invoke(sonarAction).toString());
					} else if (methodMap.containsKey("getUrl")) {
						Method m = methodMap.get("getUrl");
						String tempUrl = m.invoke(sonarAction).toString(); //invoke sonarAnalysisAction getUrl() method
						String serverUrl = tempUrl.substring(0, tempUrl.indexOf("/dashboard"));
						urlBuilder = urlBuilder.append(serverUrl);
					}

					sonarModel.setScanID(ceTaskId);
					sonarModel.setUrl(urlBuilder.toString());
					sonarModel.setStageNodeId(blockId);
					finalSonarQubeModelList.add(sonarModel);
				}
			}
		} catch (RuntimeException ignore) {
			LOGGER.log(Level.WARNING, " DevOpsRunStatusAction.getSonarQubeAnalysis()- RunTime Exception :  "
					+ ignore.getMessage());
		} catch (Exception ignore) {
			LOGGER.log(Level.WARNING, " DevOpsRunStatusAction.getSonarQubeAnalysis()- Exception occured : "
					+ ignore.getMessage());
		}
		return finalSonarQubeModelList;
	}

	public List<DevOpsTestSummary> createTestSummary(final Run<?, ?> run, String stageName,
	                                                 String pipelineName, int buildNumber,
	                                                 long stageEndTime, String blockId,
	                                                 String pronoun, String isMultiBranch, String branchName) {
		List<DevOpsTestSummary> finalTestSummaryList = new ArrayList<>();
		try {
			List<? extends Action> testActions = run.getAllActions().stream()
					.filter(act -> act instanceof AbstractTestResultAction).collect(
							Collectors.toList());
			for (Action action : testActions) {
				AbstractTestResultAction testAction = (AbstractTestResultAction) action;
				if (testAction != null) {
					if (AggregatedTestResultAction.class.isInstance(testAction)) {
						AggregatedTestResultAction aggtestAction =
								(AggregatedTestResultAction) testAction;
						List<AggregatedTestResultAction.ChildReport> childReports =
								aggtestAction.getChildReports();
						for (AggregatedTestResultAction.ChildReport childReport : childReports) {
							List<DevOpsTestSummary> testSummaryList =
									processTestResult(childReport.run, testAction,
											childReport.result, stageName, pipelineName,
											buildNumber,
											stageEndTime, blockId, pronoun, isMultiBranch, branchName);
							if (testSummaryList != null && testSummaryList.size() > 0)
								finalTestSummaryList.addAll(testSummaryList);
						}
					} else {
						List<DevOpsTestSummary> testSummaryList = processTestResult(run, testAction,
								testAction.getResult(), stageName, pipelineName, buildNumber,
								stageEndTime, blockId, pronoun, isMultiBranch, branchName);
						if (testSummaryList != null && testSummaryList.size() > 0)
							finalTestSummaryList.addAll(testSummaryList);
					}
				}
			}
		} catch (Exception ignore) {
			LOGGER.log(Level.WARNING,
					" DevOpsRunStatusAction.createTestSummary()- Error when extracting " +
							"testResults | " + ignore.getMessage());
		}
		return finalTestSummaryList;
	}

	private List<DevOpsTestSummary> processTestResult(Run run,
	                                                  AbstractTestResultAction testAction, Object result, String stageName,
	                                                  String pipelineName, int buildNumber,
	                                                  long stageEndTime, String blockId, String pronoun, String isMultiBranch, String branchName) {
		if (run == null || result == null) {
			return null;
		}
		List<DevOpsTestSummary> devOpsTestSummaryList = new ArrayList<>();
		try {

			TabulatedResult testResult = (TabulatedResult) result;

			if ((pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString()) ||
					pronoun.equalsIgnoreCase(DevOpsConstants.PIPELINE_PRONOUN.toString()) ||
					pronoun.equalsIgnoreCase(
							DevOpsConstants.BITBUCKET_MULTI_BRANCH_PIPELINE_PRONOUN
									.toString())) && testResult.hasMultipleBlocks()) {
				PipelineBlockWithTests aBlock =
						testResult.getPipelineBlockWithTests(blockId);
				if (aBlock == null) {
					return devOpsTestSummaryList;
				}
			}

			String projectName = null;
			if (pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) ||
					pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString())) {
				projectName = pipelineName;
				pipelineName = null;
				stageName = projectName;
			}

			Collection<? extends hudson.tasks.test.TestResult> packageResults = testResult.getChildren();
			for (hudson.tasks.test.TestResult packageResult : packageResults) { // packageresult

				TabulatedResult tabResult = (TabulatedResult) packageResult;
				String reptUrl =
						Jenkins.getInstance().getRootUrl() + run.getUrl() + getResultUrl(tabResult) + "/" + tabResult.getSafeName();
				long startTime = stageEndTime - (long) (tabResult.getDuration() * 1000);

				DevOpsTestSummary testSummary =
						new DevOpsTestSummary.Builder(tabResult.getName())
								.total(tabResult.getTotalCount())
								.failed(tabResult.getFailCount())
								.passed(tabResult.getPassCount())
								.skipped(tabResult.getSkipCount())
								.duration(tabResult.getDuration())
								.inStage(stageName)
								.inPipeline(pipelineName)
								.inProject(projectName)
								.buildNumber(buildNumber)
								.start(startTime)
								.finish(stageEndTime)
								.reportUrl(reptUrl)
								.branchName(branchName)
								.multiBranch(isMultiBranch)
								.stageNodeId(blockId)
								.build();

				devOpsTestSummaryList.add(testSummary);
			}
		} catch (ClassCastException e) {
			LOGGER.log(Level.WARNING, "Got ClassCast exception while converting results to Tabulated Result from action: " +
					testAction.getClass().getName() + ". Ignoring as we only want test results for processing.");
		} catch (Exception ignore) {
			LOGGER.log(Level.WARNING,
					" DevOpsRunStatusAction.processTestResult() - Error when extracting " +
							"testResults | " + ignore.getMessage());
		}
		return devOpsTestSummaryList;
	}

	protected String getResultUrl(TabulatedResult result) {
		boolean isTestng = result.getClass().getName().startsWith("hudson.plugins.testng.results");
		if (isTestng) {
			return "testngreports";
		} else {
			return "testReport";
		}
	}

	public DevOpsRunStatusTestModel createRunStatusTest(final Run<?, ?> run, String pronoun,
	                                                    String stageId, String stageName) {
		DevOpsRunStatusTestModel status = new DevOpsRunStatusTestModel();
		if (run != null) {
			TestResultAction testResultAction = run.getAction(TestResultAction.class);
			if (testResultAction != null) {
				TestResult testResult = testResultAction.getResult();
				if (testResult != null) {
					status.setDuration(testResult.getDuration());
					status.setName(testResult.getDisplayName() + " - " + testResult.getName());
					if (pronoun.equalsIgnoreCase(DevOpsConstants.PULL_REQUEST_PRONOUN.toString()) ||
							pronoun.equalsIgnoreCase(DevOpsConstants.PIPELINE_PRONOUN.toString()) ||
							pronoun.equalsIgnoreCase(
									DevOpsConstants.BITBUCKET_MULTI_BRANCH_PIPELINE_PRONOUN
											.toString())) {
						if (stageId != null && stageName != null && !stageId.isEmpty() &&
								!stageName.isEmpty()) {
							int passed = 0;
							int skipped = 0;
							int failed = 0;
							int regression = 0;
							int fixed = 0;

							Collection<SuiteResult> suiteResults = testResult.getSuites();
							for (SuiteResult suiteResult : suiteResults) {
								if (suiteResult != null) {
									List<String> blockIds = suiteResult.getEnclosingBlocks();
									List<String> blockNames = suiteResult.getEnclosingBlockNames();
									if (blockIds != null && blockNames != null) {
										if (blockIds.size() >= 1 && blockNames.size() >= 1) {
											if (blockIds.contains(stageId.trim()) &&
													blockNames.contains(stageName.trim())) {
												DevOpsRunStatusTestSuiteModel suite =
														new DevOpsRunStatusTestSuiteModel();
												suite.setDuration(suiteResult.getDuration());
												suite.setName(suiteResult.getName());
												suite.setStdErr(suiteResult.getStderr());
												suite.setStdOut(suiteResult.getStdout());
												List<CaseResult> caseResults =
														suiteResult.getCases();
												for (CaseResult caseResult : caseResults) {
													if (caseResult != null) {
														DevOpsRunStatusTestCaseModel _case =
																new DevOpsRunStatusTestCaseModel();
														_case.setClassName(
																caseResult.getClassName());
														_case.setName(caseResult.getName());
														_case.setDuration(caseResult.getDuration());
														_case.setErrorDetails(
																caseResult.getErrorDetails());
														_case.setErrorStackTrace(
																caseResult.getErrorStackTrace());
														CaseResult.Status caseStatus =
																caseResult.getStatus();
														if (caseStatus != null) {
															if (caseStatus.getMessage()
																	.equalsIgnoreCase(
																			CaseResult.Status.FAILED
																					.toString()))
																failed += 1;
															else if (caseStatus.getMessage()
																	.equalsIgnoreCase(
																			CaseResult.Status.PASSED
																					.toString()))
																passed += 1;
															else if (caseStatus.getMessage()
																	.equalsIgnoreCase(
																			CaseResult.Status.SKIPPED
																					.toString()))
																skipped += 1;
															else if (caseStatus.getMessage()
																	.equalsIgnoreCase(
																			CaseResult.Status.REGRESSION
																					.toString()))
																regression += 1;
															else if (caseStatus.getMessage()
																	.equalsIgnoreCase(
																			CaseResult.Status.FIXED
																					.toString()))
																fixed += 1;
															_case.setStatus(
																	caseStatus.getMessage());
														}
														List<DevOpsRunStatusTestCaseModel> cases =
																suite.getCases();
														if (cases == null)
															cases =
																	new ArrayList<DevOpsRunStatusTestCaseModel>();
														cases.add(_case);
														suite.setCases(cases);
													}
												}
												List<DevOpsRunStatusTestSuiteModel> suites =
														status.getSuites();
												if (suites == null)
													suites =
															new ArrayList<DevOpsRunStatusTestSuiteModel>();
												suites.add(suite);
												status.setSuites(suites);
											}
										} else {
											printDebug("createRunStatusTest",
													new String[]{"blockIds"},
													new String[]{blockIds.toString()}, Level.FINE);
											printDebug("createRunStatusTest",
													new String[]{"blockNames"},
													new String[]{blockNames.toString()}, Level.FINE);
										}
									}
								}
							}

							status.setFailed(failed);
							status.setPassed(passed);
							status.setSkipped(skipped);
							status.setFixed(fixed);
							status.setRegression(regression);
							status.setTotal(failed + passed + skipped + fixed + regression);
						}
					} else if (pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) ||
							pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString())) {
						int passed = 0;
						int skipped = 0;
						int failed = 0;
						int regression = 0;
						int fixed = 0;
						Collection<SuiteResult> suiteResults = testResult.getSuites();
						for (SuiteResult suiteResult : suiteResults) {
							if (suiteResult != null) {
								DevOpsRunStatusTestSuiteModel suite =
										new DevOpsRunStatusTestSuiteModel();
								suite.setDuration(suiteResult.getDuration());
								suite.setName(suiteResult.getName());
								suite.setStdErr(suiteResult.getStderr());
								suite.setStdOut(suiteResult.getStdout());
								List<CaseResult> caseResults = suiteResult.getCases();
								for (CaseResult caseResult : caseResults) {
									if (caseResult != null) {
										DevOpsRunStatusTestCaseModel _case =
												new DevOpsRunStatusTestCaseModel();
										_case.setClassName(caseResult.getClassName());
										_case.setName(caseResult.getName());
										_case.setDuration(caseResult.getDuration());
										_case.setErrorDetails(caseResult.getErrorDetails());
										_case.setErrorStackTrace(caseResult.getErrorStackTrace());
										CaseResult.Status caseStatus = caseResult.getStatus();
										if (caseStatus != null) {
											if (caseStatus.getMessage().equalsIgnoreCase(
													CaseResult.Status.FAILED.toString()))
												failed += 1;
											else if (caseStatus.getMessage().equalsIgnoreCase(
													CaseResult.Status.PASSED.toString()))
												passed += 1;
											else if (caseStatus.getMessage().equalsIgnoreCase(
													CaseResult.Status.SKIPPED.toString()))
												skipped += 1;
											else if (caseStatus.getMessage().equalsIgnoreCase(
													CaseResult.Status.REGRESSION.toString()))
												regression += 1;
											else if (caseStatus.getMessage().equalsIgnoreCase(
													CaseResult.Status.FIXED.toString()))
												fixed += 1;
											_case.setStatus(caseStatus.getMessage());
										}
										List<DevOpsRunStatusTestCaseModel> cases = suite.getCases();
										if (cases == null)
											cases = new ArrayList<DevOpsRunStatusTestCaseModel>();
										cases.add(_case);
										suite.setCases(cases);
									}
								}
								List<DevOpsRunStatusTestSuiteModel> suites = status.getSuites();
								if (suites == null)
									suites = new ArrayList<DevOpsRunStatusTestSuiteModel>();
								suites.add(suite);
								status.setSuites(suites);
							}
						}
						status.setFailed(failed);
						status.setPassed(passed);
						status.setSkipped(skipped);
						status.setFixed(fixed);
						status.setRegression(regression);
						status.setTotal(failed + passed + skipped + fixed + regression);
					}
				}
			}
		}
		return status;
	}

	public long getTime(FlowNode startNode, FlowNode endNode) {
		TimingAction startTime = startNode.getAction(TimingAction.class);
		TimingAction endTime = endNode.getAction(TimingAction.class);
		if (startTime != null && endTime != null) {
			return endTime.getStartTime() - startTime.getStartTime();
		}
		return 0;
	}

	public String getNodeName(FlowNode startNode) {
		String nodeName = "";
		if (startNode != null) {
			LabelAction label = startNode.getAction(LabelAction.class);
			if (label != null)
				nodeName = label.getDisplayName();
			else
				nodeName = startNode.getDisplayName();
		}
		return nodeName;
	}

	public List<String> getChangedFiles(Run<?, ?> run) {
		List<String> affectedPaths = new ArrayList<>();
		if (run instanceof AbstractBuild) {
			AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
			Object[] items = build.getChangeSet().getItems();
			if (items != null && items.length > 0) {
				for (Object o : items) {
					if (o instanceof ChangeLogSet.Entry)
						affectedPaths.addAll(((ChangeLogSet.Entry) o).getAffectedPaths());
				}
			}
		}
		return affectedPaths;
	}

	public List<String> getCulprits(Run<?, ?> run) {
		List<String> culprits = new ArrayList<>();
		if (run instanceof AbstractBuild) {
			AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
			Set<User> buildCulprits = build.getCulprits();
			for (User user : buildCulprits)
				culprits.add(user.getId());
		}
		return culprits;
	}

	private void printDebug(String methodName, String[] variables, String[] values, Level logLevel) {
		GenericUtils
				.printDebug(DevOpsRunStatusAction.class.getName(), methodName, variables, values,
						logLevel);
	}

	public DevOpsPipelineGraph getPipelineGraph() {
		return pipelineGraph;
	}

	public void setPipelineGraph(DevOpsPipelineGraph pipelineGraph) {
		this.pipelineGraph = pipelineGraph;
	}

	public List<DevOpsSecurityResultModel> getVeracodeModels(final Run<?, ?> run, String stageName, String pipelineName,
	                                                         int buildNumber, long stageEndTime, String blockId, String pronoun, String isMultiBranch,
	                                                         String branchName) {
		List<DevOpsSecurityResultModel> veracodeModels = new ArrayList<>();

		try {
			for (Action runAction : run.getAllActions()) {
				if (runAction.getClass().getName().equalsIgnoreCase("com.veracode.jenkins.plugin.VeracodeAction")) {

					Method[] methods = runAction.getClass().getMethods();

					Map<String, Method> methodMap = new HashMap<String, Method>();
					for (Method m : methods) {
						methodMap.put(m.getName(), m);
					}

					if (methodMap.containsKey("getDetailedReportURLForHTMLAttr")) {
						Method m = methodMap.get("getDetailedReportURLForHTMLAttr");
						String detailedURL = (String) m.invoke(runAction);
						String[] urlParts = detailedURL.split(":");
						if (urlParts.length > 3) {
							String buildId = urlParts[urlParts.length - 1];
							String appId = urlParts[urlParts.length - 2];
							JSONObject attributes = new JSONObject();
							attributes.put(DevOpsConstants.SEC_TOOL_SCANNER.toString(), DevOpsConstants.VERACODE.toString());
							attributes.put(DevOpsConstants.VERACODE_APP_ID.toString(), appId);
							attributes.put(DevOpsConstants.VERACODE_BUILD_ID.toString(), buildId);
							attributes.put(DevOpsConstants.CREATE_IBE.toString(), true);
							DevOpsSecurityResultModel model = new DevOpsSecurityResultModel(attributes.toString());
							model.setStageNodeId(blockId);
							veracodeModels.add(model);
						}
					}
				}
			}
		} catch (RuntimeException ignore) {
			LOGGER.log(Level.WARNING, " DevOpsRunStatusAction.getVeracodeModels()- RunTime Exception :  "
					+ ignore.getMessage());
		} catch (Exception ignore) {
			LOGGER.log(Level.WARNING, " DevOpsRunStatusAction.getVeracodeModels()- Exception occured :  "
					+ ignore.getMessage());
		}

		return veracodeModels;
	}

	public List<DevOpsSecurityResultModel> getCheckmarxModels(final Run<?, ?> run, String stageName, String pipelineName,
	                                                          int buildNumber, long stageEndTime, String blockId, String pronoun, String isMultiBranch,
	                                                          String branchName) {
		List<DevOpsSecurityResultModel> checkmarxModels = new ArrayList<>();
		String scanId = null, projectId = null;
		try {
			for (Action runAction : run.getAllActions()) {
				LOGGER.log(Level.INFO, runAction.getClass().getName());
				if (runAction.getClass().getName().equalsIgnoreCase("com.checkmarx.jenkins.CheckmarxScanResultsAction")) {
					Method[] methods = runAction.getClass().getMethods();

					Map<String, Method> methodMap = new HashMap<String, Method>();
					for (Method m : methods) {
						methodMap.put(m.getName(), m);
					}

					if (methodMap.containsKey("getResultsSummary")) {
						Method m = methodMap.get("getResultsSummary");

						if (m.invoke(runAction) != null) {
							Map<String, Method> resultsMethodMap = new HashMap<String, Method>();
							methods = m.invoke(runAction).getClass().getMethods();

							for (Method method : methods) {
								resultsMethodMap.put(method.getName(), method);
							}

							if (resultsMethodMap.containsKey("getScanId")) {
								Method resultMethod = resultsMethodMap.get("getScanId");
								scanId = (String) resultMethod.invoke(m.invoke(runAction));
							}

							if (resultsMethodMap.containsKey("getProjectId")) {
								Method resultMethod = resultsMethodMap.get("getProjectId");
								projectId = (String) resultMethod.invoke(m.invoke(runAction));
							}

							if (scanId != null && projectId != null) {
								JSONObject attributes = new JSONObject();
								attributes.put(DevOpsConstants.SEC_TOOL_SCANNER.toString(),
										DevOpsConstants.CHECKMARX_ONE.toString());
								attributes.put(DevOpsConstants.CHECKMARX_SCAN_ID.toString(), scanId);
								attributes.put(DevOpsConstants.CHECKMARX_PROJECT_ID.toString(), projectId);
								attributes.put(DevOpsConstants.CREATE_IBE.toString(), true);
								DevOpsSecurityResultModel model = new DevOpsSecurityResultModel(attributes.toString());
								model.setStageNodeId(blockId);
								checkmarxModels.add(model);
							}
						}
					}
				}
			}
		} catch (RuntimeException ignore) {
			LOGGER.log(Level.WARNING, " DevOpsRunStatusAction.getCheckmarxModels()- RunTime Exception :  "
					+ ignore.getMessage());
		} catch (Exception ignore) {
			LOGGER.log(Level.WARNING, " DevOpsRunStatusAction.getCheckmarxModels()- Exception occurred :  "
					+ ignore.getMessage());
		}
		return checkmarxModels;
	}

	public List<DevOpsSecurityResultModel> getSecurityResultSteps(final Run<?, ?> run, String stageName, String pipelineName,
	                                                              int buildNumber, long stageEndTime, String blockId, String pronoun, String isMultiBranch,
	                                                              String branchName) {
		List<DevOpsSecurityResultModel> finalSecurityResultModel = new ArrayList<>();
		try {
			for (Action action : run.getAllActions()) {
				if (action.getClass().getName().equalsIgnoreCase(RegisterSecurityAction.class.getName())) {
					RegisterSecurityAction ra = (RegisterSecurityAction) action;
					DevOpsSecurityResultModel securityResultModel = new DevOpsSecurityResultModel(ra.getSecurityToolAttributes().toString());
					securityResultModel.setStageNodeId(blockId);
					finalSecurityResultModel.add(securityResultModel);
				}
			}
		} catch (RuntimeException ignore) {
			LOGGER.log(Level.WARNING, " DevOpsRunStatusAction.getSecurityResultModel()- RunTime Exception :  "
					+ ignore.getMessage());
		} catch (Exception ignore) {
			LOGGER.log(Level.WARNING, " DevOpsRunStatusAction.getSecurityResultModel()- Exception occured : "
					+ ignore.getMessage());
		}
		return finalSecurityResultModel;
	}

}