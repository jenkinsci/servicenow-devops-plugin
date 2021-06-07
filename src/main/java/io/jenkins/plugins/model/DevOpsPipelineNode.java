package io.jenkins.plugins.model;

import java.io.IOException;

import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;

/**
 * Node signifies the each stage / step hierarchy 
 * and holds all the information related to that stage / step
 * 
 * The information that can be fetched/understood from each Node:
 *     a. rootNode (an active node with no parents' data attached)
 *     b. previousNode (a previously completed rootNode)
 *      		This helps understand stage to stage relationship 
 *      		(like stage1 -- stage2 -- stageN ) to  map actual sequence 
 *      		of stages within the pipeline.
 * 
 */
public class DevOpsPipelineNode {
	
	
	private boolean active;
	private String id;
	private String name;
	private String parentId;
	private boolean stepAssociated;
	private String executionUrl;
	private String upstreamTaskExecutionURL;
	private String upstreamStageName;
	private FlowNode flowNode;
	private DevOpsRunStatusTestModel testModel;
	private boolean changeCtrlInProgress;
	private DevOpsPipelineNode rootNodeRef;
	private DevOpsPipelineNode previousRootNode;
	private String stageExecutionStatus;
	private long startTime;

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public WorkspaceAction getWsAction() {
		return wsAction;
	}

	public void setWsAction(WorkspaceAction wsAction) {
		this.wsAction = wsAction;
	}

	private WorkspaceAction wsAction;
	
	public DevOpsPipelineNode(FlowNode flowNode, String upstreamExecUrl, String upstreamStageName, String stageExecutionStatus) {
		super();
		this.active = true;
		this.name = flowNode.getDisplayName();
		this.id = flowNode.getId();
		this.flowNode = flowNode;
		this.upstreamTaskExecutionURL = upstreamExecUrl;
		this.upstreamStageName = upstreamStageName;
		this.setExecutionUrl();
		this.stageExecutionStatus = stageExecutionStatus;
	}

	public String getId() {
		return id;
	}
	
	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getParentId() {
		return parentId;
	}
	public void setParentId(String parentId) {
		this.parentId = parentId;
	}
	
	public boolean isStepAssociated() {
		if (null != getRootNodeRef())
			return rootNodeRef.isStepAssociated();
		return stepAssociated; // this node is a root
	}

	public void setStepAssociated(boolean stepAssociated) {
		if(null != getRootNodeRef())
			getRootNodeRef().setStepAssociated(stepAssociated);
		this.stepAssociated = stepAssociated; // this node is a root
	}

	public String getExecutionUrl() {
		return executionUrl;
	}

	private void setExecutionUrl() {
		if (null != this.getFlowNode()) {
			try {
				this.executionUrl = this.getFlowNode().getUrl();
			} catch (IOException e) {
				GenericUtils.printDebug(DevOpsPipelineNode.class.getName(), "setExecutionURL",
						new String[] { "message" }, new String[] { "Skipping declarative stage" }, true);
			}
		}
	}
	
	public FlowNode getFlowNode() {
		return flowNode;
	}
	public void setFlowNode(FlowNode flowNode) {
		this.flowNode = flowNode;
	}

	public void setTestModel(DevOpsRunStatusTestModel testModel) {
		this.testModel = testModel;
	}

	public DevOpsRunStatusTestModel getTestModel() {
		return this.testModel;
	}
	
	public String getUpstreamTaskExecURL() {
		if (null != getRootNodeRef())
			return getRootNodeRef().getUpstreamTaskExecURL();
		// this rootNode
		if(null != getPreviousRootNode()) {
			DevOpsPipelineNode previousRootNode = getPreviousRootNode();
			if( previousRootNode.isStepAssociated() || 
					DevOpsConstants.STAGE_RUN_FAILURE.toString().equals(previousRootNode.getStageExecStatus()) ) {
				return this.upstreamTaskExecutionURL;
			} else {
				return previousRootNode.getUpstreamTaskExecURL();
			}
		}
		return this.upstreamTaskExecutionURL;
	}
	
	public void setUpstreamTaskExecURL(String upstreamTaskExecURL) {
		this.upstreamTaskExecutionURL = upstreamTaskExecURL;
	}

	public String getUpstreamStageName() {
		if (null != getRootNodeRef())
			return getRootNodeRef().getUpstreamStageName();
		// this rootNode
		if(null!=getPreviousRootNode()) {
			DevOpsPipelineNode previousRootNode = getPreviousRootNode();
			if( previousRootNode.isStepAssociated() || 
						DevOpsConstants.STAGE_RUN_FAILURE.toString().equals(previousRootNode.getStageExecStatus()) ) {
					return this.upstreamStageName;			
			} else {
				return previousRootNode.getUpstreamStageName();
			}
		}
		return this.upstreamStageName;
	}
	
	public void setUpstreamStageName(String upstreamStageName) {
		this.upstreamStageName = upstreamStageName;
	}

	public boolean isChangeCtrlInProgress() {
		DevOpsPipelineNode rootNodeRef = getRootNodeRef();
		if(null != rootNodeRef)
			return rootNodeRef.isChangeCtrlInProgress();
		else
			return changeCtrlInProgress;
	}

	public void setChangeCtrlInProgress(boolean isChangeStepInProgress) {
		DevOpsPipelineNode rootNodeRef = getRootNodeRef();
		if(null != rootNodeRef)
			rootNodeRef.setChangeCtrlInProgress(isChangeStepInProgress);
		else
			this.changeCtrlInProgress = isChangeStepInProgress;
	}

	public DevOpsPipelineNode getRootNodeRef() {
		return rootNodeRef;
	}

	public void setRootNodeRef(DevOpsPipelineNode rootNodeRef) {
		this.rootNodeRef = rootNodeRef;
	}
	
	public void setPreviousRootNode(DevOpsPipelineNode previousRootNode) {
		this.previousRootNode = previousRootNode;
	}
	
	public DevOpsPipelineNode getPreviousRootNode() {
		return previousRootNode;
	}
	
	public boolean isRootNode() {
		return (null == getRootNodeRef()) ? true:false;
	}

	public String getStageExecStatus() {
		DevOpsPipelineNode rootNodeRef = getRootNodeRef();
		if(null != rootNodeRef)
			return rootNodeRef.getStageExecStatus();
		else
			return stageExecutionStatus;
	}

	public void setStageExecStatus(String status) {
		DevOpsPipelineNode rootNodeRef = getRootNodeRef();
		if(null != rootNodeRef)
			rootNodeRef.setStageExecStatus(status);
		else
			this.stageExecutionStatus = status;
	}
	
}
