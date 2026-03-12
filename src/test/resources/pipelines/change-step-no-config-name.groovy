pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsChange(
                    ignoreErrors: true,
                    changeRequestDetails: '''{
                        "attributes": {
                            "short_description": "Test change without config name",
                            "priority": "1"
                        }
                    }'''
                )
            }
        }
    }
}
