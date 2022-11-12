pipeline {
    agent any

    environment {
        SBT_NATIVE_CLIENT = 'true'
    }

    stages {
        stage('Build') {
            steps {
                sh 'sbt compile'
            }
        }
        stage('Test') {
            steps {
                sh 'sbt test'
            }
            post {
                always {
                    junit 'target/test-reports/TEST*.xml'
                }
            }
        }
        stage('Package') {
            steps {
                sh 'sbt assembly'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/scala*/stripped/*.jar', fingerprint: true
                }
            }
        }
    }
}
