package io.jenkins.plugins.model;

import net.sf.json.JSONObject;


public class CDMSnapshot {
	private String sys_id;
	private String name;
	private String description;
	private String validation;
	private Boolean published;
	private JSONObject validationResults;

	public JSONObject getValidationResults() {
		return validationResults;
	}

	public void setValidationResults(JSONObject validationResults) {
		this.validationResults = validationResults;
	}

	public String getSys_created_on() {
		return sys_created_on;
	}

	public void setSys_created_on(String sys_created_on) {
		this.sys_created_on = sys_created_on;
	}

	private String sys_created_on;

	public String getSys_id() {
		return sys_id;
	}

	public void setSys_id(String sys_id) {
		this.sys_id = sys_id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getValidation() {
		return validation;
	}

	public void setValidation(String validation) {
		this.validation = validation;
	}

	public Boolean getPublished() {
		return published;
	}

	public void setPublished(Boolean published) {
		this.published = published;
	}
}