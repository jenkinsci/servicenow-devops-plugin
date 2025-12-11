package io.jenkins.plugins.model;

import java.io.IOException;
import java.util.logging.Level;

import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import io.jenkins.plugins.utils.GenericUtils;

/**
 * Node signifies the each stage / step hierarchy
 * and holds all the information related to that stage / step
 */
public class DevOpsPipelineNode {

	private boolean active;
	private String id;
	private String name;
	private String shortName;
	private String parentId;
	private String parentName;
	private String parentExecutionUrl;
	private boolean stepAssociated;
	private String executionUrl;
	private String pipelineExecutionUrl;
	private String upstreamTaskExecutionURL;
	private String upstreamStageName;
	private FlowNode flowNode;
	private DevOpsRunStatusTestModel testModel;
	private boolean changeCtrlInProgress;
	private String stageExecutionStatus;
	private long startTime;
	private long duration;
	private long changeStartTime;
	private WorkspaceAction wsAction;
	/**
	 * Stores the stage status value derived from tags.
	 * This field represents the execution status that is extracted from pipeline stage tags.
	 * Used in the pipeline details API response to provide additional stage status information.
	 */
	private String stageStatusFromTag;

	public long getChangeStartTime() {
		return changeStartTime;
	}
	public void setChangeStartTime(long changeStartTime) {
		this.changeStartTime = changeStartTime;
	}
	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public long getDuration() {
		return duration;
	}

	public void setDuration(long duration) {
		this.duration = duration;
	}

	public WorkspaceAction getWsAction() {
		return wsAction;
	}

	public void setWsAction(WorkspaceAction wsAction) {
		this.wsAction = wsAction;
	}

	public DevOpsPipelineNode(String parentId, String shortName,String name, FlowNode flowNode, String pipelineExecutionUrl, String stageExecutionStatus) {
		super();
		this.active = true;
		this.name = name;
		this.shortName = shortName;
		this.id = flowNode.getId();
		this.parentId = parentId;
		this.flowNode = flowNode;
		this.pipelineExecutionUrl = pipelineExecutionUrl;
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

	public String getShortName() {
		return this.shortName;
	}

	public void setShortName(final String shortName) {
		this.shortName = shortName;
	}

	public String getParentId() {
		return parentId;
	}

	public void setParentId(String parentId) {
		this.parentId = parentId;
	}

	public boolean isStepAssociated() {
		return stepAssociated;
	}

	public void setStepAssociated(boolean stepAssociated) {
		this.stepAssociated = stepAssociated;
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
						new String[]{"message"}, new String[]{"Skipping declarative stage"}, Level.FINE);
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
		return this.upstreamTaskExecutionURL;
	}

	public void setUpstreamTaskExecURL(String upstreamTaskExecURL) {
		this.upstreamTaskExecutionURL = upstreamTaskExecURL;
	}

	public String getUpstreamStageName() {
		return this.upstreamStageName;
	}

	public void setUpstreamStageName(String upstreamStageName) {
		this.upstreamStageName = upstreamStageName;
	}

	public boolean isChangeCtrlInProgress() {
		return changeCtrlInProgress;
	}

	public void setChangeCtrlInProgress(boolean isChangeStepInProgress) {
		this.changeCtrlInProgress = isChangeStepInProgress;
	}

	public void setStageExecStatus(String status) {
		this.stageExecutionStatus = status;
	}
	
	/**
	 * Gets the stage status that is derived from pipeline stage tags.
	 * 
	 * @return The stage status value extracted from tags, or null if not set
	 */
	public String getStageStatusFromTag() {
		return stageStatusFromTag;
	}
	
	/**
	 * Sets the stage status value derived from pipeline stage tags.
	 * This value is included in the pipeline details API response when not empty.
	 * 
	 * @param stageStatusFromTag The stage status value to set
	 */
	public void setStageStatusFromTag(String stageStatusFromTag) {
		this.stageStatusFromTag = stageStatusFromTag;
	}

	public String getPipelineExecutionUrl() {
		return pipelineExecutionUrl;
	}

	public void setPipelineExecutionUrl(String pipelineExecutionUrl) {
		this.pipelineExecutionUrl = pipelineExecutionUrl;
	}

	public String getParentName() {
		return parentName;
	}

	public void setParentName(String parentName) {
		this.parentName = parentName;
	}

	public String getParentExecutionUrl() {
		return parentExecutionUrl;
	}

	public void setParentExecutionUrl(String parentExecutionUrl) {
		this.parentExecutionUrl = parentExecutionUrl;
	}
}
