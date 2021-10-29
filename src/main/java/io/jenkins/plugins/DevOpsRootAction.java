package io.jenkins.plugins;

import java.io.IOException;
import java.util.HashMap;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.Extension;
import hudson.model.RootAction;
import hudson.security.csrf.CrumbExclusion;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.pipeline.steps.executions.DevOpsPipelineChangeStepExecution;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;

import java.io.BufferedReader;
import java.nio.CharBuffer;
import java.util.logging.Level;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import hudson.FilePath;

import java.io.UnsupportedEncodingException;  
import java.net.URLDecoder;


@Extension
public class DevOpsRootAction extends CrumbExclusion implements RootAction {
	
	private static final HashMap<String, String> webhooks = new HashMap<>(); // token->jobId (Dispatcher)
	private static final HashMap<String, String> jobs = new HashMap<>(); 	 // jobId->token (Dispatcher)
	private static final HashMap<String, String> callbackContent = new HashMap<>();// jobId->callbackResponse (Dispatcher/FreestyleStep)
	private static final HashMap<String, String> callbackToken = new HashMap<>(); // jobId->token (FreestyleStep)
    private static final HashMap<String, DevOpsPipelineChangeStepExecution> pipelineWebhooks = new HashMap<>(); // token->asyncStepExecution (PipelineChangeStep)
    private static final HashMap<String, String> changeRequestContent = new HashMap<>(); // jobId->callbackResponse (Dispatcher/FreestyleStep)
    
    private static final HashMap<String, Boolean> trackedJobs = new HashMap<>(); // runId->True/False
	private static final HashMap<String, DevOpsModel.DevOpsPipelineInfo> snPipelineInfo = new HashMap<>(); // runId
	// ->JSONObject
	
	@Override
	public String getIconFileName() {
		return null;
	}

	@Override
	public String getDisplayName() {
		return null;
	}

	@Override
	public String getUrlName() {
		return DevOpsConstants.CALLBACK_URL_IDENTIFIER.toString();
    }
    
    private boolean _handleFreestyleCallback(String token, StringBuffer content) {
        // cross validation to make sure the token received matches the one we had mapped to this jobId
        String jobId;
        synchronized (webhooks) { jobId = webhooks.remove(token); }
        String originalToken;
        synchronized (jobs) { originalToken = jobs.remove(jobId); }
        if (jobId != null && originalToken.equals(token)) {
        	synchronized (callbackContent) { callbackContent.put(jobId, content.toString().trim()); }
            synchronized (callbackToken) { callbackToken.put(jobId, token); }
            return true;
        } 
        return false;
    }

    private boolean _handlePipelineCallback(String token, StringBuffer content) {
        DevOpsPipelineChangeStepExecution exec;
        synchronized (pipelineWebhooks) { exec = pipelineWebhooks.remove(token); }
        if (exec != null) {
            exec.onTriggered(token, content.toString().trim());
            return true;
        } 
        return false;
    }

    private boolean _displayFreestyleChangeRequestInfo(String token, StringBuffer content) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_displayFreestyleChangeRequestInfo", new String[]{"token"}, new String[]{token}, Level.INFO);
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_displayFreestyleChangeRequestInfo", new String[]{"content"}, new String[]{content.toString()}, Level.INFO);
        String jobId;
        synchronized (webhooks) { jobId = webhooks.get(token); }
        String originalToken;
        synchronized (jobs) { originalToken = jobs.get(jobId); }
        if (jobId != null && originalToken.equals(token)) {
        	synchronized (changeRequestContent) { changeRequestContent.put(jobId, content.toString().trim()); }
            return true;
        } 
        return false;
    }

    private boolean _displayPipelineChangeRequestInfo(String token, StringBuffer content) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_displayPipelineChangeRequestInfo", new String[]{"token"}, new String[]{token}, Level.INFO);
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "_displayPipelineChangeRequestInfo", new String[]{"content"}, new String[]{content.toString()}, Level.INFO);
        DevOpsPipelineChangeStepExecution exec;
        synchronized (pipelineWebhooks) { exec = pipelineWebhooks.get(token); }
        if (exec != null) {
            exec.displayPipelineChangeRequestInfo(token, content.toString().trim());
            return true;
        } 
        return false;
    }

    public static String decode(String url)  
    {  
         try {  
            String prevURL = "";  
            String decodeURL = url;  
            while(!prevURL.equals(decodeURL))  
            {  
                prevURL = decodeURL;  
                decodeURL = URLDecoder.decode(decodeURL, "UTF-8" );  
            }  
            return decodeURL;  
        } catch (UnsupportedEncodingException e) {
            GenericUtils.printDebug(DevOpsRootAction.class.getName(), "decode", new String[]{"UnsupportedEncodingException"}, new String[]{e.getMessage()}, Level.SEVERE);
			return url;  
        }  
    }

    //This method return /{JENKINS_HOME}/jobs/{jobName}/snPipelineInfo.json 
    //This method return /{JENKINS_HOME}/jobs/{folderName}/jobs/{jobName}/snPipelineInfo.json
	public static String getRootDirFilePath(String jobName) {
        DevOpsModel devopsModel = new DevOpsModel();
		String jenkinsDirFilePath = devopsModel.getJenkinsRootDirPath();
        jobName = decode(jobName);
        if(jobName.contains(DevOpsConstants.PATH_SEPARATOR.toString()))
            jobName = jobName.replace(DevOpsConstants.PATH_SEPARATOR.toString(), DevOpsConstants.JOBS_PATH.toString());
		String finalPath = jenkinsDirFilePath + DevOpsConstants.JOBS_PATH.toString() + jobName + DevOpsConstants.PATH_SEPARATOR.toString() + DevOpsConstants.SERVICENOW_PIPELINE_INFO_FILE_NAME.toString();
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "getRootDirFilePath", new String[]{"jobName"}, new String[]{finalPath}, Level.INFO);
		return finalPath;
	}
    
    /*
    *method called when api path : /sn-devops/snupdate_{jobName}
    *returns 200 is success else 410
    *file path will be {JENKINS_HOME}/snPipelineInfo.json
    *Request data format:
    *{track:true, "testInfo": {"stages": {"Build": "/Users/StepBuild","Deploy": "/Users/Deploy"}}, "pipeline": "/Users/TestSummary")
    *Note: This api doesn't make new entries in the file
    *for multibranch pipeline using ONLY jobName as the key
    */
    private boolean _updatePipelineInfoFile(String token, StringBuffer content) {
        String jobName = token.split(DevOpsConstants.CALLBACK_TOKEN_SEPARATOR.toString(), 2)[1];
        jobName = decode(jobName);
        if (jobName != null) {
            JSONObject apiResponse = JSONObject.fromObject(content.toString());
            if (apiResponse != null) {
			    String rootDirFilePath = getRootDirFilePath(jobName);
                if(updateResponseInFile(jobName, apiResponse, rootDirFilePath)){
                    return true;
                }
            }
        }
        return false;
    }

    public boolean updateResponseInFile(String jobName, JSONObject apiResponse, String rootDirFilePath) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "updateResponseInFile", new String[]{"jobName"}, new String[]{jobName}, Level.INFO);
        try{
            FilePath pipelineInfoFile = new FilePath(new File(rootDirFilePath));
			if(pipelineInfoFile.exists()){
				String fileContents = pipelineInfoFile.readToString();
				JSONObject pipelineInfo = JSONObject.fromObject(fileContents);
			    if (pipelineInfo != null) {
                    if (pipelineInfo.containsKey(jobName)) {
                        JSONObject updatedResponse = getUpdatedResponse(apiResponse, pipelineInfo.getJSONObject(jobName));
                        pipelineInfo.put(jobName, updatedResponse);
                        pipelineInfoFile.write(pipelineInfo.toString(), "UTF-8");
                        return true;
                    }
			    }
            }
            return false;
        } catch (Exception e) {
            GenericUtils.printDebug(DevOpsRootAction.class.getName(), "updateResponseInFile", new String[]{"Exception"}, new String[]{e.getMessage()}, Level.SEVERE);
			return false;
        }
	}
    
    /*
    *edits the request data to match the format of file data
    */
    public JSONObject getUpdatedResponse(JSONObject apiResponse, JSONObject fileData) {
        JSONObject pipelineInfo = fileData.getJSONObject(DevOpsConstants.COMMON_RESPONSE_RESULT.toString());
        if (pipelineInfo != null) {
            if(apiResponse.containsKey(DevOpsConstants.TRACKING_RESPONSE_ATTR.toString())){
                pipelineInfo.put(DevOpsConstants.TRACKING_RESPONSE_ATTR.toString(), apiResponse.get(DevOpsConstants.TRACKING_RESPONSE_ATTR.toString()));
            }
            if(apiResponse.containsKey(DevOpsConstants.TEST_INFO_RESPONSE.toString())){
                pipelineInfo.put(DevOpsConstants.TEST_INFO_RESPONSE.toString(), apiResponse.get(DevOpsConstants.TEST_INFO_RESPONSE.toString()));
            }
        }
        fileData.put(DevOpsConstants.COMMON_RESPONSE_RESULT.toString(), pipelineInfo);
        return fileData;
    }
	
	public void doDynamic(StaplerRequest request, StaplerResponse response) {
		// read response content
        String token = request.getOriginalRestOfPath().substring(1).trim(); //Strip leading slash

        CharBuffer dest = CharBuffer.allocate(1024);
        StringBuffer content = new StringBuffer();
        try {
            BufferedReader reader = request.getReader();
            while (reader.read(dest) > 0) {
                dest.rewind();
                content.append(dest.toString());
            }
        } catch (IOException e) {
            response.setStatus(400);
            return;
        }

        if (token.startsWith(DevOpsConstants.FREESTYLE_CALLBACK_URL_IDENTIFIER.toString()) && content != null && content.length() > 0 && content.toString().trim().contains("changeRequestId")) {
            boolean result = _displayFreestyleChangeRequestInfo(token, content);
            if (result) {
                response.setStatus(200);
                return;
            }
            response.setStatus(400);
            return;

        } else if (token.startsWith(DevOpsConstants.PIPELINE_CALLBACK_URL_IDENTIFIER.toString()) && content != null && content.length() > 0 && content.toString().trim().contains("changeRequestId")) {
            boolean result = _displayPipelineChangeRequestInfo(token, content);
            if (result) {
                response.setStatus(200);
                return;
            }
            response.setStatus(400);
            return;
        }

        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", new String[]{"message"}, new String[]{"Callback handler called with token: " + token + " / content: " + content.toString()}, Level.INFO);

        boolean result = false;
        if (token.startsWith(DevOpsConstants.FREESTYLE_CALLBACK_URL_IDENTIFIER.toString()) && content != null && content.length() > 0)
            result = _handleFreestyleCallback(token, content);
        else if (token.startsWith(DevOpsConstants.PIPELINE_CALLBACK_URL_IDENTIFIER.toString()) && content != null && content.length() > 0)
            result = _handlePipelineCallback(token, content);
        else if (token.startsWith(DevOpsConstants.PIPELINE_INFO_UPDATE_IDENTIFIER.toString()) && content != null && content.length() > 0)
            result = _updatePipelineInfoFile(token, content);

        if (result) {
            response.setHeader("Result", "Jenkins webhook triggered successfully");
            response.setStatus(200);
        }
        else
            response.setStatus(410); // 410 Gone
    }


    public static JSONObject checkInfoInFile(String jobName, String branchName, boolean isMultiBranch) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "checkInfoInFile", new String[]{"jobName"}, new String[]{jobName}, Level.INFO);
		try{
            String rootDirFilePath = getRootDirFilePath(jobName);
            FilePath pipelineInfoFile = new FilePath(new File(rootDirFilePath));
			if(!pipelineInfoFile.exists()){
				return null;
			}
			String fileContents = pipelineInfoFile.readToString();
			JSONObject pipelineInfo = JSONObject.fromObject(fileContents);
			if (pipelineInfo != null) {
				if (pipelineInfo.containsKey(jobName)) {
					JSONObject response = pipelineInfo.getJSONObject(jobName);
					if (GenericUtils.checkIfAttributeExist(response, DevOpsConstants.TRACKING_RESPONSE_ATTR.toString()))
						return response;
				}
			}
			return null;
        } catch (Exception e) {
            GenericUtils.printDebug(DevOpsRootAction.class.getName(), "checkInfoInFile", new String[]{"Exception"}, new String[]{e.getMessage()}, Level.SEVERE);
			return null;
        }
	}

	public static Boolean updateInfoInFile(String jobName, JSONObject infoAPIResponse, String branchName, boolean isMultiBranch) {
		GenericUtils.printDebug(DevOpsRootAction.class.getName(), "updateInfoInFile", new String[]{"jobName"}, new String[]{jobName}, Level.INFO);
		try{
            String rootDirFilePath = getRootDirFilePath(jobName);
            FilePath pipelineInfoFile = new FilePath(new File(rootDirFilePath));
			JSONObject pipelineInfo = new JSONObject();
			if(pipelineInfoFile.exists()){
				String fileContents = pipelineInfoFile.readToString();
				pipelineInfo = JSONObject.fromObject(fileContents);
			}
			pipelineInfo.put(jobName, infoAPIResponse);
			pipelineInfoFile.write(pipelineInfo.toString(), "UTF-8");
			return true;
        } catch (Exception e) {
            GenericUtils.printDebug(DevOpsRootAction.class.getName(), "updateInfoInFile", new String[]{"Exception"}, new String[]{e.getMessage()}, Level.SEVERE);
			return false;
        }
	}
    
    public static Boolean getTrackedJob(String key) {
        Boolean tracking;
        synchronized(trackedJobs) { tracking = trackedJobs.get(key); }
		return tracking;
    }
    
    public static void setTrackedJob(String key) {
        Boolean tracking = Boolean.valueOf(true);
        synchronized (trackedJobs) { trackedJobs.put(key, tracking); }
    }
    
    public static Boolean removeTrackedJob(String key) {
        Boolean tracking=false;
        synchronized (trackedJobs) {
	        if (trackedJobs.containsKey(key)) {
		        tracking = trackedJobs.remove(key);
	        }
        }
        return tracking;
	}

	public static void setSnPipelineInfo(String key, DevOpsModel.DevOpsPipelineInfo pipelineInfo) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "setSnPipelineInfo", new String[]{"key"}, new String[]{key}, Level.FINE);
		synchronized (snPipelineInfo) { snPipelineInfo.put(key, pipelineInfo); }
	}

	public static DevOpsModel.DevOpsPipelineInfo getSnPipelineInfo(String key) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "getSnPipelineInfo", new String[]{"key"}, new String[]{key}, Level.FINE);
		DevOpsModel.DevOpsPipelineInfo pipelineInfo;
		synchronized (snPipelineInfo) { pipelineInfo = snPipelineInfo.get(key); }
		return pipelineInfo;
	}

	public static void removeSnPipelineInfo(String key){
		synchronized (snPipelineInfo) {
			if (snPipelineInfo.containsKey(key)){
				snPipelineInfo.remove(key);
			}
		}
	}

	public static String getChangeRequestContent(String jobId) {
        String content;
        synchronized(changeRequestContent) { content = changeRequestContent.get(jobId); }
		return content;
	}

    public static String removeChangeRequestContent(String jobId) {
        String content;
        synchronized(changeRequestContent) { content = changeRequestContent.remove(jobId); }
		return content;
	}

	// called from dispatcher
	public static String getCallbackContent(String jobId) {
        String content;
        synchronized(callbackContent) { content = callbackContent.get(jobId); }
		return content;
	}
	
	public static String removeCallbackContent(String jobId) {
		String content;
		synchronized (callbackContent) { content = callbackContent.remove(jobId); }
		return content;
    }
    
    // called from dispatcher
    public static void setCallbackContent(String jobId, String content) {
        if (jobId != null && content != null) {
            synchronized (callbackContent) { callbackContent.put(jobId, content.trim()); }
        }
    }
	
    public static String removeCallbackToken(String jobId) {
    	String token;
    	synchronized (callbackToken) { token = callbackToken.remove(jobId); }
    	return token;
    }
	
	public static String getToken(String jobId) {
        String token;
		synchronized(jobs) { token = jobs.get(jobId); }
		return token;
	}
	
	public static String getJobId(String token) {
        String jobId;
		synchronized(webhooks) { jobId = webhooks.get(token); }
		return jobId;
	}
	// called from task dispatcher
    public static void registerWebhook(String token, String jobId) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "registerWebhook", new String[]{"message"}, new String[]{"Registering freestyle webhook with token: " + token}, Level.INFO);
        synchronized (webhooks) { webhooks.put(token, jobId);}
    }
    // called from task dispatcher
    public static void registerJob(String jobId, String token) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "registerJob", new String[]{"message"}, new String[]{"Registering freestyle job with id: " + jobId}, Level.INFO);
        synchronized (jobs) { jobs.put(jobId, token);}
    }
    // not used
    public static void deregisterWebhook(String token) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "deregisterWebhook", new String[]{"message"}, new String[]{"Deregistering freestyle webhook with token: " + token}, Level.INFO);
        synchronized (webhooks) { webhooks.remove(token); }
    }
    // not used
    public static void deregisterJob(String jobId) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "deregisterJob", new String[]{"message"}, new String[]{"Deregistering freestyle job with id: " + jobId}, Level.INFO);
        synchronized (jobs) { jobs.remove(jobId); }
	}

	public static void registerPipelineWebhook(DevOpsPipelineChangeStepExecution exec) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "registerPipelineWebhook", new String[]{"message"}, new String[]{"Registering pipeline webhook with token: " + exec.getToken()}, Level.INFO);
        synchronized (pipelineWebhooks) { pipelineWebhooks.put(exec.getToken(), exec);}
    }

    public static void deregisterPipelineWebhook(DevOpsPipelineChangeStepExecution exec) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "deregisterPipelineWebhook", new String[]{"message"}, new String[]{"Deregistering pipeline webhook with token: " + exec.getToken()}, Level.INFO);
        synchronized (pipelineWebhooks) { pipelineWebhooks.remove(exec.getToken()); }
    }
    
    // Intercepts the incoming HTTP requests, looking for the /sn-devops/ URL identifier. If one is found, 
    // forward it to the next filter in the chain (doDynamic) which will verify if the token is valid
	@Override
	public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		String pathInfo = request.getPathInfo();
		if (pathInfo != null && pathInfo.startsWith("/"+DevOpsConstants.CALLBACK_URL_IDENTIFIER.toString()+"/")) {
            chain.doFilter(request, response);
            return true;
        }
        return false;
	}

}
