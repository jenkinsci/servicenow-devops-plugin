package io.jenkins.plugins.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import hudson.FilePath;
import io.jenkins.plugins.DevOpsRunListener;

/*
 * The graph(hashMap) holds the ordered-list of children for each nodes
 *   parent-stage-1 --> child-stage-1,child-stage-2,
 *   parent-stage-2 --> child-stage-3,child-stage-4,
 *
 *   Ex:    S1
 *              S2
 *              S3
 *          S4
 *              S5
 *                  S6
 *
 *        Graph:
 *        null   --> S1,S4
 *        S1     --> S2,S3
 *        S4     --> S5
 *        S5     --> S6
 */

public class DevOpsPipelineGraph {

	private String jobExecutionUrl;
	private FilePath workspacePath;
	// In restart senario duplicate event are coming, just to filter them using processedIdList
	private final Set<String> processedIdList = new HashSet<String>();
	private final Map<String, DevOpsPipelineNode> map = new HashMap<>(); // NodeId -> DevopsPipelineNode
	private final Map<String, LinkedList<DevOpsPipelineNode>> graph = new HashMap<>();  // ParentNodeId -> LinkedList<DevopsPipelineNode>

	public FilePath getWorkspacePath() {
		return workspacePath;
	}

	public void setWorkspacePath(FilePath workspacePath) {
		this.workspacePath = workspacePath;
	}

	private final Set<DevOpsTestSummary> jobTestResults = new HashSet<DevOpsTestSummary>();
	private final Set<DevOpsSonarQubeModel> jobSonarQubeModelResults = new HashSet<DevOpsSonarQubeModel>();

	public DevOpsPipelineNode getNodeById(String id) {
		return this.map.get(id);
	}

	public DevOpsPipelineNode getNodeByName(String nodeName) {
		Optional<DevOpsPipelineNode> node = this.map.values().stream().filter(entry -> entry.getName().equals(nodeName)).findFirst();
		return node.orElse(null);
	}

	public String getJobExecutionUrl() {
		return jobExecutionUrl;
	}

	public void setJobExecutionUrl(String jobExecutionUrl) {
		if (null == this.jobExecutionUrl)
			this.jobExecutionUrl = jobExecutionUrl;
	}

	public void addToProcessedList(String stageId) {
		processedIdList.add(stageId);
	}

	public boolean isAlreadyProcessed(String stageId) {
		return processedIdList.contains(stageId);
	}

	public void addStepToNode(String stageId) {
		if (null != stageId) {
			DevOpsPipelineNode nodeById = getNodeById(stageId);
			if (null != nodeById)
				nodeById.setStepAssociated(true);
		}
	}

	public boolean isStepAssociated(String stageId) {
		DevOpsPipelineNode nodeById = getNodeById(stageId);
		if (null != nodeById) {
			return nodeById.isStepAssociated();
		}
		return false;
	}

	public boolean isTestResultPublished(DevOpsTestSummary testSummary) {
		return this.jobTestResults.contains(testSummary);
	}

	public void addToJobTestResults(DevOpsTestSummary testSummary) {
		this.jobTestResults.add(testSummary);
	}

	public boolean isSonarQubeModelResultPublished(DevOpsSonarQubeModel sonarQubeModel) {
		return this.jobSonarQubeModelResults.contains(sonarQubeModel);
	}

	public void addToJobSonarQubeModelResults(DevOpsSonarQubeModel sonarQubeModel) {
		this.jobSonarQubeModelResults.add(sonarQubeModel);
	}

	private String getGraphHashKey(String nodeId) {
		return "Prefix_" + nodeId;
	}

	public DevOpsPipelineNode addNode(String parentId, String shortName, FlowNode flowNode, String pipelineExecutionUrl, String status) {
		String name = shortName;
		if (!StringUtils.isEmpty(parentId)) {
			DevOpsPipelineNode parentNode = map.get(parentId);
			if (parentNode != null)
				name = parentNode.getName() + "/" + shortName;
		}
		DevOpsPipelineNode node = new DevOpsPipelineNode(parentId, shortName, name, flowNode, pipelineExecutionUrl, status);
		String key = getGraphHashKey(parentId);
		LinkedList<DevOpsPipelineNode> linkedList;
		synchronized (graph) {
			if (!graph.containsKey(key)) {
				linkedList = new LinkedList<DevOpsPipelineNode>();
				graph.put(key, linkedList);
			} else {
				linkedList = graph.get(key);
			}

			if (linkedList.isEmpty() || (!linkedList.isEmpty() && !Objects.equals(linkedList.getFirst().getId(), flowNode.getId()))) {
				linkedList.addFirst(node);
			}
		}
		map.put(flowNode.getId(), node);
		populateUpstreamDetails(parentId, flowNode.getId());
		populateParentDetails(parentId, flowNode.getId());
		return node;
	}

	public static String getStageExecutionUrl(String pipelineUrl, String stageId) {
		return pipelineUrl + "execution/node/" + stageId + "/wfapi/describe";
	}


	private void populateParentDetails(String parentStageId, String stageId) {
		if (!StringUtils.isEmpty(parentStageId)) {
			DevOpsPipelineNode parentNode = getNodeById(parentStageId);
			DevOpsPipelineNode node = getNodeById(stageId);

			if (parentNode != null) {
				node.setParentName(parentNode.getName());
				String parentExecutionUrl = getStageExecutionUrl(parentNode.getPipelineExecutionUrl(), parentStageId);
				node.setParentExecutionUrl(parentExecutionUrl);
			}
		}
	}

	private void populateUpstreamDetails(String parentStageId, String stageId) {
		if (map.containsKey(stageId)) {
			DevOpsPipelineNode node = getNodeById(stageId);
			FlowNode flowNode = node.getFlowNode();

			Boolean isParallelStage = DevOpsRunListener.DevOpsStageListener.isEnclosedInParallel(flowNode);
			if (!isParallelStage) {
				DevOpsPipelineNode upStreamPipelineNode = getUpStreamNode(parentStageId, stageId);
				if (upStreamPipelineNode != null) {
					node.setUpstreamStageName(upStreamPipelineNode.getName());
					String upstreamTaskExecURL = getStageExecutionUrl(upStreamPipelineNode.getPipelineExecutionUrl(), upStreamPipelineNode.getId());
					node.setUpstreamTaskExecURL(upstreamTaskExecURL);
				}
			}
		}
	}

	private DevOpsPipelineNode getUpStreamNode(String parentId, String id) {
		DevOpsPipelineNode upStreamStage = null;
		String key = getGraphHashKey(parentId);

		if (graph.containsKey(key)) {
			LinkedList<DevOpsPipelineNode> linkedList = graph.get(key);
			for (int i = 0; i < linkedList.size() - 1; i++) {
				if (linkedList.get(i).getId().equals(id)) {
					upStreamStage = linkedList.get(i + 1);
					break;
				}
			}
		}
		return upStreamStage;
	}

	public List<String> getWaitForChildExecutions(String stageId) {
		List<String> childs = new ArrayList<>();
		String key = getGraphHashKey(stageId);
		if (graph.containsKey(key)) {
			LinkedList<DevOpsPipelineNode> list = graph.get(key);
			for (DevOpsPipelineNode node : list) {
				childs.add(getStageExecutionUrl(node.getPipelineExecutionUrl(), node.getId()));
			}
		}
		return childs;
	}
}
