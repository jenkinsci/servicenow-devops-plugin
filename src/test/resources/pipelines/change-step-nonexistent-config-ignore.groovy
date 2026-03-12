pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsChange(
                    configurationName: 'NonExistentConfig',
                    ignoreErrors: true,
                    changeRequestDetails: '''{
                        "attributes": {
                            "short_description": "Test change with nonexistent config ignore"
                        }
                    }'''
                )
            }
        }
    }
}
