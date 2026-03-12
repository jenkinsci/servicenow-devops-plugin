pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsUpdateChangeInfo(
                    configurationName: 'NonExistentConfig',
                    changeRequestNumber: 'CHG0001234',
                    changeRequestDetails: '{"short_description": "Updated description"}'
                )
            }
        }
    }
}
