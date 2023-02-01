package io.jenkins.plugins.model;

public class DevOpsJobModel {
	private String displayName;
	private String fullName;
	private String name;
	private String url;
	private String _class;

	public DevOpsJobModel(String displayName, String fullName, String name, String url, String _class) {
		this.displayName = displayName;
		this.fullName = fullName;
		this.name = name;
		this.url = url;
		this._class = _class;
	}

	public DevOpsJobModel() {
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String get_class() {
		return _class;
	}

	public void set_class(String _class) {
		this._class = _class;
	}
}

