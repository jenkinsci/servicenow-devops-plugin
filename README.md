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

1. Jenkins 2.204.6+ instance
2. ServiceNow DevOps 1.30 application installed in your ServiceNow instance

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

1. Jenkins 2.204.6+ instance
2. ServiceNow DevOps application installed in your ServiceNow instance



### Release Notes
Feature Enhancements:

- Support for parallel stages: ServiceNow DevOps will now track stages that run in parallel/nested in Jenkins pipelines. The parallel stages will be rendered accurately in the Pipeline User Interface, and automated Change Requests will only get created once parallel stages preceding it are complete.

Defects Addressed:

- Multibranch pipelines with BitBucket configuration has task execution value as empty.
- Pipeline executions are created even though Track is NOT enabled for multibranch pipeline and nested pipeline.
- After recreating Jenkins tool, inbound events are created even though pipelines are not discovered or tracked.


**NOTE**: Starting with DevOps 1.30, the minimum base version of Jenkins server must be **2.204.6** to fix the vulnerabilities

## Support Model

ServiceNow built this integration with the intent to help customers get started faster in adopting DevOps APIs for DevOps workflows, but __will not be providing formal support__. This integration is therefore considered "use at your own risk", and will rely on the open-source community to help drive fixes and feature enhancements via Issues. Occasionally, ServiceNow may choose to contribute to the open-source project to help address the highest priority Issues, and will do our best to keep the integrations updated with the latest API changes shipped with family releases. This is a good opportunity for our customers and community developers to step up and help drive iteration and improvement on these open-source integrations for everyone's benefit. 

## Governance Model

Initially, ServiceNow product management and engineering representatives will own governance of these integrations to ensure consistency with roadmap direction. In the longer term, we hope that contributors from customers and our community developers will help to guide prioritization and maintenance of these integrations. At that point, this governance model can be updated to reflect a broader pool of contributors and maintainers. 
