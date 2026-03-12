pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsArtifact(artifactsPayload: '{"artifacts":[{"name": "test-artifact.jar", "version": "1.0", "repositoryName": "repo1"}], "branchName": "master"}')
            }
        }
    }
}
