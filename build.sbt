val scalaHomeProperty = "macroparser.scala.home"
lazy val sharedSettings: Seq[Setting[_]] = Seq(
  version := "0.1.0-SNAPSHOT",
  organization := "com.github.duhemm",
  scalaVersion := "2.11.6",
  resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  libraryDependencies += "org.scalameta" %% "scalameta" % "0.1.0-SNAPSHOT",
  libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
  scalaHome := {
    System getProperty scalaHomeProperty match {
      case null =>
        None
      case scalaHome => Some(file(scalaHome))
    }
  }
)

lazy val testSettings: Seq[Setting[_]] = Seq(
  libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.12.2" % "test",
  fullClasspath in Test := {
    val testcp = (fullClasspath in Test).value.files.map(_.getAbsolutePath).mkString(java.io.File.pathSeparatorChar.toString)
    sys.props("sbt.class.directory") = testcp
    (fullClasspath in Test).value
  }
)

val pluginJarName = "fat-plugin.jar"

lazy val usePluginSettings = Seq(
  scalacOptions in Compile <++= (Keys.`package` in (plugin, Compile)) map { (jar: File) =>
    val fatJar = file(jar.getParent + "/" + pluginJarName)
    System.setProperty("sbt.paths.plugin.jar", fatJar.getAbsolutePath)
    val addPlugin = "-Xplugin:" + fatJar.getAbsolutePath
    // Thanks Jason for this cool idea (taken from https://github.com/retronym/boxer)
    // add plugin timestamp to compiler options to trigger recompile of
    // main after editing the plugin. (Otherwise a 'clean' is needed.)
    val dummy = "-Jdummy=" + fatJar.lastModified
    Seq(addPlugin, dummy)
  }
)

lazy val quasiquotesMacros: Project =
  (project in file("quasiquotes-macros")) settings (
    sharedSettings: _*
  ) settings (
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full)
  )

lazy val quasiquotes: Project =
  (project in file("quasiquotes")) settings (
    sharedSettings ++ testSettings: _*
  ) settings (
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full)
  ) aggregate quasiquotesMacros dependsOn quasiquotesMacros

lazy val plugin: Project =
  (project in file("plugin")) settings (
    sharedSettings: _*
  ) settings (
    name := "parsermacros-plugin",
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _),
    libraryDependencies += "org.scalameta" % "scalahost" % "0.1.0-SNAPSHOT" cross CrossVersion.full,
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false, includeDependency = true),
    assemblyJarName in assembly := pluginJarName,
    assemblyMergeStrategy in assembly := {
      // scalahost also provides `scalac-plugin.xml`, but we are only interested in ours.
      case "scalac-plugin.xml"                           => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    // Produce a fat jar containing dependencies of the plugin after compilation. This is required because the plugin
    // depends on scala.meta, which must therefore be available when the plugin is run.
    // It looks like this task is defined in the wrong order (assembly and then compilation), but it seems to work fine.
    compile <<= (compile in Compile) dependsOn assembly,
    resourceDirectory in Compile <<= baseDirectory(_ / "src" / "main" / "scala" / "org" / "duhemm" / "parsermacro" / "embedded"),
    initialCommands in console := """
      import scala.meta._
      import scala.meta.dialects.Scala211
      import scala.meta.tokenquasiquotes._
    """
  ) dependsOn quasiquotes

lazy val sandboxMacros: Project =
  (project in file("sandbox-macros")) settings (
    sharedSettings ++ usePluginSettings: _*
  ) settings (
    publishArtifact in Compile := false,
    compile <<= (compile in Compile)
  ) dependsOn quasiquotes

lazy val sandboxClients =
  (project in file("sandbox-clients")) settings (
    sharedSettings ++ usePluginSettings: _*
  ) settings (
    // Always clean before running compile in this subproject
    compile <<= (compile in Compile) dependsOn clean,
    scalacOptions ++= Seq("-Ymacro-debug-verbose")
  ) dependsOn sandboxMacros

lazy val tests =
  (project in file("tests")) settings (
    sharedSettings ++ usePluginSettings ++ testSettings: _*
  ) settings (
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _)
  ) dependsOn plugin
