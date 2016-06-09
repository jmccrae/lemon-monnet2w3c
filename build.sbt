name := "monnet2w3c"

organization := "org.insightcentre.unlp"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
    "org.apache.jena" % "jena-arq" % "3.1.0",
    "org.scalatest" %% "scalatest" % "2.1.7" % "test",
    "org.mockito" % "mockito-core" % "1.10.8" % "test",
    "com.github.scopt" %% "scopt" % "3.3.0",
    "org.slf4j" % "slf4j-nop" % "1.7.21"

)

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature")

