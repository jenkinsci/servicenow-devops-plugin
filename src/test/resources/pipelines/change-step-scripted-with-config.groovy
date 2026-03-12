node {
    stage('Test') {
        snDevOpsChange(
            configurationName: 'DevOpsConfig2',
            ignoreErrors: true,
            changeRequestDetails: '''{
                "attributes": {
                    "short_description": "Scripted pipeline change with non-default config",
                    "priority": "1"
                }
            }'''
        )
    }
}
