resolvers += ("Gutefrage Release Repo" at "http://artifacts.endor.gutefrage.net/content/groups/public")
  .withAllowInsecureProtocol(true)

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.2.1")

addSbtPlugin("net.gutefrage.sbt" %% "aurora-plugins" % "0.5.15")

addSbtPlugin("net.gutefrage.sbt" %% "deployment-plugins" % "0.5.15")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
