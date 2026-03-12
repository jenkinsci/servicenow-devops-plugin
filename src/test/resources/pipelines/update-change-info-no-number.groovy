pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsUpdateChangeInfo(
                    changeRequestDetails: '{"short_description": "Updated description"}'
                )
            }
        }
    }
}
