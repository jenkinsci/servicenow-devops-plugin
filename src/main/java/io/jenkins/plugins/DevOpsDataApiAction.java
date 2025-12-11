package io.jenkins.plugins;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import jenkins.model.Jenkins;
import io.jenkins.plugins.model.DevOpsPipelineGraph;
import io.jenkins.plugins.model.DevOpsPipelineNode;
import io.jenkins.plugins.model.DevOpsSecurityResultModel;
import io.jenkins.plugins.model.DevOpsSonarQubeModel;
import io.jenkins.plugins.model.DevOpsTestSummary;
import io.jenkins.plugins.model.DevOpsJFrogModel;

// Utility for logging and data access
import io.jenkins.plugins.utils.GenericUtils;


/**
 * API class for providing detailed pipeline data to ServiceNow DevOps integration
 * This is a business logic class accessed through DevOpsEndpointsAction
 */
@Extension
public class DevOpsDataApiAction {
    
    /**
     * Handles requests for pipeline details including stages and task results
     * Made public to allow calling from DevOpsEndpointsAction for unified API structure
     */
    public void handlePipelineDetailsRequest(StaplerRequest request, StaplerResponse response) throws IOException {
        String methodName = "handlePipelineDetailsRequest";
        String jobName = request.getParameter("job");
        String buildNumber = request.getParameter("build");
        String branch = request.getParameter("branch"); // Optional branch parameter for multibranch pipelines
        
        // Log incoming request with branch info
        GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                new String[]{"requestURL", "jobName", "branch", "buildNumber"}, 
                new String[]{request.getRequestURI(), jobName, branch, buildNumber}, 
                Level.INFO);
        
        // Validate required parameters
        if (GenericUtils.isEmpty(jobName) || GenericUtils.isEmpty(buildNumber)) {
            String errorMsg = "Missing required parameters: job and build";
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, errorMsg, Level.WARNING);
            
            // Return structured error response
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode errorResult = mapper.createObjectNode();
            errorResult.put("status", "error");
            errorResult.put("code", 400);
            errorResult.put("message", errorMsg);
            
            response.setStatus(400);
            response.setContentType("application/json");
            response.getWriter().write(mapper.writeValueAsString(errorResult));
            return;
        }
        
        try {
            // Find the job, handling multibranch pipelines if a branch is specified
            Job<?,?> job;
            if (!GenericUtils.isEmpty(branch)) {
                // Handle multibranch pipeline with separate branch parameter
                String fullJobPath = sanitizeJobPath(jobName + "/" + branch);
                
                // Log the path being used for lookup
                GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                    "Looking up job with fullJobPath: " + fullJobPath, Level.INFO);
                    
                job = Jenkins.get().getItemByFullName(fullJobPath, Job.class);
                
                // Log that we're using multibranch handling
                GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                    "Accessing multibranch pipeline: " + fullJobPath, Level.INFO);
                
                if (job == null) {
                    // Try finding the job directly - maybe the branch is embedded in the job name already
                    String directJobPath = sanitizeJobPath(jobName);
                    
                    // Log the direct path being used for fallback lookup
                    GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                        "Fallback lookup with direct jobPath: " + directJobPath, Level.INFO);
                        
                    job = Jenkins.get().getItemByFullName(directJobPath, Job.class);
                }
            } else {
                // Standard job lookup
                String standardJobPath = sanitizeJobPath(jobName);
                
                // Log the standard path being used for lookup
                GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                    "Standard job lookup with path: " + standardJobPath, Level.INFO);
                    
                job = Jenkins.get().getItemByFullName(standardJobPath, Job.class);
            }
            if (job == null) {
                String errorMsg = "Job not found: " + jobName;
                GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, errorMsg, Level.WARNING);
                
                // Return structured error response
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode errorResult = mapper.createObjectNode();
                errorResult.put("status", "error");
                errorResult.put("code", 404);
                errorResult.put("message", errorMsg);
                
                response.setStatus(404);
                response.setContentType("application/json");
                response.getWriter().write(mapper.writeValueAsString(errorResult));
                return;
            }
            
            Run<?,?> run = job.getBuildByNumber(Integer.parseInt(buildNumber));
            if (run == null) {
                String errorMsg = "Build not found: " + buildNumber;
                GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, errorMsg, Level.WARNING);
                
                // Return structured error response
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode errorResult = mapper.createObjectNode();
                errorResult.put("status", "error");
                errorResult.put("code", 404);
                errorResult.put("message", errorMsg);
                
                response.setStatus(404);
                response.setContentType("application/json");
                response.getWriter().write(mapper.writeValueAsString(errorResult));
                return;
            }
            
            // Get pipeline details
            ObjectNode result = getPipelineDetailsWithTasks(run);
            
            // Add status field to indicate success
            result.put("status", "success");
            result.put("code", 200);
            
            // Return as JSON
            response.setStatus(200);
            response.setContentType("application/json");
            response.getWriter().write(new ObjectMapper().writeValueAsString(result));
            
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                    "Successfully processed pipeline details request for job: " + jobName + ", build: " + buildNumber, 
                    Level.INFO);
            
        } catch (NumberFormatException e) {
            String errorMsg = "Invalid build number format: " + buildNumber;
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, errorMsg + ": " + e.getMessage(), Level.WARNING);
            
            // Return structured error response
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode errorResult = mapper.createObjectNode();
            errorResult.put("status", "error");
            errorResult.put("code", 400);
            errorResult.put("message", errorMsg);
            
            response.setStatus(400);
            response.setContentType("application/json");
            response.getWriter().write(mapper.writeValueAsString(errorResult));
        } catch (Exception e) {
            String errorMsg = "Error processing request: " + e.getMessage();
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, errorMsg, Level.SEVERE);
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, GenericUtils.getStackTraceAsString(e), Level.SEVERE);
            
            // Return structured error response
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode errorResult = mapper.createObjectNode();
            errorResult.put("status", "error");
            errorResult.put("code", 500);
            errorResult.put("message", errorMsg);
            
            response.setStatus(500);
            response.setContentType("application/json");
            response.getWriter().write(mapper.writeValueAsString(errorResult));
        }
    }
    
    /**
     * Get detailed pipeline information with tasks for each stage
     * 
     * @param run The Jenkins build run
     * @return JSON object with pipeline details
     */
    /**
     * Get detailed pipeline information with tasks for each stage
     * Enhanced to support different pipeline types (Freestyle, Scripted, Declarative)
     * 
     * @param run The Jenkins build run
     * @return JSON object with pipeline details
     */
    /**
     * Get detailed pipeline information with tasks for each stage, handling parallel and nested stages
     * in the same way as started and completed events sent to ServiceNow
     * 
     * @param run The Jenkins build run
     * @return JSON object with pipeline details in the same format as ServiceNow notifications
     */
    public ObjectNode getPipelineDetailsWithTasks(Run<?,?> run) {
        String methodName = "getPipelineDetailsWithTasks";
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        
        try {
            // Input validation
            if (run == null) {
                GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, "Run is null", Level.WARNING);
                result.put("error", "Invalid input: run parameter is null");
                return result;
            }
            
            // Extract job information safely
            String jobName;
            int buildNumber;
            String buildUrl;
            
            try {
                // Using a separate try-catch specifically for getting the parent to satisfy SpotBugs
                Job<?, ?> parent = run.getParent();
                if (parent == null) {
                    throw new NullPointerException("Run parent is null");
                }
                jobName = parent.getFullName();
                buildNumber = run.getNumber();
                buildUrl = run.getUrl();
            } catch (NullPointerException e) {
                GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                        "Error getting run information: " + e.getMessage(), Level.WARNING);
                result.put("error", "Invalid job data: " + e.getMessage());
                return result;
            }
            // Variables already declared above in try block
            
            // Safely get result - run is known to be non-null at this point
            String buildResult = "IN_PROGRESS";
            // Only check for null Result, since run is guaranteed non-null
            if (run.getResult() != null) {
                buildResult = run.getResult().toString();
            }
            
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                    new String[]{"jobName", "buildNumber", "buildResult"}, 
                    new String[]{jobName, String.valueOf(buildNumber), buildResult}, 
                    Level.INFO);
            
            // Create object in format matching DevOpsRunStatusModel structure
            result.put("jobName", jobName);
            result.put("buildNumber", buildNumber);
            result.put("buildUrl", buildUrl);
            result.put("result", buildResult);
            result.put("url", buildUrl); // Include 'url' field like in notifications
            result.put("phase", "COMPLETED"); // Always completed for API call
            
            // Add pull request information directly from DevOpsRunStatusAction
            DevOpsRunStatusAction runStatusAction = run.getAction(DevOpsRunStatusAction.class);
            if (runStatusAction != null && runStatusAction.getModel() != null && 
                runStatusAction.getModel().getPullRequestModel() != null) {
                ObjectMapper prMapper = new ObjectMapper();
                ObjectNode prNode = prMapper.valueToTree(runStatusAction.getModel().getPullRequestModel());
                result.set("pullRequestModel", prNode);
            }
            
            // Add timestamp fields in format matching notifications
            result.put("timestamp", String.valueOf(run.getStartTimeInMillis()));
            result.put("startDateTime", new Date(run.getStartTimeInMillis()).toString());
            if (run.getResult() != null) {
                long endTime = run.getStartTimeInMillis() + run.getDuration();
                result.put("endDateTime", new Date(endTime).toString());
            }
            
            // Get information on whether this is a multibranch pipeline
            String isMultiBranch = "false";
            String branchName = "";
            try {
                // Check if the parent's parent class name indicates this is a MultiBranchProject
                if (run.getParent().getParent() != null && 
                    run.getParent().getParent().getClass().getName().contains("MultiBranch")) {
                    isMultiBranch = "true";
                    branchName = run.getParent().getName();
                }
            } catch (Exception e) {
                // Ignore errors in detecting multibranch pipeline
            }
            result.put("isMultiBranch", isMultiBranch);
            if (!branchName.isEmpty()) {
                result.put("branch", branchName);
            }
            
            // Create pipeline job model for consistency with notifications
            ObjectNode jobModel = mapper.createObjectNode();
            jobModel.put("name", run.getParent().getName());
            jobModel.put("url", run.getParent().getAbsoluteUrl());
            result.set("jobModel", jobModel);
            
            // Check if this is a pipeline job
            boolean isPipeline = run instanceof WorkflowRun;
            
            // Create pipeline execution details structure
            ObjectNode pipelineExecutionDetails = mapper.createObjectNode();
            pipelineExecutionDetails.put("url", buildUrl);
            pipelineExecutionDetails.put("number", buildNumber);
            pipelineExecutionDetails.put("phase", "COMPLETED");
            pipelineExecutionDetails.put("result", buildResult);
            // Use set() method instead of deprecated put(String, JsonNode)
            pipelineExecutionDetails.set("startDateTime", result.get("startDateTime"));
            if (result.has("endDateTime")) {
                pipelineExecutionDetails.set("endDateTime", result.get("endDateTime"));
            }
            
            // Get pipeline graph details and all result data directly from DevOpsRunStatusAction
            DevOpsRunStatusAction action = run.getAction(DevOpsRunStatusAction.class);
            if (action != null) {
                GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                        "Found DevOpsRunStatusAction for build", Level.INFO);
                
                // Get the complete pipeline graph with all data
                DevOpsPipelineGraph pipelineGraph = action.getPipelineGraph();
                
                if (pipelineGraph != null) {
                    GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                            "Processing pipeline graph nodes", Level.INFO);
                    
                    // Create pipeline graph details structure
                    ObjectNode pipelineGraphDetails = mapper.createObjectNode();
                    ArrayNode stagesArray = mapper.createArrayNode();
                    
                    try {
                        // Direct access to the pipeline graph map for perfect consistency with notifications
                        Field mapField = DevOpsPipelineGraph.class.getDeclaredField("map");
                        mapField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Map<String, DevOpsPipelineNode> stageMap = (Map<String, DevOpsPipelineNode>) mapField.get(pipelineGraph);
                        
                        // Convert each stage node to a proper JSON structure
                        if (stageMap != null && !stageMap.isEmpty()) {
                            for (Map.Entry<String, DevOpsPipelineNode> entry : stageMap.entrySet()) {
                                try {
                                    DevOpsPipelineNode stageNode = entry.getValue();
                                    if (stageNode != null) {
                                        // Create a new ObjectNode and manually copy all fields to avoid serializing flowNode
                                        ObjectNode stageObject = mapper.createObjectNode();
                                        
                                        // Copy basic fields
                                        stageObject.put("id", stageNode.getId());
                                        stageObject.put("name", stageNode.getName());
                                        stageObject.put("shortName", stageNode.getShortName());
                                        stageObject.put("active", stageNode.isActive());
                                        stageObject.put("stageName", stageNode.getName());
                                        stageObject.put("pipelineName", jobName);
                                        stageObject.put("stepAssociated", stageNode.isStepAssociated());
                                        
                                        // Parent information
                                        String parentId = stageNode.getParentId();
                                        if (parentId != null && !parentId.isEmpty()) {
                                            stageObject.put("parentId", parentId);
                                            stageObject.put("parentName", stageNode.getParentName());
                                            stageObject.put("parentExecutionUrl", stageNode.getParentExecutionUrl());
                                        }
                                        
                                        // URLs and status
                                        stageObject.put("executionUrl", stageNode.getExecutionUrl());
                                        stageObject.put("pipelineExecutionUrl", stageNode.getPipelineExecutionUrl());
                                        
                                        // Timestamps and duration
                                        stageObject.put("startTime", stageNode.getStartTime());
                                        stageObject.put("duration", stageNode.getDuration());
                                        stageObject.put("changeStartTime", stageNode.getChangeStartTime());
                                        
                                        // Status fields
                                        stageObject.put("changeCtrlInProgress", stageNode.isChangeCtrlInProgress());
                                        
                                        // Upstream information
                                        String upstreamTaskExecURL = stageNode.getUpstreamTaskExecURL();
                                        if (upstreamTaskExecURL != null && !upstreamTaskExecURL.isEmpty()) {
                                            stageObject.put("upstreamTaskExecutionURL", upstreamTaskExecURL);
                                            stageObject.put("upstreamStageName", stageNode.getUpstreamStageName());
                                        }
                                        
                                        // Add status field - using reflection to get stageExecutionStatus
                                        try {
                                            java.lang.reflect.Field statusField = DevOpsPipelineNode.class.getDeclaredField("stageExecutionStatus");
                                            statusField.setAccessible(true);
                                            String status = (String) statusField.get(stageNode);
                                            
                                            if (status != null && !status.isEmpty()) {
                                                stageObject.put("status", status);
                                                stageObject.put("stageExecutionStatus", status);
                                            } else {
                                                stageObject.put("status", stageNode.isStepAssociated() ? "COMPLETED" : "STARTED");
                                            }
                                        } catch (NoSuchFieldException | IllegalAccessException ex) {
                                            // If reflection fails, fall back to a default value
                                            stageObject.put("status", stageNode.isStepAssociated() ? "COMPLETED" : "STARTED");
                                        }
                                        
                                        // Check for and include stageStatusFromTag in the API response
                                        // This field represents the stage status derived from pipeline tags
                                        // Following the same pattern as in DevOpsRunListener, only include the field 
                                        // when it contains actual data to keep the JSON payload clean
                                        String stageStatusFromTag = stageNode.getStageStatusFromTag();
                                        if (stageStatusFromTag != null && !stageStatusFromTag.isEmpty()) {
                                            stageObject.put("stageStatusFromTag", stageStatusFromTag);
                                        }
                                        
                                        // Set waitForChildExecutions
                                        stageObject.put("waitForChildExecutions", parentId != null && !parentId.isEmpty() ? "true" : "false");
                                        
                                        // Add task results (tests, SonarQube, security) for this stage
                                        addTaskResultsToStage(stageObject, stageNode.getId(), pipelineGraph, mapper);
                                        
                                        // Add stage to stages array
                                        stagesArray.add(stageObject);
                                    }
                                } catch (IllegalArgumentException | NullPointerException | SecurityException ex) {
                                    // Log error but continue processing other nodes
                                    GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                                            "Error processing individual pipeline node: " + ex.getMessage(), Level.WARNING);
                                }
                            }
                        }
                        
                        // Set stages array in pipeline graph details
                        pipelineGraphDetails.set("nodes", stagesArray);
                        
                        // Add pipeline graph details to pipeline execution details
                        pipelineExecutionDetails.set("pipelineGraphDetails", pipelineGraphDetails);
                        
                        // Results are included per-stage in the pipeline graph nodes
                        // No need to duplicate this data at the top level
                        GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                                "Pipeline graph details already contain stage-specific results - avoiding duplication", Level.INFO);
                        // Keeping only stage-specific results in the pipeline graph details
                        // This reduces response size and avoids duplicate data
                        
                    // This section only throws specific exceptions that are caught here
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName,
                                "Error accessing field via reflection: " + e.getMessage(), Level.WARNING);
                    } catch (NullPointerException | ClassCastException e) {
                        GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName,
                                "Error processing pipeline graph data: " + e.getMessage(), Level.WARNING);
                    }
                } else if (isPipeline) {
                    // For pipeline jobs without a graph in DevOpsRunStatusAction
                    GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                            "No pipeline graph found in DevOpsRunStatusAction", Level.WARNING);
                    
                    // Add error message about missing ServiceNow Instance configuration
                    pipelineExecutionDetails.put("error", "Pipeline is either not tracked or Jenkins is not configured with proper ServiceNow Instance credentials. Please ensure Jenkins is properly configured with ServiceNow Instance credentials, verify the pipeline is tracked, and then run the pipeline again before attempting to import data.");
                } else {
                    // For non-pipeline jobs, just inform the user
                    GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                            "Non-pipeline job detected", Level.INFO);
                    pipelineExecutionDetails.put("message", "This is not a pipeline job. Only pipeline jobs can be tracked in ServiceNow DevOps.");
                }
            } else {
                // No DevOpsRunStatusAction found for build
                GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                        "No DevOpsRunStatusAction found for build", Level.WARNING);
                
                if (isPipeline) {
                    // Provide guidance about missing plugin
                    pipelineExecutionDetails.put("error", "ServiceNow DevOps integration plugin is not installed or configured for this Jenkins instance. Please install the ServiceNow DevOps plugin, configure it with your ServiceNow Instance credentials, and ensure pipeline tracking is enabled before attempting to import data.");
                } else {
                    // This is not a pipeline job
                    pipelineExecutionDetails.put("message", "This is not a pipeline job. Only pipeline jobs can be tracked in ServiceNow DevOps.");
                }
            }
        
            // Add pipeline execution details
            result.set("pipelineExecutionDetails", pipelineExecutionDetails);
            
            // Add status and code for API response
            result.put("status", "success");
            result.put("code", 200);
            
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                    "Successfully generated pipeline details", Level.INFO);
            
        // Instead of catching a generic Exception, use specific exception types
        // that might actually be thrown in this method
        } catch (NullPointerException e) {
            String errorMsg = "Error generating pipeline details (null value encountered): " + e.getMessage();
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, errorMsg, Level.SEVERE);
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, GenericUtils.getStackTraceAsString(e), Level.SEVERE);
            result.put("error", errorMsg);
        } catch (RuntimeException e) {
            // Catch other runtime exceptions like ClassCastException, IllegalArgumentException, etc.
            String errorMsg = "Error generating pipeline details: " + e.getMessage();
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, errorMsg, Level.SEVERE);
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, GenericUtils.getStackTraceAsString(e), Level.SEVERE);
            result.put("error", errorMsg);
        }
        
        return result;
    }
    
    
    /**
     * Add task results (tests, security scans, etc.) to a stage
     */
    /**
     * Add task results (tests, security scans, etc.) to a stage
     * 
     * Optimized for performance with large pipelines by:
     * 1. Using a shared cache for reflection operations (avoid redundant reflection)
     * 2. Using direct access to sets when possible instead of creating intermediate collections
     * 3. Filtering directly during iteration instead of creating a filtered list
     * 
     * @param stageObject The JSON object representing the stage
     * @param stageNodeId The node ID of the stage
     * @param pipelineGraph The pipeline graph containing task results
     * @param mapper JSON object mapper
     */
    private void addTaskResultsToStage(ObjectNode stageObject, String stageNodeId, DevOpsPipelineGraph pipelineGraph, ObjectMapper mapper) {
        String methodName = "addTaskResultsToStage";
        
        // Use a static cache for reflection fields to avoid redundant lookups
        // This dramatically improves performance for large pipelines
        Map<String, Field> reflectionCache = getReflectionCache();
        
        // Initialize results arrays - will only be added to the response if they contain data
        ArrayNode testResults = mapper.createArrayNode();
        ArrayNode sonarResults = mapper.createArrayNode();
        ArrayNode securityResultsArray = mapper.createArrayNode();
        
        // Track result counts for logging
        int testCount = 0;
        int sonarCount = 0;
        int securityCount = 0;
        int jfrogCount = 0;
        
        // Initialize JFrog results array
        ArrayNode jfrogResults = mapper.createArrayNode();
        
        // Process test results
        try {
            // Get the field from cache or via reflection
            Field testResultsField = reflectionCache.get("jobTestResults");
            if (testResultsField == null) {
                testResultsField = DevOpsPipelineGraph.class.getDeclaredField("jobTestResults");
                testResultsField.setAccessible(true);
                reflectionCache.put("jobTestResults", testResultsField);
            }
            
            // Get the test results set
            @SuppressWarnings("unchecked")
            Set<DevOpsTestSummary> testResultsSet = (Set<DevOpsTestSummary>) testResultsField.get(pipelineGraph);
            
            // Process only if we have results
            if (testResultsSet != null && !testResultsSet.isEmpty()) {
                // Filter and process in one pass
                for (DevOpsTestSummary testSummary : testResultsSet) {
                    // Only process results for this stage
                    if (stageNodeId.equals(testSummary.getStageNodeId())) {
                        ObjectNode testNode = mapper.createObjectNode();
                        testNode.put("name", testSummary.getName());
                        testNode.put("passedTests", testSummary.getPassedTests());
                        testNode.put("failedTests", testSummary.getFailedTests());
                        testNode.put("skippedTests", testSummary.getSkippedTests());
                        testNode.put("totalTests", testSummary.getTotalTests());
                        testNode.put("duration", testSummary.getDuration());
                        testNode.put("url", testSummary.getUrl());
                        testResults.add(testNode);
                        testCount++;
                    }
                }
            }
        } catch (Exception e) {
            // Don't fail the entire API call if one result type has issues
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName,
                    "Could not access test results: " + e.getMessage(), Level.WARNING);
        }
        
        // Process SonarQube results
        try {
            // Get the field from cache or via reflection
            Field sonarResultsField = reflectionCache.get("jobSonarQubeModelResults");
            if (sonarResultsField == null) {
                sonarResultsField = DevOpsPipelineGraph.class.getDeclaredField("jobSonarQubeModelResults");
                sonarResultsField.setAccessible(true);
                reflectionCache.put("jobSonarQubeModelResults", sonarResultsField);
            }
            
            // Get the sonar results set
            @SuppressWarnings("unchecked")
            Set<DevOpsSonarQubeModel> sonarResultsSet = (Set<DevOpsSonarQubeModel>) sonarResultsField.get(pipelineGraph);
            
            // Process only if we have results
            if (sonarResultsSet != null && !sonarResultsSet.isEmpty()) {
                // Filter and process in one pass
                for (DevOpsSonarQubeModel sonarModel : sonarResultsSet) {
                    // Only process results for this stage
                    if (stageNodeId.equals(sonarModel.getStageNodeId())) {
                        ObjectNode sonarNode = mapper.createObjectNode();
                        sonarNode.put("scanID", sonarModel.getScanID());
                        sonarNode.put("url", sonarModel.getUrl());
                        sonarResults.add(sonarNode);
                        sonarCount++;
                    }
                }
            }
        } catch (Exception e) {
            // Don't fail the entire API call if one result type has issues
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName,
                    "Could not access SonarQube results: " + e.getMessage(), Level.WARNING);
        }
        
        // Process security results
        try {
            // Get the field from cache or via reflection
            Field securityResultsField = reflectionCache.get("jobSecurityResults");
            if (securityResultsField == null) {
                securityResultsField = DevOpsPipelineGraph.class.getDeclaredField("jobSecurityResults");
                securityResultsField.setAccessible(true);
                reflectionCache.put("jobSecurityResults", securityResultsField);
            }
            
            // Get the security results set
            @SuppressWarnings("unchecked")
            Set<DevOpsSecurityResultModel> securityResultsSet = 
                    (Set<DevOpsSecurityResultModel>) securityResultsField.get(pipelineGraph);
            
            // Process only if we have results
            if (securityResultsSet != null && !securityResultsSet.isEmpty()) {
                // Filter and process in one pass
                for (DevOpsSecurityResultModel securityModel : securityResultsSet) {
                    // Only process results for this stage
                    if (stageNodeId.equals(securityModel.getStageNodeId())) {
                        ObjectNode securityNode = mapper.createObjectNode();
                        
                        // Parse security attributes as JSON instead of using toString()
                        try {
                            // Get the attributes string
                            String attributesJson = securityModel.getSecurityResultAttributes().toString();
                            
                            // Parse it as a JsonNode
                            JsonNode attributesNode = mapper.readTree(attributesJson);
                            
                            // Add it as a proper JSON object, not a string
                            securityNode.set("attributes", attributesNode);
                        } catch (Exception ex) {
                            // Fall back to string if JSON parsing fails
                            securityNode.put("attributes", securityModel.getSecurityResultAttributes().toString());
                            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName,
                                "Error parsing security attributes as JSON: " + ex.getMessage(), Level.FINE);
                        }
                        
                        securityResultsArray.add(securityNode);
                        securityCount++;
                    }
                }
            }
        } catch (Exception e) {
            // Don't fail the entire API call if one result type has issues
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName,
                    "Could not access security results: " + e.getMessage(), Level.WARNING);
        }
        
        // Process JFrog results
        try {
            // Get the field from cache or via reflection
            Field jfrogResultsField = reflectionCache.get("jobJFrogModelResults");
            if (jfrogResultsField == null) {
                jfrogResultsField = DevOpsPipelineGraph.class.getDeclaredField("jobJFrogModelResults");
                jfrogResultsField.setAccessible(true);
                reflectionCache.put("jobJFrogModelResults", jfrogResultsField);
            }
            
            // Get the JFrog results set
            @SuppressWarnings("unchecked")
            Set<DevOpsJFrogModel> jfrogResultsSet = 
                    (Set<DevOpsJFrogModel>) jfrogResultsField.get(pipelineGraph);
            
            // Process only if we have results
            if (jfrogResultsSet != null && !jfrogResultsSet.isEmpty()) {
                // Filter and process in one pass - only include JFrog results for this specific stage
                for (DevOpsJFrogModel jfrogModel : jfrogResultsSet) {
                    // Only process results for this stage or include if no stageNodeId is set
                    // Note: JFrog results with null stageNodeId intentionally appear in all stages
                    // This handles artifacts created outside the pipeline stage context
                    if (Objects.equals(stageNodeId, jfrogModel.getStageNodeId()) || jfrogModel.getStageNodeId() == null) {
                        ObjectNode jfrogNode = mapper.createObjectNode();
                        jfrogNode.put("buildName", jfrogModel.getBuildName());
                        jfrogNode.put("buildNumber", jfrogModel.getBuildNumber());
                        jfrogNode.put("startedTimeStamp", jfrogModel.getStartedTimeStamp());
                        jfrogNode.put("artifactoryUrl", jfrogModel.getArtifactoryUrl());
                        
                        // Log warning if artifactoryUrl is empty
                        if (jfrogModel.getArtifactoryUrl() == null || jfrogModel.getArtifactoryUrl().isEmpty()) {
                            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName,
                                "Warning: Empty artifactoryUrl for JFrog build " + jfrogModel.getBuildName() 
                                + ":" + jfrogModel.getBuildNumber(), Level.WARNING);
                        }
                        
                        jfrogResults.add(jfrogNode);
                        jfrogCount++;
                    }
                }
            }
        } catch (Exception e) {
            // Don't fail the entire API call if one result type has issues
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName,
                    "Could not access JFrog results: " + e.getMessage(), Level.WARNING);
        }
        
        // Set the results arrays on the stage object ONLY if they contain data
        // This prevents empty arrays from being included in the response
        if (testResults.size() > 0) {
            stageObject.set("testResults", testResults);
        }
        if (sonarResults.size() > 0) {
            stageObject.set("sonarResults", sonarResults);
        }
        if (securityResultsArray.size() > 0) {
            stageObject.set("securityResults", securityResultsArray);
        }
        if (jfrogResults.size() > 0) {
            stageObject.set("jfrogResults", jfrogResults);
        }
        
        // Only log at FINE level if we found any results
        if (testCount > 0 || sonarCount > 0 || securityCount > 0 || jfrogCount > 0) {
            GenericUtils.printDebug(DevOpsDataApiAction.class.getName(), methodName, 
                    String.format("Stage %s: Added %d test results, %d SonarQube results, %d security results, %d JFrog results", 
                            stageNodeId, testCount, sonarCount, securityCount, jfrogCount), Level.FINE);
        }
    }
    
    /**
     * Cache for reflection fields to avoid redundant lookups
     * This dramatically improves performance for large pipelines with many stages
     */
    private static final Map<String, Field> reflectionFieldCache = new HashMap<>();
    
    /**
     * Sanitizes a Jenkins job path to handle special characters and properly encode them for Jenkins item lookup.
     * This method handles special characters that commonly appear in branch names like slashes, dots, etc.
     *
     * @param path The job path to sanitize
     * @return The sanitized path ready for Jenkins item lookup
     */
    private String sanitizeJobPath(String path) {
        if (path == null) {
            return null;
        }
        
        // Handle common special characters in branch names that might need special treatment
        // Some branch names may contain slashes (feature/branch), dots (release.1.0), etc.
        
        // Remove any double slashes that might have been introduced
        path = path.replaceAll("\\/+", "/");
        
        // Handle trailing slashes
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        
        return path;
    }
    
    /**
     * Get the reflection cache for field lookups
     */
    private Map<String, Field> getReflectionCache() {
        return reflectionFieldCache;
    }
    
    /**
     * Add pull request information to the API response if the job appears to be a pull request
     * Uses the same format as started/completed events
     * 
     * @param run The current build run
     * @param result The JSON result object to add pull request info to
     * @param jobName The name of the job
     */

    // Note: The readPipelineDetailsFromBuildXml method and its helper getElementTextContent have been removed as they are no longer used
}
