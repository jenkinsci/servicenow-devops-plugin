pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsUpdateChangeInfo(
                    changeRequestNumber: 'CHG0001234',
                    changeRequestDetails: '{"short_description": "Updated description"}'
                )
            }
        }
    }
}
