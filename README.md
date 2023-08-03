# ServiceNow DevOps Plugin for Jenkins


## Contents

- [Intro](#intro)
- [Requirements](#requirements)
- [Support Model](#support-model)
- [Governance Model](#governance-model)

---

## Intro

The ServiceNow DevOps plugin extends the default behaviors of Jenkins and provides a mechanism to control Job executions via the ServiceNow Change Management application on the [Now Platform from ServiceNow](https://www.servicenow.com/now-platform.html).

### Getting Started

The following instructions demonstrate how to download and install the ServiceNow DevOps plugin in your Jenkins instance, and verify that Jenkins is successfully able to communicate with your ServiceNow instance.


### Prerequisities

1. Jenkins 2.289.1+ instance
2. ServiceNow DevOps 1.36.0 application installed in your ServiceNow instance

### Installation/Upgrade

Upgrade Note: It is best to uninstall the plugin first, then install the new plugin, then restart Jenkins and go here to cleanup and old data <your jenkins url>/administrativeMonitor/OldData/manage.

Download the plugin zip file attached to this article by clicking "I Accept" at the bottom of the page. Extract the .hpi file and install it in Jenkins by navigating to Manage Jenkins -> Advanced -> Upload Plugin.

The following plugin dependencies are required. Depending on your Jenkins configuration, they may automatically be installed/updated as needed to meet the minimum requirements of the Jenkins plugin for ServiceNow DevOps.  If not, you must manually ensure that the correct plugin versions are present:
    
- org.jenkins-ci.plugins.credentials 2.3.11
- org.jenkins-ci.plugins.jackson2-api 2.11.1
- org.jenkins-ci.plugins.junit 1.37
- org.jenkins-ci.plugins.structs 1.21
- org.jenkins-ci.plugins.workflow.workflow-api 2.41
- org.jenkins-ci.plugins.workflow.workflow-cps 2.88
- org.jenkins-ci.plugins.workflow.workflow-job 2.40
- org.jenkins-ci.plugins.workflow.workflow-step-api 2.23
- com.evanlennickom.retry4j 0.15.0
- com.google.code.gson.gson 2.8.5
- org.apache.commons.commons-lang 3 3.7


### Configuration
Each Jenkins job that should notify ServiceNow must set the ServiceNow DevOps options.

### Declarative & Scripted Pipeline Support

To support declarative and scripted pipelines in the 1.5 version of the plugin, the following has been introduced.

**snDevOpsChange** - Enables change control for any root-level stage that is mapped to a DevOps step.

For complete details please see: Using a declarative or scripted pipeline in the ServiceNow DevOps documentation.


## Requirements

### System Requirements

1. Jenkins 2.289.1+ instance
2. ServiceNow DevOps application installed in your ServiceNow instance


### Release Notes

#### Secure token authentication for Integration user 
Jenkins now supports token-based authentication for the integration user. It also supports basic auth and token authentication to make it compatible with DevOps Config. 


**NOTE**: The ServiceNow DevOps plugin now requires 2.289.1+ as a minimum version


## Support Model

ServiceNow customers may request support through the [Now Support (HI) portal](https://support.servicenow.com/nav_to.do?uri=%2Fnow_support_home.do).


## Governance Model

Initially, ServiceNow product management and engineering representatives will own governance of these integrations to ensure consistency with roadmap direction. In the longer term, we hope that contributors from customers and our community developers will help to guide prioritization and maintenance of these integrations. At that point, this governance model can be updated to reflect a broader pool of contributors and maintainers. 

## Vulnerability Reporting
Please notify psirt-oss@servicenow.com regarding any vulnerability reports in addition to following current reporting procedure.
