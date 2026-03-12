pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsChange(
                    changeRequestDetails: '''{
                        "attributes": {
                            "state": "Approved",
                            "watch_list": "1"
                        }
                    }'''
                )
            }
        }
    }
}
