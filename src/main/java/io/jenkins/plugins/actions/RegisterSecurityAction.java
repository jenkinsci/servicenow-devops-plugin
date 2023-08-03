package io.jenkins.plugins.actions;

import java.util.Objects;

import org.kohsuke.stapler.export.ExportedBean;

import hudson.model.InvisibleAction;
import net.sf.json.JSONObject;

@ExportedBean(defaultVisibility = 2)
public class RegisterSecurityAction extends InvisibleAction {
	private String securityToolAttributes;


	public RegisterSecurityAction() {

	}

	public RegisterSecurityAction(String securityToolAttributes) {
		this.securityToolAttributes = securityToolAttributes;

	}

	public JSONObject getSecurityToolAttributes() {
		return JSONObject.fromObject(securityToolAttributes);
	}

	public void setSecurityToolAttributes(JSONObject securityToolAttributes) {
		this.securityToolAttributes = securityToolAttributes.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		RegisterSecurityAction that = (RegisterSecurityAction) o;
		return securityToolAttributes.equals(that.securityToolAttributes);
	}

	@Override
	public int hashCode() {
		return Objects.hash(securityToolAttributes);
	}

	@Override
	public String toString() {
		return "RegisterSecurityAction{" +
				"securityToolAttributes='" + securityToolAttributes + '\'' +
				'}';
	}
}
