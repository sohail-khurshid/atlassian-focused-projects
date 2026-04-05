## Overview
   A collection of Groovy-based scripts designed to programmatically migrate Jira issues from Jira Datacenter to Jira Cloud

## Features
*  Automated Retrieval: Fetch and list issues from a specific project using JQL (Jira Query Language).
*  Issue Creation: Programmatically create new tasks with custom summaries and descriptions.
*  Secure Configuration: Externalized configuration using YAML to keep sensitive API tokens out of the source code.
*  Native Groovy Implementation: Uses JsonSlurper and YamlSlurper for lightweight execution without heavy external dependencies.

## Prerequisites
* Groovy 3.0+ installed on your machine.
* An Atlassian API Token (Generate one at id.atlassian.com).
* A Jira Cloud instance.

## Setup
   1. Clone the repository:
      ```
      git clone https://github.com/yourusername/jira-groovy-automation.git
      cd jira-groovy-automation 
      ```
   3. Configure Environment:\
      Rename settings.template.yaml to settings.yaml and fill in your details:
      ```
      jira:
          base_url: "https://your-domain.atlassian.net"
          email: "your-email@example.com"
          api_token: "your_token_here"
          project_key: "PROJ"
      ```
## Usage
* To run the scripts, ensure you are in the root directory:
   * To retrieve issues:
     ```
        groovy src/RetrieveIssues.groovy
     ```
** To upload a new issue:
```
   groovy src/UploadIssues.groovy
```
## DevOps Utility & Enterprise Integration
While these scripts function as standalone tools, they are designed with the flexibility to be integrated into professional DevOps and ITSM workflows:
* Jenkins Pipelines: The Groovy logic used here is natively compatible with Jenkinsfiles. These scripts can be easily adapted into Jenkins Shared Libraries to automate ticket creation or status updates during CI/CD build failures or deployments.
* Atlassian ScriptRunner: The core logic for issue manipulation and REST interaction serves as a foundation for ScriptRunner for Jira, allowing for advanced workflow listeners, custom REST endpoints, and scripted fields.
* Operational Efficiency: By externalizing configuration into YAML, these tools follow Infrastructure as Code (IaC) principles, making them easy to manage via configuration management tools or secret-management services like HashiCorp Vault or AWS Secrets Manager.
* Cross-Platform Portability: As JVM-based scripts, they offer high performance and seamless integration with existing Java-based enterprise toolchains without the need for language-specific interpreters beyond the Groovy runtime.
