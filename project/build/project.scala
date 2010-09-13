import sbt._

class Project(info: ProjectInfo) extends DefaultProject(info)
{
  lazy val mochi = "aria42" % "hobbs-deps" % "0.1-SNAPSHOT" from "http://csail.mit.edu/~aria42/hobbs-deps-fatjar-0.1-SNAPSHOT.jar"  
  override val mainClass = Some("coref.hobbs.Main")
}
