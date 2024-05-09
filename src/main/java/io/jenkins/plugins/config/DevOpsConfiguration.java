package io.jenkins.plugins.config;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.XmlFile;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@Extension
@Symbol("snDevOpsConfig")
public class DevOpsConfiguration extends GlobalConfiguration {
	private static final Logger LOGGER = Logger.getLogger(DevOpsConfiguration.class.getName());

	private String logLevel;

	public XmlFile getConfigXml() {
		return getConfigFile();
	}

	private List<DevOpsConfigurationEntry> entries = new ArrayList<>();

	public Boolean isThresholdReached() {
		return this.entries.size() >= Integer.parseInt(DevOpsConstants.MAX_ALLOWED_DEVOPS_CONFIGURATIONS.toString());
	}

	public static @NonNull
	DevOpsConfiguration get() {
		DevOpsConfiguration instance = GlobalConfiguration.all().get(DevOpsConfiguration.class);
		if (instance == null) {
			throw new IllegalStateException();
		}
		return instance;
	}

	public DevOpsConfiguration() {
		load();
	}

	@Override
	public synchronized void load() {
		XmlFile xmlFile = this.getConfigFile();
		try {
			if (elementExists(xmlFile, "snDevopsEnabled") && !elementExists(xmlFile, "entries") && !elementExists(xmlFile, "entries/")) {
				DevOpsConfigurationEntry entry = parse(xmlFile);
				String logLevel = parseLogLevel(xmlFile);
				List<DevOpsConfigurationEntry> entries = getEntries();
				entries.add(entry);
				setLogLevel(logLevel);
				setEntries(entries);
			} else {
				super.load();
			}
		} catch (IOException ex) {
			LOGGER.log(Level.SEVERE, "Exception occurred while parsing the stored configuration", ex);
		}
	}

	public List<DevOpsConfigurationEntry> getEntries() {
		return entries;
	}

	@DataBoundSetter
	public void setEntries(List<DevOpsConfigurationEntry> entries) {
		this.entries = entries;
		save();
	}

	public String getLogLevel() {
		return logLevel;
	}

	@DataBoundSetter
	public void setLogLevel(String logLevel) {
		this.logLevel = logLevel;
		save();
	}

	@Override
	public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
		Integer allowedConfigurations = Integer.parseInt(DevOpsConstants.MAX_ALLOWED_DEVOPS_CONFIGURATIONS.toString());
		if (formData.get("entries") instanceof JSONArray && ((JSONArray) formData.get("entries")).size() > allowedConfigurations) {
			throw new FormException(String.format("You can add only %d ServiceNow configurations. Remove the unused configuration or use a different Jenkins server to configure.", allowedConfigurations), "configuration");
		}
		String errorMessage = findErrors(formData);
		if (errorMessage != null)
			throw new FormException(errorMessage, "name");
		else {
			String logLevel = getLogLevel(formData);
			if (logLevel != null)
				GenericUtils.configureLogger(logLevel);
			setEntries(new ArrayList<>());
			req.bindJSON(this, formData);
			return true;
		}
	}

	private String emptyRequiredFields(JSONObject entry) {
		if (entry == null)
			return null;
		if (entry.isEmpty())
			return null;
		if (entry.isNullObject())
			return null;
		StringBuilder errorMessage;
		String name = entry.getString("name");
		String instanceUrl = entry.getString("instanceUrl");
		String toolId = entry.getString("toolId");
		if (name.isEmpty() || instanceUrl.isEmpty() || toolId.isEmpty()) {
			errorMessage = new StringBuilder("The following fields are required, but were not provided for a ServiceNow DevOps configuration - ");
			if (name.isEmpty())
				errorMessage.append("Name, ");
			if (instanceUrl.isEmpty())
				errorMessage.append("Instance URL, ");
			if (toolId.isEmpty())
				errorMessage.append("Orchestration Tool ID");
			return errorMessage.toString().trim().replaceAll(",+$", "");
		}
		return null;
	}

	private String getLogLevel(JSONObject formData) {
		if (formData.isEmpty())
			return null;
		if (!formData.has("logLevel"))
			return null;
		return formData.getString("logLevel");
	}

	private String findErrors(JSONObject formData) {
		String prefix = "Changes not saved for the following reason: ";
		// Empty form is valid (no configurations)
		if (formData.isEmpty())
			return null;
		if (formData.has("logLevel") && !formData.has("entries"))
			return null;
		Object entries = formData.get("entries");
		// If we have a list of configurations
		if (entries instanceof JSONArray) {
			JSONArray entriesArray = (JSONArray) entries;
			Set<String> namesSet = new HashSet<>();
			Set<String> instanceUrlToolIdSet = new HashSet<>();
			int numDefaultConnections = 0;

			for (int i = 0; i < entriesArray.size(); i++) {
				JSONObject entry = entriesArray.getJSONObject(i);
				if (entry.getBoolean("defaultConnection"))
					numDefaultConnections++;
				// Validate required fields
				String errorMessage = emptyRequiredFields(entry);
				if (errorMessage != null)
					return prefix + errorMessage;
				// Validate unique name and combination of instanceUrl + toolId
				String name = entry.getString("name");
				String instanceUrlToolId = GenericUtils.removeTrailingSlashes(entry.getString("instanceUrl")) + entry.getString("toolId");
				if (namesSet.contains(name))
					return prefix + "Each ServiceNow DevOps configuration must have a unique Name";
				else
					namesSet.add(name);
				if (instanceUrlToolIdSet.contains(instanceUrlToolId))
					return prefix + "Each ServiceNow DevOps configuration must have a unique combination of Instance URL and Orchestration Tool ID";
				else
					instanceUrlToolIdSet.add(instanceUrlToolId);
			}

			// Validate exactly one connection set to Default
			if (numDefaultConnections != 1)
				return prefix + "There should be exactly one ServiceNow DevOps configuration set to Default";
		}
		// If we have a single configuration
		else if (entries instanceof JSONObject) {
			JSONObject entry = (JSONObject) entries;
			if (!entry.getBoolean("defaultConnection"))
				return prefix + "This ServiceNow DevOps configuration must be set to Default";
			// Validate required fields
			String errorMessage = emptyRequiredFields(entry);
			if (errorMessage != null)
				return prefix + errorMessage;
		}
		return null;
	}

	private boolean elementExists(XmlFile xmlFile, String element) throws IOException {
		if (!xmlFile.exists()) {
			return false;
		}
		String xmlContent = xmlFile.asString();
		return xmlContent.contains(String.format("<%s>", element));
	}

	private String parseLogLevel(XmlFile xmlFile) {
		try {
			if (!xmlFile.exists()) {
				return null;
			}
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			InputSource src = new InputSource();
			src.setCharacterStream(new StringReader(xmlFile.asString()));
			Document document = builder.parse(src);
			return getElementValue(document, "logLevel");
		} catch (ParserConfigurationException | IOException | SAXException ex) {
			LOGGER.log(Level.SEVERE, "Exception occurred while parsing the stored configuration", ex);
		}
		return null;
	}

	private DevOpsConfigurationEntry parse(XmlFile xmlFile) {
		try {
			if (!xmlFile.exists()) {
				return null;
			}
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			InputSource src = new InputSource();
			src.setCharacterStream(new StringReader(xmlFile.asString()));
			Document document = builder.parse(src);
			String credentialsId = getElementValue(document, "credentialsId");
			boolean trackCheck = Boolean.valueOf(getElementValue(document, "trackCheck"));
			String secretCredentialId = getElementValue(document, "secretCredentialId");
			String snArtifactToolId = getElementValue(document, "snArtifactToolId");
			String toolId = getElementValue(document, "toolId");
			String apiVersion = getElementValue(document, "apiVersion");
			String instanceUrl = getElementValue(document, "instanceUrl");
			String configName = GenericUtils.getAutoGeneratedConfigName(instanceUrl);
			boolean trackPullRequestPipelinesCheck = Boolean.valueOf(getElementValue(document, "trackPullRequestPipelinesCheck"));
			boolean active = Boolean.valueOf(getElementValue(document, "snDevopsEnabled"));
			return new DevOpsConfigurationEntry(configName, active, true, instanceUrl, toolId, snArtifactToolId, apiVersion, credentialsId, trackCheck, trackPullRequestPipelinesCheck, secretCredentialId);
		} catch (ParserConfigurationException | IOException | SAXException | URISyntaxException ex) {
			LOGGER.log(Level.SEVERE, "Exception occurred while parsing the stored configuration", ex);
		}
		return null;
	}

	private static String getElementValue(Document doc, String fieldName) {
		NodeList nodeList = doc.getElementsByTagName(fieldName);
		if (nodeList.getLength() > 0) {
			Node node = nodeList.item(0);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) node;
				return element.getTextContent();
			}
		}
		return null;
	}

	public ListBoxModel doFillLogLevelItems(@QueryParameter String logLevel) {
		ListBoxModel options = new ListBoxModel();
		options.add("inherit");
		options.add("off");
		options.add("severe");
		options.add("warning");
		options.add("info");
		options.add("config");
		options.add("fine");
		options.add("finer");
		options.add("finest");
		options.add("all");
		for (ListBoxModel.Option option : options) {
			if (option.value.equals(logLevel)) {
				option.selected = true;
			}
		}
		return options;
	}

}
