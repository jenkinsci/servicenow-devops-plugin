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

@Extension
public class DevOpsRootAction extends CrumbExclusion implements RootAction {
	
	private static final HashMap<String, String> webhooks = new HashMap<>(); // token->jobId (Dispatcher)
	private static final HashMap<String, String> jobs = new HashMap<>(); 	 // jobId->token (Dispatcher)
	private static final HashMap<String, String> callbackContent = new HashMap<>();// jobId->callbackResponse (Dispatcher/FreestyleStep)
	private static final HashMap<String, String> callbackToken = new HashMap<>(); // jobId->token (FreestyleStep)
    private static final HashMap<String, DevOpsPipelineChangeStepExecution> pipelineWebhooks = new HashMap<>(); // token->asyncStepExecution (PipelineChangeStep)
    
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

        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "doDynamic", new String[]{"message"}, new String[]{"Callack handler called with token: " + token + " / content: " + content.toString()}, true);

        boolean result = false;
        if (token.startsWith(DevOpsConstants.FREESTYLE_CALLBACK_URL_IDENTIFIER.toString()) && content != null && content.length() > 0)
            result = _handleFreestyleCallback(token, content);
        else if (token.startsWith(DevOpsConstants.PIPELINE_CALLBACK_URL_IDENTIFIER.toString()) && content != null && content.length() > 0)
            result = _handlePipelineCallback(token, content);

        if (result) {
            response.setHeader("Result", "Jenkins webhook triggered successfully");
            response.setStatus(200);
        }
        else
            response.setStatus(410); // 410 Gone
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
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "setSnPipelineInfo", new String[]{"key"}, new String[]{key}, true);
		synchronized (snPipelineInfo) { snPipelineInfo.put(key, pipelineInfo); }
	}

	public static DevOpsModel.DevOpsPipelineInfo getSnPipelineInfo(String key) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "getSnPipelineInfo", new String[]{"key"}, new String[]{key}, true);
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
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "registerWebhook", new String[]{"message"}, new String[]{"Registering freestyle webhook with token: " + token}, true);
        synchronized (webhooks) { webhooks.put(token, jobId);}
    }
    // called from task dispatcher
    public static void registerJob(String jobId, String token) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "registerJob", new String[]{"message"}, new String[]{"Registering freestyle job with id: " + jobId}, true);
        synchronized (jobs) { jobs.put(jobId, token);}
    }
    // not used
    public static void deregisterWebhook(String token) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "deregisterWebhook", new String[]{"message"}, new String[]{"Deregistering freestyle webhook with token: " + token}, true);
        synchronized (webhooks) { webhooks.remove(token); }
    }
    // not used
    public static void deregisterJob(String jobId) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "deregisterJob", new String[]{"message"}, new String[]{"Deregistering freestyle job with id: " + jobId}, true);
        synchronized (jobs) { jobs.remove(jobId); }
	}

	public static void registerPipelineWebhook(DevOpsPipelineChangeStepExecution exec) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "registerPipelineWebhook", new String[]{"message"}, new String[]{"Registering pipeline webhook with token: " + exec.getToken()}, true);
        synchronized (pipelineWebhooks) { pipelineWebhooks.put(exec.getToken(), exec);}
    }

    public static void deregisterPipelineWebhook(DevOpsPipelineChangeStepExecution exec) {
        GenericUtils.printDebug(DevOpsRootAction.class.getName(), "deregisterPipelineWebhook", new String[]{"message"}, new String[]{"Deregistering pipeline webhook with token: " + exec.getToken()}, true);
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
