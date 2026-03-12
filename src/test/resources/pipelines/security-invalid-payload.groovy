pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsSecurityResult(securityResultAttributes: 'this-is-not-valid-json')
            }
        }
    }
}
