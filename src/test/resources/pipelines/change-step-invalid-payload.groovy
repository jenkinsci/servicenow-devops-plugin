pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsChange(
                    changeRequestDetails: '''{
                        "attributes": {
                            "state": "Test description"
                            "priority": "1"
                    }'''
                )
            }
        }
    }
}
