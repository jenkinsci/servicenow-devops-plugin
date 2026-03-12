pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsUpdateChangeInfo(
                    changeRequestNumber: 'CHG0001234',
                    changeRequestDetails: 'this-is-not-valid-json'
                )
            }
        }
    }
}
