pipeline {
    agent any

    environment {
        CI = "jenkins"
        JAVA_HOME = "${tool 'jdk1.8.0_172'}"
        // SBT configuration
        SBT_OPTS = "-Xms1g -Xmx2g"
        // requires an sbt installation named sbt-1.3.3
        PATH = "${JAVA_HOME}/bin:${tool name: 'sbt-1.3.3', type: 'org.jvnet.hudson.plugins.SbtPluginBuilder$SbtInstallation'}/bin/:${PATH}"
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
        stage('Deploy') {
            parallel {
                stage('Create dataset') {
                    steps {
                        // unstash the .build_number and .git_commit file
                        unstash 'pipeline'
                        // override the current BUILD_NUMBER with the contents of the .build_number file
                        withEnv(["BUILD_NUMBER=${readFile encoding: 'utf-8', file: '.pipeline.build_number'}"]) {
                            echo "Create dataset for build number ${BUILD_NUMBER}"
                            //sh "sbt deployProductionAngmar"
                        }
                    }
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