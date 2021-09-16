package io.jenkins.plugins;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.StageAction;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import com.google.common.util.concurrent.ListenableFuture;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Run.RunnerAbortedException;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import io.jenkins.plugins.config.DevOpsJobProperty;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.model.DevOpsNotificationModel;
import io.jenkins.plugins.model.DevOpsPipelineGraph;
import io.jenkins.plugins.model.DevOpsRunStatusModel;
import io.jenkins.plugins.model.DevOpsRunStatusStageModel;
import io.jenkins.plugins.model.DevOpsTestSummary;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;


@Extension
public class DevOpsRunListener extends RunListener<Run<?, ?>> {

	private DevOpsNotificationModel notificationModel;

	public static class DevOpsStageListener implements GraphListener {

		private final Run<?, ?> run;
		private final EnvVars vars;
		private DevOpsNotificationModel notificationModel;

		public DevOpsStageListener(Run<?, ?> run, EnvVars vars,
		                           DevOpsNotificationModel notificationModel) {
			this.run = run;
			this.vars = vars;
			this.notificationModel = notificationModel;
		}

		@Override
		public void onNewHead(FlowNode flowNode) {
			_printDebug("onNewHead", null, null, Level.FINE);
			if (isStageStart(flowNode)) {
				_printDebug("onNewHead", new String[]{"message"},
						new String[]{"stageStart"}, Level.FINE);
				if (!isDeclarativeStage(flowNode, true)) {

					String upstreamTaskExecutionURL = null;
					String upstreamStageName = null;
					DevOpsRunStatusAction action =
							run.getAction(DevOpsRunStatusAction.class);
					// If sending onStarted/onCompleted Run events, it should fall here (action already created by onStarted)
					if (action != null) {
						this.addToPipelineGraph(action, flowNode);
						DevOpsRunStatusModel previousModel = action.getModel();
						if (previousModel != null) {
							DevOpsRunStatusStageModel previousStageModel =
									previousModel.getStageModel();
							upstreamTaskExecutionURL = previousStageModel.getUrl();
							upstreamStageName = previousStageModel.getName();
						}

						//associate step to flowNode
						DevOpsModel devopsModel = new DevOpsModel();
						String stageName = devopsModel.getStageNameFromAction(run);
						devopsModel.associateStepToNode(run, stageName);

						// grab the upstream url from the previous DevOpsRunStatusStageModel
						DevOpsRunStatusModel model =
								action.createRunStatus(flowNode, run, vars, null,
										DevOpsConstants.NOTIFICATION_STARTED.toString(),
										true, upstreamTaskExecutionURL,
										upstreamStageName);
						action.setModel(model);
					}
					// If not sending onStarted/onCompleted Run events, it should fall here when it enters the first stage
					else {
						action = new DevOpsRunStatusAction();
						this.addToPipelineGraph(action, flowNode);
						DevOpsRunStatusModel model =
								action.createRunStatus(flowNode, run, vars, null,
										DevOpsConstants.NOTIFICATION_STARTED.toString(),
										true, upstreamTaskExecutionURL,
										upstreamStageName);
						action.setModel(model);
						run.addAction(action);
					}
					if (notificationModel != null)
						notificationModel.send(action.getModel());
				} else
					_printDebug("onNewHead", new String[]{"message"},
							new String[]{"Skipping declarative stage"}, Level.FINE);
			} else if (isStageEnd(flowNode)) {
				_printDebug("onNewHead", new String[]{"message"},
						new String[]{"stageEnd"}, Level.FINE);
				if (!isDeclarativeStage(flowNode, false)) {
					// check the start node to see if this stage was skipped
					StepStartNode startNode = ((StepEndNode) flowNode).getStartNode();
					String stageStatusFromTag = getStageStatusFromTag(startNode);
					String upstreamTaskExecutionURL = null;
					String upstreamStageName = null;
					DevOpsRunStatusAction action =
							run.getAction(DevOpsRunStatusAction.class);
					if (action != null) {
						this.markNodeToInactive(action, flowNode);
						DevOpsRunStatusModel previousModel = action.getModel();
						if (previousModel != null) {
							DevOpsRunStatusStageModel previousStageModel =
									previousModel.getStageModel();
							if (previousStageModel != null) {
								// for nested stages, there will be 2+ sequential "COMPLETED" events
								String previousPhase = previousStageModel.getPhase();
								if (previousPhase.equalsIgnoreCase(
										DevOpsConstants.NOTIFICATION_STARTED
												.toString())) {
									upstreamTaskExecutionURL = previousStageModel
											.getUpstreamTaskExecutionUrl();
									upstreamStageName =
											previousStageModel.getUpstreamStageName();
								} else if (previousPhase.equalsIgnoreCase(
										DevOpsConstants.NOTIFICATION_COMPLETED
												.toString())) {
									upstreamTaskExecutionURL =
											previousStageModel.getUrl();
									upstreamStageName = previousStageModel.getName();
								}

							}
						}
						DevOpsRunStatusModel model =
								action.createRunStatus(flowNode, run, vars, null,
										DevOpsConstants.NOTIFICATION_COMPLETED.toString(),
										false, upstreamTaskExecutionURL,
										upstreamStageName);
						// set stage status from tag
						if (GenericUtils.isNotEmpty(stageStatusFromTag)) {
							DevOpsRunStatusStageModel stageModel = model.getStageModel();
							stageModel.setStageStatusFromTag(stageStatusFromTag);
						}

						action.setModel(model);
						if (notificationModel != null)
							notificationModel.send(action.getModel());

						//call test results api
						if (model.getTestSummaries() != null && model.getTestSummaries().size()>0) {
							for (DevOpsTestSummary devOpsTestSummary : model.getTestSummaries()){
								notificationModel
										.sendTestResults(devOpsTestSummary);
							}
						}
					}
				} else
					_printDebug("onNewHead", new String[]{"message"},
							new String[]{"Skipping declarative stage"}, Level.FINE);
			}

		}
		
		private String getStageStatusFromTag(FlowNode fn) {
			String tagValue =null;
			try {
				_printDebug("getStageStatusFromTag", null, null, Level.FINE);

				TagsAction tagsAction = fn.getPersistentAction(TagsAction.class);
				if (tagsAction != null) {
					tagValue = tagsAction.getTagValue("STAGE_STATUS");
					if (tagValue != null && skippedStages().contains(tagValue))
						return tagValue;
				}
			} catch (Exception ignore) {
				_printDebug("getStageStatusFromTag", new String[]{"Exception"}, new String[]{ignore.getMessage()},
						Level.SEVERE);
			}

			return tagValue;
		}

		public static List<String> skippedStages() {
			return Arrays.asList("SKIPPED_FOR_FAILURE","SKIPPED_FOR_UNSTABLE",
					"SKIPPED_FOR_CONDITIONAL","SKIPPED_FOR_RESTART");
		}

		private void addToPipelineGraph(DevOpsRunStatusAction action, FlowNode flowNode) {
			if (null != action && null != flowNode && null!= run) {
				DevOpsPipelineGraph pipelineGraph = action.getPipelineGraph();
				pipelineGraph.setJobExecutionUrl(run.getUrl());
				DevOpsRunStatusStageModel previousStageModel = action.getModel().getStageModel();
				String upstreamExecUrl = previousStageModel.getUrl();
				String upstreamStageName = previousStageModel.getName();
				String stageRunStatus = DevOpsConstants.STAGE_RUN_IN_PROGRESS.toString();
				Result result = run.getResult();
				if(null != result)
					stageRunStatus = result.toString();
					
				pipelineGraph.addNode(flowNode, upstreamExecUrl, upstreamStageName, 
						stageRunStatus);
			}
		}
		
		private void markNodeToInactive(DevOpsRunStatusAction action, FlowNode flowNode) {
			if(null != action && null != flowNode) {
				DevOpsPipelineGraph pipelineGraph = action.getPipelineGraph();
				if (pipelineGraph != null) {
					String id = String.valueOf(Integer.parseInt(flowNode.getEnclosingId()) + 1);
					if(null != pipelineGraph.getNodeById(id)) {
						pipelineGraph.getNodeById(id).setActive(false);
						String stageRunStatus = DevOpsConstants.STAGE_RUN_COMPLETED.toString();
						if(null != run) {
							Result result = run.getResult();
							if(null != result)
								stageRunStatus = result.toString();
						}
						pipelineGraph.getNodeById(id).setStageExecStatus(stageRunStatus);
					}
				}
			}
		}
		
		private boolean isDeclarativeStage(FlowNode fn, boolean stageStart) {
			boolean result = false;
			if (fn != null) {
				String nodeName = "";
				if (stageStart)
					nodeName = getNodeName((StepStartNode) fn);
				else {
					StepStartNode startNode = ((StepEndNode) fn).getStartNode();
					if (startNode != null)
						nodeName = getNodeName(startNode);
				}
				result =
						nodeName.startsWith(DevOpsConstants.DECLARATIVE_STAGE.toString());
			}
			return result;
		}

		private String getNodeName(FlowNode startNode) {
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

		private boolean isStageStart(FlowNode fn) {
			_printDebug("isStageStart", null, null, Level.FINE);
			return fn != null && (
					(fn.getAction(StageAction.class) != null) ||
					(fn.getAction(LabelAction.class) != null &&
					 fn.getAction(ThreadNameAction.class) == null) && isStageStartStep(fn)
			);
		}

		private boolean isStageEnd(FlowNode fn) {
			_printDebug("isStageEnd", null, null, Level.FINE);
			if (fn != null && isStageEndStep(fn)) {
				DevOpsRunStatusAction action = run.getAction(DevOpsRunStatusAction.class);
				if (action != null) {
					DevOpsRunStatusModel model = action.getModel();
					StepStartNode endNode = ((StepEndNode) fn).getStartNode();
					if (endNode != null && model != null) {
						DevOpsRunStatusStageModel stageModel = model.getStageModel();
						if (stageModel != null) {
							String startId = endNode.getId();
							Set<String> seenIds = action.getSeenIds();
							if (seenIds.contains(startId)) {
								return true;
							}
						}
					}
				}
			}
			return false;
		}

		private boolean isStageStartStep(FlowNode fn) {
			_printDebug("isStageStartStep", null, null, Level.FINE);
			if (fn instanceof StepStartNode)
				return ((StepStartNode) fn).getStepName().equalsIgnoreCase("stage");
			return false;
		}

		private boolean isStageEndStep(FlowNode fn) {
			_printDebug("isStageEndStep", null, null, Level.FINE);
			if (fn instanceof StepEndNode) {
				StepStartNode ssn = ((StepEndNode) fn).getStartNode();
				if (ssn != null) {
					return ssn.getStepName().equalsIgnoreCase("stage");
				}
			}
			return false;
		}

		private void _printDebug(String methodName, String[] variables, String[] values,
		                         Level logLevel) {
			GenericUtils.printDebug(DevOpsStageListener.class.getName(), methodName,
					variables, values, logLevel);
		}

	}

	@Override
	public void onCompleted(final Run<?, ?> run, TaskListener listener) {
		super.onCompleted(run, listener);
		DevOpsModel model = new DevOpsModel();
		printDebug("onCompleted", null, null, Level.FINE);
		try {
			if (model.checkIsTrackingCache(run.getParent(), run.getId())) {
				String pronoun = run.getParent().getPronoun();
				printDebug("onCompleted", new String[]{"pronoun"}, new String[]{pronoun},
						Level.FINE);
				EnvVars vars = GenericUtils.getEnvVars(run, listener);
				// Pipeline
				if (pronoun.equalsIgnoreCase(DevOpsConstants.PIPELINE_PRONOUN.toString()) ||
				    pronoun.equalsIgnoreCase(
						    DevOpsConstants.BITBUCKET_MULTI_BRANCH_PIPELINE_PRONOUN.toString()))
					handleRunCompleted(run, vars); // not necessary in case we don't want run Start/Completed events
					// Freestyle
				else if (pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) ||
				         pronoun.equalsIgnoreCase(
						         DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString()))
					handleRunCompleted(run, vars);
			}
		} finally {
			model.removeFromTrackingCache(run.getParent().getFullName(), run.getId());
			model.removeFromPipelineInfoCache(run.getParent().getFullName(), run.getId());
		}
	}

	@Override
	public Environment setUpEnvironment(AbstractBuild build, Launcher launcher,
	                                    BuildListener listener)
			throws IOException, InterruptedException, RunnerAbortedException {
		DevOpsModel model = new DevOpsModel();
		printDebug("setUpEnvironment", null, null, Level.FINE);
		if (build != null) {
			DevOpsModel.DevOpsPipelineInfo pipelineInfo = model.getPipelineInfo(build.getParent(), build.getId());
			DevOpsJobProperty jobProperties = model.getJobProperty(build.getParent());
			if (build.getParent().getPronoun().equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) ||
			    build.getParent().getPronoun().equalsIgnoreCase(DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString())) {

				if (model.checkIsTrackingCache(build.getParent(), build.getId())) {
					String jobId = model.getJobId(build, build.getParent());
					String token = model.removeCallbackToken(jobId);
					String jobUrl = build.getParent().getAbsoluteUrl();
					String jobName = build.getParent().getName();
					String jenkinsUrl = model.getJenkinsUrl();
					String buildUrl = jenkinsUrl + build.getUrl();

					if ( jobId != null && jobUrl != null &&
					    jobName != null && jenkinsUrl != null && buildUrl != null) {

						if (token !=null) {
							if (shouldStop(build, listener, build.getParent(), model)) {
								model.sendBuildAndToken(token, jenkinsUrl, buildUrl, jobUrl,
										jobName, null, null, false, null, false);
								build.setResult(Result.FAILURE);
								throw new RunnerAbortedException();
							} else
								model.sendBuildAndToken(token, jenkinsUrl, buildUrl, jobUrl,
										jobName, null, null, false, null, false);
						} else {
							if (shouldStopDueToLocalError(build, listener, build.getParent(), model)){
								build.setResult(Result.FAILURE);
								throw new RunnerAbortedException();
							}
						}
						// Schedule the next Job once current has resumed, in case of parallel pipeline executions
						//if (model.isQueueJobs()) {
						//	if (model.scheduleNextJob(build, job, 5) && Level.FINE)
						//		printDebug("setupEnvironment", new String[]{"message"}, new String[]{"Next queued Job has been scheduled."}, Level.FINE);
						//}
					}
				}
				else if (pipelineInfo != null && pipelineInfo.isUnreacheable()) {
					if (jobProperties.isIgnoreSNErrors())
						listener.getLogger()
								.println("[ServiceNow DevOps] ServiceNow instance not contactable, but will ignore");
					else {
						printDebug("setUpEnvironment", new String[]{"message"},
								new String[]{pipelineInfo.getErrorMessage()}, Level.WARNING);
						listener.getLogger().println("[ServiceNow DevOps] " + pipelineInfo.getErrorMessage());
						build.setResult(Result.FAILURE);
						throw new RunnerAbortedException();
					}
				}

			}
		}
		return super.setUpEnvironment(build, launcher, listener);
	}

	private boolean shouldStopDueToLocalError(final Run<?, ?> run, BuildListener listener, Job<?, ?> job,
	                                          DevOpsModel model){

		printDebug("shouldStopDueToLocalError", null, null, Level.FINE);
		boolean result = false;
		if (run != null && job != null && listener != null) {
			if (job.getPronoun().equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) ||
			    job.getPronoun().equalsIgnoreCase(DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString())) {
				String jobId = model.getJobId(run, job);
				String _result = model.removeCallbackResult(jobId);
				// Valid result available
				if (jobId != null && _result != null && _result.contains(DevOpsConstants.COMMON_RESULT_FAILURE.toString())) {
					result = true;
					// There is error in sending the request
					String msg = "There was error in sending callback request";
					if (_result.contains(DevOpsConstants.COMMON_RESULT_FAILURE.toString())) {
						msg = _result;
					}
					printDebug("shouldStop", new String[]{"message"},
							new String[]{msg},
							Level.FINE);
					listener.getLogger().println(
							"[ServiceNow DevOps]" + msg);
				}
			}
		}
		return result;
	}

	private boolean shouldStop(final Run<?, ?> run, BuildListener listener, Job<?, ?> job,
	                           DevOpsModel model) {
		printDebug("shouldStop", null, null, Level.FINE);
		boolean result = false;
		if (run != null && job != null && listener != null) {
			if (job.getPronoun().equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) ||
			    job.getPronoun().equalsIgnoreCase(DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString())) {
				String jobId = model.getJobId(run, job);
				String _result = model.removeCallbackResult(jobId);
				// Valid result available
				if (jobId != null && _result != null) {

					// Change not approved, fail gracefully so the subsequent build steps won't be executed
					if (!model.isApproved(_result)) {
						result = true;
						// Check if it was canceled by user
						if (model.isCanceled(_result)) {
							listener.getLogger()
									.println("[ServiceNow DevOps] Job was canceled");
							printDebug("shouldStop", new String[]{"message"},
									new String[]{"Job was canceled"}, Level.INFO);
							String changeComments = model.getChangeComments(_result);
							if (!GenericUtils.isEmpty(changeComments))
								listener.getLogger().println("[ServiceNow DevOps] \nCancel comments:\n" + changeComments);
						}
						// Not canceled and not approved
						else {
							String msg = "Job was not approved for execution";
							if ( _result.contains(DevOpsConstants.COMMON_RESULT_FAILURE.toString())) {
								msg = _result;
							}

							printDebug("shouldStop", new String[]{"message"},
									new String[]{msg},
									Level.FINE);
							//i
							listener.getLogger().println(
									"[ServiceNow DevOps]" + msg);

							String changeComments = model.getChangeComments(_result);
							if (!GenericUtils.isEmpty(changeComments))
								listener.getLogger().println("[ServiceNow DevOps] \nRejection comments:\n" + changeComments);
						}
					} else {
						printDebug("shouldStop", new String[]{"message"},
								new String[]{"Job has been approved for execution"},
								Level.INFO);
						listener.getLogger().println(
								"[ServiceNow DevOps] Job has been approved for execution");
						String changeComments = model.getChangeComments(_result);
						if (!GenericUtils.isEmpty(changeComments))
							listener.getLogger().println("[ServiceNow DevOps] \nApproval comments:\n" + changeComments);
					}
				}

			}
		}

		return result;
	}

	@Override
	public void onStarted(final Run<?, ?> run, TaskListener listener) {
		super.onStarted(run, listener);
		DevOpsModel model = new DevOpsModel();
		printDebug("onStarted", null, null, Level.FINE);
		EnvVars vars = GenericUtils.getEnvVars(run, listener);
		if (vars != null) {
			DevOpsModel.DevOpsPipelineInfo pipelineInfo = model.checkIsTracking(run.getParent(),
					run.getId(), vars.get("BRANCH_NAME"));
			printDebug("onStarted", new String[]{"pipelineInfo"}, new String[]{pipelineInfo.toString()},
					Level.FINE);
			if (pipelineInfo != null) {
				model.addToPipelineInfoCache(run.getParent().getFullName(), run.getId(), pipelineInfo);
				
				if (pipelineInfo.isTrack()) {
					model.addToTrackingCache(run.getParent().getFullName(), run.getId(), pipelineInfo);
					notificationModel = new DevOpsNotificationModel();
					String pronoun = run.getParent().getPronoun();
					// Pipeline
					if (pronoun.equalsIgnoreCase(DevOpsConstants.PIPELINE_PRONOUN.toString()) ||
						pronoun.equalsIgnoreCase(
								DevOpsConstants.BITBUCKET_MULTI_BRANCH_PIPELINE_PRONOUN.toString())) {
						handleRunStarted(run, vars); // not necessary in case we don't want run Start/Completed events
						handlePipeline(run, vars);//, configProp);
					}
					// Freestyle
					else if (pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) ||
							pronoun.equalsIgnoreCase(
									DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString()))
						handleRunStarted(run, vars);
				}
			}
		}
	}

	private void handlePipeline(final Run<?, ?> run, EnvVars vars) {
			printDebug("handlePipeline", null, null, Level.FINE);
		if (run instanceof WorkflowRun) {
			EnvVars _vars = vars;
			ListenableFuture<FlowExecution> promise =
					((WorkflowRun) run).getExecutionPromise();
			promise.addListener(new Runnable() {
				@Override
				public void run() {
					try {
						FlowExecution ex =
								((WorkflowRun) run).getExecutionPromise().get();
						ex.addListener(
								new DevOpsStageListener(run, _vars, notificationModel));
					} catch (InterruptedException e) {
						printDebug("handlePipeline", new String[]{"InterruptedException"}, new String[]{e.getMessage()},
								Level.SEVERE);
					} catch (ExecutionException e) {
						printDebug("handlePipeline", new String[]{"ExecutionException"}, new String[]{e.getMessage()},
								Level.SEVERE);
					}
				}
			}, Executors.newSingleThreadExecutor());
		}
	}

	private void handleRunStarted(final Run<?, ?> run, EnvVars vars) {
		printDebug("handleRunStarted", null, null, Level.FINE);
		if (run != null) {
			DevOpsRunStatusAction action = new DevOpsRunStatusAction();
			DevOpsRunStatusModel model = action.createRunStatus(null, run, vars,
					DevOpsConstants.NOTIFICATION_STARTED.toString(), null, false, null,
					null);
			action.setModel(model);
			run.addAction(action);
			if (notificationModel != null)
				notificationModel.send(action.getModel());
		}
	}

	private void handleRunCompleted(final Run<?, ?> run, EnvVars vars) {
		printDebug("handleRunCompleted", null, null, Level.FINE);
		if (run != null) {
			DevOpsRunStatusAction action = run.getAction(DevOpsRunStatusAction.class);
			if (action != null) {
				DevOpsRunStatusModel model = action.createRunStatus(null, run, vars,
						DevOpsConstants.NOTIFICATION_COMPLETED.toString(), null, false,
						null, null);
				action.setModel(model);
				if (notificationModel != null) {
					notificationModel.send(action.getModel());

					if (action.getModel().getTestSummaries() != null &&
					    action.getModel().getTestSummaries().size() > 0) {
						for (DevOpsTestSummary devOpsTestSummary : action.getModel()
								.getTestSummaries()) {
							notificationModel
									.sendTestResults(devOpsTestSummary);
						}
					}
				}

			}
		}
	}

	private void printDebug(String methodName, String[] variables, String[] values,
	                        Level logLevel) {
		GenericUtils.printDebug(DevOpsRunListener.class.getName(), methodName, variables,
				values, logLevel);
	}

}
