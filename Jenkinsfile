pipeline {
    agent any

    environment {
        SONAR_TOKEN = credentials('sonar-token')
        DOCKER_API_VERSION = '1.41'
    }

    stages {

        stage('Checkout') {
            steps {
                echo '📥 Checking out source code...'
                checkout scm
            }
        }

        stage('Build Backend') {
            steps {
                dir('hr-workflow-backend') {
                    sh 'mvn clean compile -B'
                }
            }
        }

        stage('Run Tests') {
            steps {
                dir('hr-workflow-backend') {
                    sh 'mvn verify -Dmaven.test.failure.ignore=true -B'
                }
            }
            post {
                always {
                    junit 'hr-workflow-backend/target/surefire-reports/*.xml'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                dir('hr-workflow-backend') {
                    withSonarQubeEnv('SonarQube') {
                        sh """
                            mvn sonar:sonar \
                              -Dsonar.projectKey=HR-Workflow \
                              -Dsonar.token=${SONAR_TOKEN} \
                              -B
                        """
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
                sh 'docker-compose up -d'
            }
        }
    }

    post {
        success { echo '✅ Running at http://localhost:3000' }
        failure { echo '❌ Pipeline failed.' }
    }
}