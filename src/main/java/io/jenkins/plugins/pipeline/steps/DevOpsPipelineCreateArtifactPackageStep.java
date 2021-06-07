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
import io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineCreateArtifactPackageStepExecution;
import io.jenkins.plugins.utils.DevOpsConstants;

/**
 * Artifact package create step, identified by step name 'snDevOpsPackage'
 * 
 * Usage: 
 * 	1. snDevOpsPackage(name: "packageName", artifactsPayload: "[ { "name": "artifact1","repositoryName": "repo1", "currentBuildInfo" : "true"}, 
 * 			{"name": "artifact2","repositoryName": "repo2", currentBuildInfo : true } ] ”")
 * 	2. snDevOpsPackage(packageName: "packageName", artifactsPayload: """{"artifacts": [{"name": "artifact1",
 * 			"repositoryName": "repo1","version": "3.1"}, {"name": "artifact2",
 * 				"repositoryName": "repo2","version": "9.2"}],"branchName":"master"}""")
 * 
 *
 */
public class DevOpsPipelineCreateArtifactPackageStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;
	private String artifactsPayload;
	private String name;

	@DataBoundConstructor
	public DevOpsPipelineCreateArtifactPackageStep(String name, String artifactsPayload) {
		this.name = name;
		this.artifactsPayload = artifactsPayload;
	}
	
	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new DevOpsPipelineCreateArtifactPackageStepExecution(context, this);
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	public String getArtifactsPayload() {
		return artifactsPayload;
	}
	
	public String getName() {
		return name;
	}
	
	@DataBoundSetter
	public void setArtifactsPayload(String artifactsPayload) {
		this.artifactsPayload = artifactsPayload;
	}
	
	@DataBoundSetter
	public void setPackageName(String name) {
		this.name = name;
	}
	
	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return DevOpsConstants.ARTIFACT_PACKAGE_STEP_FUNCTION_NAME.toString();
		}

		@Override
		public String getDisplayName() {
			return DevOpsConstants.ARTIFACT_PACKAGE_STEP_DISPLAY_NAME.toString();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
		}

	}
}
