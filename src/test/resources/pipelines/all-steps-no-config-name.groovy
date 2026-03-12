pipeline {
    agent any
    stages {
        stage('Artifact') {
            steps {
                snDevOpsArtifact(artifactsPayload: '{"artifacts":[{"name": "test-artifact.jar", "version": "1.0", "repositoryName": "repo1"}], "branchName": "master"}')
            }
        }
        stage('Package') {
            steps {
                snDevOpsPackage(name: 'testPackage', artifactsPayload: '{"artifacts":[{"name": "test-artifact.jar", "version": "1.0", "repositoryName": "repo1"}], "branchName": "master"}')
            }
        }
        stage('Change') {
            steps {
                snDevOpsChange(
                    ignoreErrors: true,
                    changeRequestDetails: '''{
                        "attributes": {
                            "short_description": "Test change default config",
                            "priority": "1"
                        }
                    }'''
                )
            }
        }
    }
}
