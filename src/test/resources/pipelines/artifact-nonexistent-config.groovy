pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsArtifact(artifactsPayload: '{"artifacts":[{"name": "test.jar", "version": "1.0", "repositoryName": "repo1"}], "branchName": "master"}', configurationName: 'NonExistentConfig')
            }
        }
    }
}
