package io.jenkins.plugins.model;

import java.util.Objects;

import net.sf.json.JSONObject;

public class DevOpsSecurityResultModel {

	private String securityResultAttributes;
	private String stageNodeId;


	public DevOpsSecurityResultModel( String securityResultAttributes) {
		this.securityResultAttributes = securityResultAttributes;
		this.stageNodeId = null;
	}
	
	public DevOpsSecurityResultModel(String securityResultAttributes, String stageNodeId) {
		this.securityResultAttributes = securityResultAttributes;
		this.stageNodeId = stageNodeId;
	}


	public JSONObject getSecurityResultAttributes() {
		return JSONObject.fromObject(securityResultAttributes);
	}

	public void setSecurityResultAttributes(JSONObject securityResultAttributes) {
		this.securityResultAttributes = securityResultAttributes.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		DevOpsSecurityResultModel that = (DevOpsSecurityResultModel) o;
		return securityResultAttributes.equals(that.securityResultAttributes);
		// stageNodeId removed from comparison to avoid duplicates in security results
	}

	@Override
	public int hashCode() {
		return Objects.hash(securityResultAttributes);
		// stageNodeId removed from hashCode for consistency with equals method
	}

	@Override
	public String toString() {
		return "DevOpsSecurityResultModel{" +
				"securityToolAttributes='" + securityResultAttributes + '\'' +
				", stageNodeId='" + stageNodeId + '\'' +
				'}';
	}
	
	public String getStageNodeId() {
		return stageNodeId;
	}

	public void setStageNodeId(String stageNodeId) {
		this.stageNodeId = stageNodeId;
	}
}
