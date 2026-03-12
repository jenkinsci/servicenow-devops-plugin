node {
    stage('Test') {
        snDevOpsChange(
            ignoreErrors: true,
            changeRequestDetails: '''{
                "attributes": {
                    "short_description": "Scripted pipeline change without config name",
                    "priority": "1"
                }
            }'''
        )
    }
}
