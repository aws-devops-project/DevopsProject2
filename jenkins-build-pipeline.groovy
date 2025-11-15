pipeline {
    agent any
    parameters {
        string(name: 'ECR_REPO_NAME', defaultValue: 'amazon-prime', description: 'Enter repository name')
        string()
    }

    environment {
        SCANNER_HOME = tool 'SonarQube Scanner' //configure in jenkns tool - install version
    }
    stages {
        stage ('1. git checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/aws-devops-project/DevopsProject2.git'
            }
        }
        stage ('2. Sonar Analysis') {
            steps {
                withSonarQubeEnv ('sonar-server') {  // conf jenkins system - tocken
                    $SCANNER_HOME/bin/sonar-scanner \
                    -Dsonar.projectName=amazon-prime \
                    -Dsonar.projectKey=amazon-prime
                                   
                }
            }
        }
        stage ('3. SonarQube Quality Gate') {
            steps {
                waitForQualityGate abortPipeline: false, credentialsId: 'sonar-tocken'
    
            }
        }
        stage ('4. NPM install') {
            steps {
                sh 'npm install'
            }
        }
        stage ('5. trivy scan') {
            steps {
                sh 'trivy fs . > trivy-scan-results.txt'
            }
        }
        stage ('6. docker image build') {
            steps {
                sh 'docker build -t . ${params.ECR_REPO_NAME} .'
            }
        }
    }
}