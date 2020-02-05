pipeline {
    agent any

    environment {
        CI = "jenkins"
        JAVA_OPTS = "-Xmx2g"
        // SBT configuration
        SBT_OPTS = "-Xms2g -Xmx6g"
        // requires an sbt installation in the path and java
        PATH = "${tool name: 'sbt-1.2.7', type: 'org.jvnet.hudson.plugins.SbtPluginBuilder$SbtInstallation'}/bin/:${PATH}"
    }

    tools {
        jdk 'jdk1.8.0_172'
    }

    options {
        ansiColor('xterm')
        buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '28', numToKeepStr: '30')
        // we need to preserve the stashes as they contain the build_number, which we need to restart arbitrary builds
        preserveStashes buildCount: 50
    }

    stages {
        stage('Build & Publish') {
            steps {
               sh "sbt jenkinsTask"
                // write the BUILD_NUMBER into a file and stash it
                writeFile encoding: 'utf-8', file: '.pipeline.build_number', text: "$BUILD_NUMBER"
                stash includes: '.pipeline.*', name: 'pipeline'
            }
        }
        stage('Create Dataset') {
            steps {
                // unstash the .build_number and .git_commit file
                unstash 'pipeline'
                // override the current BUILD_NUMBER with the contents of the .build_number file
                // override JAVA_HOME with correct path
                withEnv(["BUILD_NUMBER=${readFile encoding: 'utf-8', file: '.pipeline.build_number'}", "JAVA_HOME=${tool 'jdk1.8.0_172'}/jdk1.8.0_172"]) {
                    echo "Create dataset for build number ${BUILD_NUMBER} (with JAVA_HOME ${JAVA_HOME})"
                    sh "sbt sparkDataset/sparkSubmit"
                }
            }
        }
        stage('Train') {
            steps {
                // unstash the .build_number and .git_commit file
                unstash 'pipeline'
                // override the current BUILD_NUMBER with the contents of the .build_number file
                // override JAVA_HOME with correct path
                withEnv(["BUILD_NUMBER=${readFile encoding: 'utf-8', file: '.pipeline.build_number'}", "JAVA_HOME=${tool 'jdk1.8.0_172'}/jdk1.8.0_172"]) {
                    echo "Create dataset for build number ${BUILD_NUMBER} (with JAVA_HOME ${JAVA_HOME})"
                    sh '''
                    sbt downloadData
                    sbt depDocker
                    '''
                }
            }
        }
    }
    post {
        success {
            slackSend color: 'good',
                      message: "deployed ${JOB_BASE_NAME} ${BUILD_NUMBER} (<${BUILD_URL}|jenkins build>)"
        }
        unstable {
            slackSend color: 'warning',
                      message: "build is unstable ${JOB_BASE_NAME} ${BUILD_NUMBER} (<${BUILD_URL}|jenkins build>)"
        }
        failure {
            slackSend color: 'danger',
                      message: "build failed ${JOB_BASE_NAME} ${BUILD_NUMBER} (<${BUILD_URL}|jenkins build>)"
        }
    }
}