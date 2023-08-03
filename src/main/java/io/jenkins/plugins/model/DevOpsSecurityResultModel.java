package io.jenkins.plugins.model;

import java.util.Objects;

import net.sf.json.JSONObject;

public class DevOpsSecurityResultModel {

	private String securityResultAttributes;


	public DevOpsSecurityResultModel( String securityResultAttributes) {
		this.securityResultAttributes = securityResultAttributes;
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
	}

	@Override
	public int hashCode() {
		return Objects.hash(securityResultAttributes);
	}

	@Override
	public String toString() {
		return "DevOpsSecurityResultModel{" +
				"securityToolAttributes='" + securityResultAttributes + '\'' +
				'}';
	}
}
