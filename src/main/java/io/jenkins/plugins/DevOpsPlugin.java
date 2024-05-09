package io.jenkins.plugins;

import hudson.Extension;
import hudson.Plugin;

@Extension
public class DevOpsPlugin extends Plugin {

    //this method is called after the start() method where the initial initialization of plugin is done
    //here we are removing cache files from previous jenkins installations
    @Override
    public void postInitialize() throws Exception {
        super.postInitialize();
        DevOpsRootAction.deletePipelineInfoFiles();
    }
}
