pipeline {
    agent any

    environment {
        DOCKER_HOST = 'unix:///var/run/docker.sock'
        TESTCONTAINERS_DOCKER_CLIENT_STRATEGY = 'org.testcontainers.dockerclient.UnixSocketClientProviderStrategy'
        TESTCONTAINERS_RYUK_DISABLED = 'true'
    }

    stages {

        stage('Build Backend') {
            steps {
                dir('hr-workflow-backend') {
                    sh './mvnw clean compile -B'
                }
            }
        }

        stage('Run Tests') {
            steps {
                dir('hr-workflow-backend') {
                    sh './mvnw verify -Dmaven.test.failure.ignore=true -B'
                }
            }
            post {
                always {
                    sh 'docker ps -a | grep "postgres:15" | grep -v hr-workflow | awk \'{print $1}\' | xargs -r docker stop || true'
                    sh 'docker ps -a | grep "postgres:15" | grep -v hr-workflow | awk \'{print $1}\' | xargs -r docker rm || true'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    dir('hr-workflow-backend') {
                        withSonarQubeEnv('SonarQube') {
                            sh './mvnw sonar:sonar -Dsonar.projectKey=HR-Workflow -B'
                        }
                    }
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker-compose build'
            }
        }

        stage('Deploy') {
            steps {
                sh 'docker-compose down || true'
                sh 'docker-compose up -d'
            }
        }
    }

    post {
        success { echo 'Pipeline succeeded. Running at http://localhost:3000' }
        unstable { echo 'Pipeline completed with warnings (e.g. SonarQube). Check stage results.' }
        failure { echo 'Pipeline failed.' }
    }
}