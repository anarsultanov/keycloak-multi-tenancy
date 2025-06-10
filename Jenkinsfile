pipeline {
    agent any

    tools {
        maven 'mvn'
    }

    environment {
        AWS_REGION = 'ap-south-1'
        CODEARTIFACT_DOMAIN = 'azguards-technolabs'
        CODEARTIFACT_REPO = 'azguards-technolabs'
        AWS_ACCOUNT_ID = '694141026695'
        AWS_ACCESS_KEY_ID = credentials('aws-access-key-id')
        AWS_SECRET_ACCESS_KEY = credentials('aws-secret-access-key')
    }

    stages {
        stage('Checkout Code') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/Azguards-Technolabs/keycloak-multi-tenancy',
                    credentialsId: 'github-token'
            }
        }

        stage('Build JAR') {
            steps {
                sh 'mvn clean install -DskipTests'
            }
        }

        stage('Get CodeArtifact Auth Token') {
            steps {
                script {
                    env.CODEARTIFACT_AUTH_TOKEN = sh(
                        script: """
                            aws codeartifact get-authorization-token \
                                --domain ${CODEARTIFACT_DOMAIN} \
                                --domain-owner ${AWS_ACCOUNT_ID} \
                                --region ${AWS_REGION} \
                                --query authorizationToken \
                                --output text
                        """,
                        returnStdout: true
                    ).trim()
                }
            }
        }

        stage('Deploy to CodeArtifact') {
            steps {
                script {
                    writeFile file: 'settings.xml', text: """
                        <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
                            <servers>
                                <server>
                                    <id>codeartifact</id>
                                    <username>aws</username>
                                    <password>${env.CODEARTIFACT_AUTH_TOKEN}</password>
                                </server>
                            </servers>
                        </settings>
                    """

                    sh """
                        mvn deploy -DskipTests \
                        -DaltDeploymentRepository=codeartifact::default::https://${CODEARTIFACT_DOMAIN}-${AWS_ACCOUNT_ID}.d.codeartifact.${AWS_REGION}.amazonaws.com/maven/${CODEARTIFACT_REPO}/ \
                        --settings settings.xml
                    """
                }
            }
        }
    }

    post {
        success {
            echo 'JAR uploaded to AWS CodeArtifact successfully.'
        }
        failure {
            echo 'Upload failed. Check logs.'
        }
        always {
            sh 'rm -f settings.xml'
        }
    }
}
