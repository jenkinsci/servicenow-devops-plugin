package io.jenkins.plugins.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jenkinsci.plugins.workflow.graph.FlowNode;

import hudson.FilePath;

/**
 * The tree holds the hierarchy of the nodes
 * like parent
 * 		/    \
 * 	  child   child
 *   /   \
 * child  child
 * 
 */

public class DevOpsPipelineGraph {

	private String jobExecutionUrl;
	private FilePath workspacePath;
	private Map<String, DevOpsPipelineNode> graph = new HashMap<String, DevOpsPipelineNode>();
	private DevOpsPipelineNode rootNode;

	public FilePath getWorkspacePath() {
		return workspacePath;
	}

	public void setWorkspacePath(FilePath workspacePath) {
		this.workspacePath = workspacePath;
	}

	private Set<DevOpsTestSummary> jobTestResults = new HashSet<DevOpsTestSummary>();
	public DevOpsPipelineNode getNodeById(String id) {
		return this.graph.get(id);
	}

	public DevOpsPipelineNode getRootNodeByStageName(String nodeName) {
		DevOpsPipelineNode nodeById = getNodeById(nodeName);
		return nodeById.getRootNodeRef();
	}
	
	private DevOpsPipelineNode getRootNode() {
		return this.rootNode;
	}
	
	private void updateRootNode(DevOpsPipelineNode rootNode) {
		// if a previous stage is found, link it to currentRootNode
		if (null != this.rootNode) 
			rootNode.setPreviousRootNode(this.rootNode);
		this.rootNode = rootNode;
	}
	
	public DevOpsPipelineNode getNodeByName(String nodeName){
		Optional<DevOpsPipelineNode> node = this.graph.values().stream().filter(entry -> entry.getName().equals(nodeName)).findFirst();
		if(node.isPresent()) {
			return node.get();
		}
		return null;
	}

	public boolean isCurrentRoot(DevOpsPipelineNode node){
		if (this.rootNode ==null || node == null)
			return false;
		return this.rootNode.equals(node);
	}

	public String getJobExecutionUrl() {
		return jobExecutionUrl;
	}

	public void setJobExecutionUrl(String jobExecutionUrl) {
		if(null == this.jobExecutionUrl)
			this.jobExecutionUrl = jobExecutionUrl;
	}

	public void addNode(FlowNode flowNode, String upstreamExecUrl, String upstreamStageName, String status) {
		if(null != flowNode) {
			DevOpsPipelineNode node = new DevOpsPipelineNode(flowNode, upstreamExecUrl, upstreamStageName, status);
			this.populateParentNode(node);
			this.graph.put(node.getId(), node);
		}
	}

	private void populateParentNode(DevOpsPipelineNode node) {
		if(node != null && null != node.getFlowNode()) {
			FlowNode parentNode = this.getExistingParentNode(node.getFlowNode());
			if(null != parentNode) {
				node.setParentId(parentNode.getId());
				node.setRootNodeRef(getRootNode()); //add root node ref on each node
			}
			else
				updateRootNode(node); // save root node's ref
		}		
	}

	private FlowNode getExistingParentNode(FlowNode flowNode) {
		if(null != flowNode && !flowNode.getParents().isEmpty()) {
			// Current ParentNodes are set to only one parent node
			FlowNode parentNode = flowNode.getParents().get(0);
			if(this.containsNode(parentNode.getId()) && this.getNodeById(parentNode.getId()).isActive()) {
				return parentNode;
			} else {
				return this.getExistingParentNode(parentNode);
			}
		}
		return null;
	}

	public boolean containsNode(String id) {
		if(null != this.getNodeById(id))
			return true;
		return false;
	}

	public void addStepToNode(String stageName) {
		if(null != stageName) {
			DevOpsPipelineNode nodeByStageName = getNodeByName(stageName);
			if(null != nodeByStageName)
				nodeByStageName.setStepAssociated(true);
		}
	}

	public boolean isStepAssociated(String stageName) {
		DevOpsPipelineNode nodeByStageName = getNodeByName(stageName);
		if(null != nodeByStageName) {
			return nodeByStageName.isStepAssociated();
		}
		return false;
	}

	public boolean isRootNode(String stageName) {
		DevOpsPipelineNode nodeByName = this.getNodeByName(stageName);
		return nodeByName.isRootNode();
	}

	public boolean isChangeCtrlInProgress() {
		return this.rootNode.isChangeCtrlInProgress();
	}

	public void markChangeCtrlInProgress() {
		this.rootNode.setChangeCtrlInProgress(true);
	}

	public boolean isTestResultPublished(DevOpsTestSummary testSummary) {
		return this.jobTestResults.contains(testSummary);
	}

	public void addToJobTestResults(DevOpsTestSummary testSummary) {
		this.jobTestResults.add(testSummary);
	}
}
