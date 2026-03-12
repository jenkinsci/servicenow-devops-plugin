pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsChange(
                    configurationName: 'NonExistentConfig',
                    changeRequestDetails: '''{
                        "attributes": {
                            "short_description": "Test change with nonexistent config"
                        }
                    }'''
                )
            }
        }
    }
}
