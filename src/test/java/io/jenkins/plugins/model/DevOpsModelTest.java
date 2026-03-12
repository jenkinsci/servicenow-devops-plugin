package io.jenkins.plugins.model;

import hudson.model.Job;
import io.jenkins.plugins.utils.GenericUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DevOpsModelTest {

    private DevOpsModel devOpsModel = new DevOpsModel();
    @Mock
    File file;
    @Mock
    Job job;

    @Before
    public void setUp() {
        devOpsModel = new DevOpsModel();
        when(job.getRootDir()).thenReturn(file);
    }

    @Test
    public void testGetInfoFilePathForMultiBranchJob() {
        when(file.getAbsolutePath()).thenReturn("C:\\Jenkins\\workspace\\my-job\\branches\\feature-branch");
        try (MockedStatic<GenericUtils> mockedGenericUtils = mockStatic(GenericUtils.class)) {
            mockedGenericUtils.when(() -> GenericUtils.isMultiBranch(job)).thenReturn(true);
            mockedGenericUtils.when(() -> GenericUtils.isWindows()).thenReturn(true);
            
            String result = devOpsModel.getInfoFilePath(job);
            assertNotNull("Result should not be null", result);
            assertEquals("C:\\Jenkins\\workspace\\my-job\\snPipelineInfo.json", result);
        }
    }
    
    @Test
    public void testGetInfoFilePathForRegularJob() {
        when(file.getAbsolutePath()).thenReturn("C:\\Jenkins\\workspace\\my-job");
        
        try (MockedStatic<GenericUtils> mockedGenericUtils = mockStatic(GenericUtils.class)) {
            mockedGenericUtils.when(() -> GenericUtils.isMultiBranch(job)).thenReturn(false);
            mockedGenericUtils.when(() -> GenericUtils.isWindows()).thenReturn(true);
            
            String result = devOpsModel.getInfoFilePath(job);
            assertNotNull("Result should not be null", result);
            assertEquals("C:\\Jenkins\\workspace\\my-job\\snPipelineInfo.json", result);
        }
    }

}
