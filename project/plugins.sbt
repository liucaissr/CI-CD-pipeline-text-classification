logLevel := Level.Warn

resolvers += ("Gutefrage Release Repo" at "http://artifacts.endor.gutefrage.net/content/groups/public")
  .withAllowInsecureProtocol(true)

addSbtPlugin("no.arktekk.sbt" % "aether-deploy" % "0.23.0")

addSbtPlugin("com.twitter" %% "scrooge-sbt-plugin" % "19.2.0")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.2.1")

addSbtPlugin("net.gutefrage.sbt" %% "aurora-plugins" % "0.5.15")

// custom build for cdh5 migration
addSbtPlugin("net.gutefrage.sbt" %% "deployment-plugins" % "0.5.15")

libraryDependencies ++= Seq(
  "org.apache.thrift" % "libthrift" % "0.8.0" // https://github.com/twitter/scrooge/issues/177 thrift 0.5.0-1 might not be available
)

// due to scala_2.10
addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
)