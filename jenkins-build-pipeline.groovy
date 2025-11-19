pipeline {
    agent any
    parameters {
        string(name: 'ECR_REPO_NAME', defaultValue: 'amazon-prime', description: 'Enter repository name')
        string(name: 'AWS_ACCOUNT_ID', defaultValue: '590183931718', description: 'Enter AWS Account ID') // Added missing quote

    }
    tools {
        jdk 'JDK'
        nodejs 'NodeJS'
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
                sh 'docker build -t ${params.ECR_REPO_NAME} .'
            }
        }
        stage ('7. Create ECR Repo') {
            steps {
                 // Use AWS credentials stored in Jenkins (change credentialsId to your AWS creds)
                withCredentials([string(credentialsId: 'access-key', variable: 'AWS_ACCESS_KEY'), string(credentialsId: 'secret-key', variable: 'AWS_SECRET_KEY')]) {
                 sh """
                    aws configure set aws_access_key_id $AWS_ACCESS_KEY 
                    aws configure set aws_secret_access_key $AWS_SECRET_KEY
                    aws ecr describe-repositories --repository-name ${params.ECR_REPO_NAME} --region eu-west-2 || \
                    aws ecr create-repository --repository-name ${params.ECR_REPO_NAME} --region eu-west-2
                    """
                }
            }
        stage ('8. login to ECR and Tag img') {
            steps {
                withCredentials([string(credentialsId: 'access-key', variable: 'AWS_ACCESS_KEY'), string(credentialsId: 'secret-key', variable: 'AWS_SECRET_KEY')]) {
                 sh """
                    aws ecr get-login-password --region eu-west-2 | docker login --username AWS --password-stdin ${params.AWS_ACCOUNT_ID}.dkr.ecr.eu-west-2.amazonaws.com
                    docker tag ${params.ECR_REPO_NAME} ${params.AWS_ACCOUNT_ID}.dkr.ecr.eu-west-2.amazonaws.com/${params.ECR_REPO_NAME}:${BUILD_NUMBER}
                    docker tag ${params.ECR_REPO_NAME} ${params.AWS_ACCOUNT_ID}.dkr.ecr.eu-west-2.amazonaws.com/${params.ECR_REPO_NAME}:latest
                    """
                    }
                }
            }
        stage ('9. push img') {
            steps {
                withCredentials([string(credentialsId: 'access-key', variable: 'AWS_ACCESS_KEY'), string(credentialsId: 'secret-key', variable: 'AWS_SECRET_KEY')]) {
                sh """
                    docker push ${params.AWS_ACCOUNT_ID}.dkr.ecr.eu-west-2.amazonaws.com/${params.ECR_REPO_NAME}:${BUILD_NUMBER}
                    docker push ${params.AWS_ACCOUNT_ID}.dkr.ecr.eu-west-2.amazonaws.com/${params.ECR_REPO_NAME}:latest
                   """
                    }
                }  
            }
           
        stage ('10. Cleanup images from jenkins server') {
            steps {
                 sh """
                    docker rmi ${params.AWS_ACCOUNT_ID}.dkr.ecr.eu-west-2.amazonaws.com/${params.ECR_REPO_NAME}:${BUILD_NUMBER}
                    docker rmi ${params.AWS_ACCOUNT_ID}.dkr.ecr.eu-west-2.amazonaws.com/${params.ECR_REPO_NAME}:latest
                   """
 
                }
            }
        } 
    } 
}