package io.jenkins.plugins.freestyle.steps;

import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;

public class DevOpsFreestyleRegisterSecurityStep extends Builder implements SimpleBuildStep, Serializable {

	private static final Logger LOGGER = Logger.getLogger(DevOpsFreestyleRegisterSecurityStep.class.getName());

	private String securityResultAttributes;

	private String securityToolId;

	public String getSecurityResultAttributes() {
		return securityResultAttributes;
	}

	@DataBoundSetter
	public void setSecurityResultAttributes(String securityResultAttributes) {
		this.securityResultAttributes = securityResultAttributes;
	}

	public String getSecurityToolId() {
		return securityToolId;
	}

	@DataBoundSetter
	public void setSecurityToolId(String securityToolId) {
		this.securityToolId = securityToolId;
	}

	public DevOpsFreestyleRegisterSecurityStep() {
		super();
	}

	@DataBoundConstructor
	public DevOpsFreestyleRegisterSecurityStep(String securityResultAttributes) {
		this.securityResultAttributes = securityResultAttributes;
	}

	@Override
	public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull EnvVars env, @NonNull Launcher launcher, @NonNull TaskListener listener) throws InterruptedException, IOException {
		JSONObject freeStyleInfo = new JSONObject();
		freeStyleInfo.put(DevOpsConstants.SEC_TOOL_BUILD_NUMBER.toString(), env.get("BUILD_NUMBER"));
		freeStyleInfo.put(DevOpsConstants.SEC_TOOL_TASK_EXEC_URL.toString(), env.get("BUILD_URL"));
		freeStyleInfo.put(DevOpsConstants.SEC_TOOL_TASK_URL.toString(), env.get("JOB_URL"));
		JSONObject securityParams = null;
		try {
			securityParams = JSONObject.fromObject(this.securityResultAttributes);
		} catch(JSONException jsonException){
			LOGGER.log(Level.WARNING, "attributes should be in stringified JSON format", jsonException);
			throw new AbortException(jsonException.getMessage());
		} catch(Exception exception) {
			LOGGER.log(Level.WARNING, exception.getMessage());
			throw new AbortException(exception.getMessage());
		}

		JSONObject payload = new JSONObject();
		payload.put(DevOpsConstants.SEC_TOOL_JSON_ATTR_RESULT_META_DATA.toString(), securityParams);
		payload.put(DevOpsConstants.SEC_TOOL_JSON_ATTR_TASK_INFO.toString(), freeStyleInfo);

		DevOpsModel model = new DevOpsModel();
		model.registerSecurityResult(payload);
	}


	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}


	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		public DescriptorImpl() {
			load();
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return DevOpsConstants.SECURITY_RESULT_STEP_FUNCTION_NAME.toString();
		}

		public ListBoxModel doFillSecurityToolItems(@QueryParameter String securityTool) {
			ListBoxModel options = new ListBoxModel();
			options.add("Veracode");
			options.add("Checkmarx One");
			options.add("Checkmarx SAST");
			options.add("Others");
			for (ListBoxModel.Option option : options) {
				if(GenericUtils.isEmpty(securityTool)){
					option.selected = true;
					break;
				}
				if (option.value.equals(securityTool)) {
					option.selected = true;
				}
			}
			return options;
		}
	}

	private void printDebug(String methodName, String[] variables, String[] values, Level logLevel) {
		GenericUtils.printDebug(DevOpsFreestyleRegisterSecurityStep.class.getName(), methodName, variables, values, logLevel);
	}

}
