pipeline {
    agent any

    environment {
        CI = "jenkins"
        JAVA_OPTS = "-Xmx2g"
        // SBT configuration
        SBT_OPTS = "-Xms2g -Xmx6g"
        // requires an sbt installation named sbt-1.2.7
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
               sh '''
               echo $JAVA_HOME
               echo $PATH
               chmod a+x $JAVA_HOME/bin/java
               sbt jenkinsTask
               '''
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
                            sh '''
                                wget http://tooldhcp01.endor.gutefrage.net/binaries/spark/spark-cdh5_2.4.3-production.tgz
                                tar -zxf spark-cdh5_2.4.3-production.tgz
                                mv spark-2* spark
                                ./spark/bin/spark-submit --master yarn --deploy-mode cluster --conf spark.ui.port=4052 --driver-class-path /etc/hadoop/conf.cloudera.hdfs --class jobs.Dwh2Positive /target/scala-2.11/latest.jar
                            '''
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