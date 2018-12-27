val akkaVersion = "2.5.17"
val akkaHttpVersion = "10.1.5"

lazy val lambda_http = project in file(".") settings(
  name := "lambda-http",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.12.7",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", _*) ⇒ MergeStrategy.discard
    case "reference.conf" ⇒ MergeStrategy.concat
    case _ ⇒ MergeStrategy.deduplicate
  },
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.amazonaws" % "aws-lambda-java-core" % "1.1.0"
  )
)