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
                    sh './mvnw verify -B'
                }
            }
            post {
                always {
                    sh """
                        docker ps -a | grep "postgres:15" | grep -v "hr-workflow" | grep -v "hr-infra" | awk '{print \$1}' | xargs -r docker rm -f || true
                    """
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
                // Secrets live in a Jenkins "Secret file" credential (id: hr-workflow-env) holding the .env contents
                withCredentials([file(credentialsId: 'hr-workflow-env', variable: 'ENV_FILE')]) {
                    sh 'cp "$ENV_FILE" .env'
                    sh 'docker-compose build'
                }
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([file(credentialsId: 'hr-workflow-env', variable: 'ENV_FILE')]) {
                    sh 'cp "$ENV_FILE" .env'
                    sh 'docker-compose -p hr-app down || true'
                    sh """
                        docker ps -a | grep "hr-workflow-" | awk '{print \$1}' | xargs -r docker rm -f || true
                    """
                    sh 'docker-compose -p hr-app up -d'
                }
            }
        }
    }

    post {
        always { sh 'rm -f .env' }
        success { echo 'Pipeline succeeded. Running at http://localhost:3000' }
        unstable { echo 'Pipeline completed with warnings (e.g. SonarQube). Check stage results.' }
        failure { echo 'Pipeline failed.' }
    }
}