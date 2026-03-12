pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsPackage(name: 'testPackage', artifactsPayload: '{"artifacts":[{"name": "test-artifact.jar", "version": "1.0", "repositoryName": "repo1"}], "branchName": "master"}')
            }
        }
    }
}
