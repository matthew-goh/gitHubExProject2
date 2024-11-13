name := """githubTutorial"""
organization := "com.example"

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    name := "githubTutorial"
  )
  .enablePlugins(PlayScala)

resolvers += "HMRC-open-artefacts-maven2" at "https://open.artefacts.tax.service.gov.uk/maven2"
libraryDependencies ++= Seq(
  "uk.gov.hmrc.mongo"      %% "hmrc-mongo-play-28"   % "0.63.0",
  guice,
  "org.scalatest"          %% "scalatest"               % "3.2.5"             % Test,
  "org.scalamock"          %% "scalamock"               % "5.1.0"             % Test,
  "org.scalatestplus.play" %% "scalatestplus-play"   % "5.1.0"          % Test,
  ws,
  "org.typelevel"                %% "cats-core"                 % "2.3.0"
)


// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.example.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.example.binders._"
