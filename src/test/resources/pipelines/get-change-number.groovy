pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsGetChangeNumber(
                        changeDetails: '''{ "pipeline_name": "Test_Pipeline", "build_number": "1", "stage_name": "ChangeStage", "branch_name": "master"}'''
                )
            }
        }
    }
}
