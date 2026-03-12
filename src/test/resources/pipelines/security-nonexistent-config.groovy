pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsSecurityResult(securityResultAttributes: '{"scanner": "Veracode", "applicationName": "testApp"}', configurationName: 'NonExistentConfig')
            }
        }
    }
}
