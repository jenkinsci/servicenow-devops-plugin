package io.jenkins.plugins;

import net.sf.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyPrivate;

@RunWith(PowerMockRunner.class)
@PrepareForTest(DevOpsRootAction.class)
public class DevOpsRootActionTest {

    private DevOpsRootAction rootAction =  new DevOpsRootAction();

    @SuppressWarnings("unchecked")
    private HashMap<String, String> getStaticMap(String fieldName) throws Exception {
        Field field = DevOpsRootAction.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (HashMap<String, String>) field.get(null);
    }

    private void setupWebhook(String token, String jobId) throws Exception {
        HashMap<String, String> webhooks = getStaticMap("webhooks");
        HashMap<String, String> jobs = getStaticMap("jobs");
        webhooks.put(token, jobId);
        jobs.put(jobId, token);
    }


    @Test
    public void testAssertCallbackContentIsStored() throws Exception {
        StaplerRequest mockRequest = mock(StaplerRequest.class);
        StaplerResponse mockResponse = mock(StaplerResponse.class);

        String token = "pipeline";
        String jobId = "job_12345";
        setupWebhook(token, jobId);
        
        DevOpsRootAction spyRootAction = PowerMockito.spy(rootAction);
        JSONObject callbackData = new JSONObject();
        callbackData.put("result", "approved");
        callbackData.put("changeComments", "Change request created with errors in DevOps data retrieval. Processing errors: {\"pipelineExecution\":\"PE0051879\",\"pipelineName\":\"MAL/Non-prod/CD_Jobs/Allocation-Web-Service-ADM-Europe/Allocation-Web-Service-ADM-Europe-CE\",\"processingErrors\":{\"failed_commit_event\":[{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"},{\"IBE0471526\":\"Commit details cannot be retrieved. Unable to process the request. \",\"SYS_ID\":\"d8eadfeb47a012104de97eb9e16d43c9\"}]}} Approver: DevOps System, Date: 2024-09-16 06:12:59");
        String expectedContent = callbackData.toString();

        // Mock the request to provide the content
        when(mockRequest.getOriginalRestOfPath()).thenReturn("/" + token);
        BufferedReader reader = new BufferedReader(new StringReader(expectedContent));
        when(mockRequest.getReader()).thenReturn(reader);

        spyRootAction.doDynamic(mockRequest, mockResponse);
        
        // Capture the actual StringBuffer content argument passed to _handlePipelineCallback
        ArgumentCaptor<StringBuffer> contentCaptor = ArgumentCaptor.forClass(StringBuffer.class);
        verifyPrivate(spyRootAction, times(1))
            .invoke("_handlePipelineCallback", eq(token), contentCaptor.capture());

        // Get the actual content argument passed to _handlePipelineCallback
        StringBuffer actualContentArg = contentCaptor.getValue();
        assertNotNull("Content argument to _handlePipelineCallback must not be null", actualContentArg);
        
        String actualContentString = actualContentArg.toString().trim();
        assertNotNull("Content string must not be null", actualContentString);
        assertTrue("Content must not be empty", actualContentString.length() > 0);
        
        // Verify the content argument matches what we sent
        assertEquals("Content argument must match sent content", expectedContent.trim(), actualContentString);
        assertTrue("Content must contain DevOps System", actualContentString.contains("DevOps System"));
    }
}
