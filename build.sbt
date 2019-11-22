name := "qc-contactrequest"

import scala.util.Properties
import scala.sys.process._

// scalafmt
addCommandAlias("scalafmtFormatAll", "; scalafmtAll; scalafmtSbt")
addCommandAlias("scalafmtValidateAll", "; scalafmtCheckAll; scalafmtSbtCheck")

addCommandAlias("validate", "; scalafmtValidateAll; test")
addCommandAlias("jenkinsTask", "; clean; validate; publishHdfs")

lazy val commonSettings = Seq(
  organization := "net.gutefrage.data",
  scalaVersion := "2.11.8",
  version := Properties.envOrNone("BUILD_NUMBER").map(build => s"1.$build").getOrElse("1-SNAPSHOT"),
  resolvers += Resolver.sonatypeRepo("releases"),
  resolvers += ("Gutefrage Release Repo" at "http://artifacts.endor.gutefrage.net/content/groups/public")
    .withAllowInsecureProtocol(true),
  resolvers += ("twitter-repo" at "https://maven.twttr.com")
    .withAllowInsecureProtocol(true),
  libraryDependencies ++= Seq(
    "org.apache.spark"  %% "spark-core"       % Dependencies.sparkDepVer % "provided",
    "org.apache.spark"  %% "spark-sql"        % Dependencies.sparkDepVer % "provided",
    "org.apache.spark"  %% "spark-hive"       % Dependencies.sparkDepVer % "provided",
    "com.typesafe"      % "config"            % "1.2.1",
    "net.gutefrage"     %% "weird-string"     % "1.11",
    "net.gutefrage.etl" %% "spark-commons"    % "4.4",
    "net.gutefrage"     %% "clean-embeddings" % "1.14"
  ),
  publishHdfsBucket := "data",
  publishHdfsService := "qc-contactrequest",
  publishHdfsBuild := version.value
)
lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(
    sparkDataset,
    train
  )

lazy val sparkDataset = (project in file("spark-dataset"))
  .enablePlugins(PublishHdfsPlugin, PublishScpPlugin, BuildInfoPlugin)
  .settings(commonSettings: _*)
  .settings(assemblySettings: _*)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "buildInfo",
    sparkSubmit := {
      val s: TaskStreams = streams.value
      val assemblyFile   = assembly.value
      s.log.info(
        s"using assembly from $assemblyFile; TODO better use assemby form HDFS to support restart from stage in jenkins pipeline"
      )
      val targetDir = target.value.getAbsolutePath
      val wget =
        s"wget -c -nv -O $targetDir/spark-cdh5_2.4.3-production.tgz http://tooldhcp01.endor.gutefrage.net/binaries/spark/spark-cdh5_2.4.3-production.tgz"
          .split(" ")
          .toSeq
      val tar = s"tar -kzxf $targetDir/spark-cdh5_2.4.3-production.tgz -C $targetDir".split(" ").toSeq
      val submitJob =
        s"$targetDir/spark-2.4.3-bin-hadoop2.6/bin/spark-submit --master yarn --deploy-mode cluster --driver-memory 4g --conf spark.ui.port=4052 --driver-class-path /etc/hadoop/conf.cloudera.hdfs --class jobs.Dwh2Positive $assemblyFile"
          .split(" ")
          .toSeq
      if ((wget #&& tar #&& submitJob !) == 0) {
        s.log.success(s"sparkSubmit successful!")
      } else {
        throw new IllegalStateException("sparkSubmit build failed!")
      }
    }
  )

lazy val train = (project in file("train"))
  .settings(commonSettings: _*)

lazy val assemblySettings = AssemblyPlugin.baseAssemblySettings ++ Seq(
  assemblyMergeStrategy in assembly := {
    case PathList("org", "aopalliance", xs @ _*)      => MergeStrategy.last
    case PathList("javax", "inject", xs @ _*)         => MergeStrategy.last
    case PathList("javax", "servlet", xs @ _*)        => MergeStrategy.last
    case PathList("javax", "activation", xs @ _*)     => MergeStrategy.last
    case PathList("org", "apache", xs @ _*)           => MergeStrategy.last
    case PathList("com", "google", xs @ _*)           => MergeStrategy.last
    case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
    case PathList("com", "codahale", xs @ _*)         => MergeStrategy.last
    case PathList("com", "yammer", xs @ _*)           => MergeStrategy.last
    case "about.html"                                 => MergeStrategy.rename
    case "META-INF/ECLIPSEF.RSA"                      => MergeStrategy.last
    case "META-INF/mailcap"                           => MergeStrategy.last
    case "META-INF/mimetypes.default"                 => MergeStrategy.last
    case "plugin.properties"                          => MergeStrategy.last
    case "log4j.properties"                           => MergeStrategy.last
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

lazy val sparkSubmit = taskKey[Unit]("downloads and unpacks spark in target folder. Then runs spark job.")
