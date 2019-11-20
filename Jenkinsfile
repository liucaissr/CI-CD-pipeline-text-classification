pipeline {
    agent any

    environment {
        CI = "jenkins"
        JAVA_OPTS = "-Xmx2g"
        // SBT configuration
        SBT_OPTS = "-Xms2g -Xmx6g"
        // java home not correct with jdk setting
        JAVA_HOME = "${tool 'jdk1.8.0_172'}/jdk1.8.0_172"
        // requires an sbt installation in the path and java
        PATH = "${tool name: 'sbt-1.2.7', type: 'org.jvnet.hudson.plugins.SbtPluginBuilder$SbtInstallation'}/bin/:${env.JAVA_HOME}/bin:${PATH}"
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
                        withEnv(["BUILD_NUMBER=${readFile encoding: 'utf-8', file: '.pipeline.build_number'}", "JAVA_HOME=${tool 'jdk1.8.0_172'}/jdk1.8.0_172"]) {
                            echo "Create dataset for build number ${BUILD_NUMBER} (with JAVA_HOME ${JAVA_HOME})"
                            sh '''
                                wget -nv -O target/spark-cdh5_2.4.3-production.tgz http://tooldhcp01.endor.gutefrage.net/binaries/spark/spark-cdh5_2.4.3-production.tgz
                                tar -zxf target/spark-cdh5_2.4.3-production.tgz -C target
                                java -version
                                target/spark-2.4.3-bin-hadoop2.6/bin/spark-submit --master yarn --deploy-mode cluster --driver-memory 4g --conf spark.ui.port=4052 --driver-class-path /etc/hadoop/conf.cloudera.hdfs --class jobs.Dwh2Positive /var/lib/jenkins/workspace/Data/qc-contactrequest/spark-dataset/target/scala-2.11/spark-dataset-assembly-1.${BUILD_NUMBER}.jar
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