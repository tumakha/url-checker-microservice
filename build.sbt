organization in ThisBuild := "com.microservice"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.11.8"

val slickVersion = "3.2.1"
val slick = "com.typesafe.slick" %% "slick" % slickVersion
val slickHikariCP = "com.typesafe.slick" %% "slick-hikaricp" % slickVersion

val postgresql = "org.postgresql" % "postgresql" % "42.1.4"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4" % Test
val h2database = "com.h2database" % "h2" % "1.4.196" % Test

lazy val `url-checker` = (project in file(".")).aggregate(`url-checker-api`, `url-checker-impl`)

lazy val `url-checker-api` = (project in file("url-checker-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `url-checker-impl` = (project in file("url-checker-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      slick,
      slickHikariCP,
      postgresql,
      scalaTest,
      h2database
    )
  )
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(`url-checker-api`)