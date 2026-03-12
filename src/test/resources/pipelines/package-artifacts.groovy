pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
               snDevOpsPackage artifactsPayload: '{"artifacts":[{"name": "sa-web.jar", "version": "1.9", "repositoryName": "services-1031"}, {"name": "sa-db.jar", "version": "1.3.2", "repositoryName": "services-1032"}], "branchName": "master"}', name: 'packageName'
            }
        }
    }
}
