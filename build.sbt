def scalacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // If we're building with Scala > 2.11, enable the compile option
    //  switch to support our anonymous Bundle definitions:
    //  https://github.com/scala/bug/issues/10047
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 => Seq()
      case _ => Seq("-Xsource:2.11")
    }
  }
}

def javacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // Scala 2.12 requires Java 8. We continue to generate
    //  Java 7 compatible code for Scala 2.11
    //  for compatibility with old clients.
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 =>
        Seq("-source", "1.7", "-target", "1.7")
      case _ =>
        Seq("-source", "1.8", "-target", "1.8")
    }
  }
}


val commonSettings = Seq(
  scalaVersion := "2.12.10",
  //crossScalaVersions := Seq("2.11.12", "2.12.4"),
  libraryDependencies ++= Seq(
    "edu.berkeley.cs" %% "chisel3" % "3.4.3",
    "org.scalatest" %% "scalatest" % "3.0.8",
    "edu.berkeley.cs" %% "chisel-iotesters" % "1.5.3"
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases")
  )
)

crossScalaVersions := Seq("2.12.10", "2.11.12")

val miniSettings = commonSettings ++ Seq(
  name := "riscv-mini-dfc",
  version := "1.0",
  organization := "SKT-CPUOS")

lazy val lib  = project settings commonSettings
lazy val mini = project in file(".") settings miniSettings dependsOn lib

scalacOptions ++= scalacOptionsVersion(scalaVersion.value)

javacOptions ++= javacOptionsVersion(scalaVersion.value)