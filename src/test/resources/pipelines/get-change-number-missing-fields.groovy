pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsGetChangeNumber(
                    changeDetails: '''{ "pipeline_name": "Test_Pipeline"}'''
                )
            }
        }
    }
}
