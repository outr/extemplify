name := "extemplify"

version := "1.0.0"

organization := "com.matthicks"

scalaVersion := "2.11.8"

sbtVersion := "0.13.11"

libraryDependencies += "com.outr.scribe" %% "scribe-slf4j" % "1.2.3"

libraryDependencies += "org.powerscala" %% "powerscala-io" % "2.0.2"

libraryDependencies += "org.apache.commons" % "commons-io" % "1.3.2"

libraryDependencies += "com.github.scopt" %% "scopt" % "3.5.0"

assemblyJarName in assembly := "extemplify.jar"