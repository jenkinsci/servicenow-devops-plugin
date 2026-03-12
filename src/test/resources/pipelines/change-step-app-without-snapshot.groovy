pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsChange(
                    applicationName: 'TestApp',
                    changeRequestDetails: '''{
                        "attributes": {
                            "short_description": "Test change with app but no snapshot"
                        }
                    }'''
                )
            }
        }
    }
}
