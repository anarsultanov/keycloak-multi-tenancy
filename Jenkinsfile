pipeline {
    agent any
    tools {
        maven 'mvn' // Ensure 'mvn' is configured in Jenkins Global Tool Configuration
    }
    environment {
        JAR_DESTINATION = "/path/to/destination/folder" // Replace with the path where you want to copy the JAR
        SERVER_USER = "ubuntu" // Replace with your server's username if copying to a remote server
        SERVER_IP = "65.1.70.239" // Replace with your server's IP address if copying to a remote server
    }
    stages {
        stage('Code Checkout') {
            steps {
                git branch: 'main', changelog: false, poll: false, url: 'https://github.com/Azguards-Technolabs/keycloak-multi-tenancy', credentialsId: 'github-token'
            }
        }

        stage('Maven Clean Install') {
            steps {
                sh "mvn clean install -DskipTests"
            }
        }

        stage('Copy JAR File') {
            steps {
                script {
                    // Find the generated JAR file in the target directory
                    def jarFile = findFiles(glob: 'target/*.jar')
                    if (jarFile.length > 0) {
                        def jarPath = jarFile[0].path
                        echo "Found JAR file: ${jarPath}"
                        // Copy the JAR to the specified destination (local or remote)
                        sh """
                            mkdir -p ${JAR_DESTINATION}
                            cp ${jarPath} ${JAR_DESTINATION}/
                        """
                        echo "JAR file copied to ${JAR_DESTINATION}"
                    } else {
                        error "No JAR file found in target directory!"
                    }
                }
            }
        }

        stage('Copy JAR to Remote Server (Optional)') {
            when {
                expression { env.SERVER_IP && env.SERVER_USER }
            }
            steps {
                script {
                    def jarFile = findFiles(glob: 'target/*.jar')
                    if (jarFile.length > 0) {
                        def jarPath = jarFile[0].path
                        def jarName = jarFile[0].name
                        sshagent(['server-ssh-cred']) { // Use the ID of the SSH credential in Jenkins
                            sh """
                                scp -o StrictHostKeyChecking=no ${jarPath} ${SERVER_USER}@${SERVER_IP}:${JAR_DESTINATION}/${jarName}
                            """
                            echo "JAR file ${jarName} copied to ${SERVER_USER}@${SERVER_IP}:${JAR_DESTINATION}"
                        }
                    } else {
                        error "No JAR file found to copy to remote server!"
                    }
                }
            }
        }
    }
    post {
        success {
            echo 'Build and JAR Copy Successful'
        }
        failure {
            echo 'Build or JAR Copy Failed'
        }
    }
}