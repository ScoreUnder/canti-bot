pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                checkout scm
                sh 'sbt compile'
                stash includes: 'target/**', name: 'build'
            }
        }
        stage('Test') {
            steps {
                unstash 'build'
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
                unstash 'build'
                sh 'sbt assembly'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/scala*/*.jar', fingerprint: true
                }
            }
        }
    }
}
