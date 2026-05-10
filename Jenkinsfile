pipeline {
    agent any

    environment {
        SONAR_TOKEN = credentials('SonarToken_Local')
        DOCKER_HOST = 'unix:///var/run/docker.sock'
        TESTCONTAINERS_DOCKER_CLIENT_STRATEGY = 'org.testcontainers.dockerclient.UnixSocketClientProviderStrategy'
        TESTCONTAINERS_RYUK_DISABLED = 'true'
    }

    stages {

        stage('Checkout') {
            steps {
                echo 'Checking out source code...'
                checkout scm
            }
        }

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
        }

        stage('SonarQube Analysis') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    dir('hr-workflow-backend') {
                        withSonarQubeEnv('SonarQube') {
                            sh './mvnw sonar:sonar -Dsonar.projectKey=HR-Workflow -Dsonar.token=${SONAR_TOKEN} -B'
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
