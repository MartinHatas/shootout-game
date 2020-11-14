organization in ThisBuild := "com.hat"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.13.3"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.1.1" % Test

lazy val `shootout` = (project in file("."))
  .aggregate(`shootout-api`, `shootout-impl`)

val pac4jVersion = "3.7.0"
val lagomPac4j = "org.pac4j" %% "lagom-pac4j" % "2.2.1"
val pac4jHttp = "org.pac4j" % "pac4j-http" % pac4jVersion
val pac4jJwt = "org.pac4j" % "pac4j-jwt" % pac4jVersion


lazy val `user-api` = (project in file("user-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi, lagomLogback
    )
  )

lazy val `user-impl` = (project in file("user-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      lagomPac4j,
      pac4jHttp,
      pac4jJwt,
      macwire,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`user-api`)


lazy val `shootout-api` = (project in file("shootout-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi, lagomLogback
    )
  )

lazy val `shootout-impl` = (project in file("shootout-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`shootout-api`, `user-api`)


