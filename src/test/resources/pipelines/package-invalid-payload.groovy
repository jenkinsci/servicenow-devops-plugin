pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsPackage(name: 'testPackage', artifactsPayload: 'this-is-not-valid-json')
            }
        }
    }
}
