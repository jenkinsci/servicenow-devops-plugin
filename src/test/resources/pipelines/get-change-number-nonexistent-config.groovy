pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsGetChangeNumber(
                    configurationName: 'NonExistentConfig',
                    changeDetails: '''{ "pipeline_name": "Test_Pipeline", "build_number": "1", "stage_name": "ChangeStage"}'''
                )
            }
        }
    }
}
