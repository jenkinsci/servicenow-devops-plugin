/**
 * 
 */
package io.jenkins.plugins.pipeline.steps;

import java.io.Serializable;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineRegisterArtifactStepExecution;
import io.jenkins.plugins.utils.DevOpsConstants;

/**
 * Register Artifact Step, identified by step-name 'snDevopsArtifact'
 * 
 * Example call to this step from pipeline is as follows (please note named
 * parameters)
 * 
 * snDevOpsArtifact("""{"artifacts": [{"name": "artifact1",
 * 		"version": "3.1","semanticVersion": "3.1.0","repositoryName": "repo1"},
 * 		{"name": "artifact2","version": "3.2","semanticVersion": "3.2.0",
 * 		"repositoryName": "repo2"}],"stageName": 
 * 		"Build","branchName": "master"}""")
 * 
 *
 */
public class DevOpsPipelineRegisterArtifactStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;
	private boolean m_enabled;
	private boolean m_ignoreErrors;
	private String artifactsPayload;
	
	@DataBoundConstructor
	public DevOpsPipelineRegisterArtifactStep(String artifactsPayload) {
		m_enabled = true;
		m_ignoreErrors = false;
		this.artifactsPayload = artifactsPayload;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new DevOpsPipelineRegisterArtifactStepExecution(context, this);
	}

	public boolean isEnabled() {
		return m_enabled;
	}

	@DataBoundSetter
	public void setEnabled(boolean enabled) {
		this.m_enabled = enabled;
	}

	public boolean isIgnoreErrors() {
		return m_ignoreErrors;
	}

	@DataBoundSetter
	public void setIgnoreErrors(boolean ignore) {
		this.m_ignoreErrors = ignore;
	}
	
	@DataBoundSetter
	public void setArtifactsPayload(String artifactsPayload) {
		this.artifactsPayload = artifactsPayload;
	}

	public String getArtifactsPayload() {
		return artifactsPayload;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return DevOpsConstants.ARTIFACT_REGISTER_STEP_FUNCTION_NAME.toString();
		}

		@Override
		public String getDisplayName() {
			return DevOpsConstants.ARTIFACT_REGISTER_STEP_DISPLAY_NAME.toString();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
		}

	}

}
