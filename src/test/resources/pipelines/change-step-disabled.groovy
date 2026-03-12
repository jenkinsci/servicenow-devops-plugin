pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsChange(
                    enabled: false,
                    changeRequestDetails: '''{
                        "attributes": {
                            "short_description": "Test change disabled"
                        }
                    }'''
                )
            }
        }
    }
}
