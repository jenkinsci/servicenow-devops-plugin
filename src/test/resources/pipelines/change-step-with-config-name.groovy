pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                snDevOpsChange(
                    configurationName: 'DevOpsConfig2',
                    ignoreErrors: true,
                    changeRequestDetails: '''{
                        "attributes": {
                            "short_description": "Test change with non-default config",
                            "priority": "1"
                        }
                    }'''
                )
            }
        }
    }
}
