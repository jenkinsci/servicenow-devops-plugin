pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsArtifact(artifactsPayload: 'this-is-not-valid-json')
            }
        }
    }
}
