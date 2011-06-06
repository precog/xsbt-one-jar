import sbt._
import Keys._

object OneJarPlugin extends Plugin {
  val oneJarTask = TaskKey[Unit]("one-jar")
  val oneJarInJars = SettingKey[Seq[File]]("one-jar-in-jars")
  val oneJarInJarsTask = TaskKey[Seq[File]]("one-jar-in-jars-task")
 
  val oneJarSettings = Seq(
    oneJarInJars <<= (scalaInstance) { (si) => Seq(si.libraryJar) },
    oneJarInJarsTask <<= inJarsTaskImpl,
    oneJarTask <<= oneJarTaskImpl
  )

  private def inJarsTaskImpl = (dependencyClasspath in Compile, classDirectory in Compile, oneJarInJars) map {
    (dependencyClasspath, classDirectory, oneJarInJars) => 
      import Build.data
      data(dependencyClasspath) ++ oneJarInJars :+ classDirectory
  }
 
  private def oneJarTaskImpl = (compile in Compile, oneJarInJarsTask, mainClass, name, version, target) map {
    (_, dependencies, mainClass, name, version, target) => {
      import java.io.{ByteArrayInputStream, File}
      import java.util.jar.Manifest
      import org.apache.commons.io.FileUtils

      val manifest = new Manifest(
        new ByteArrayInputStream((
          "Manifest-Version: 1.0\n" + 
          "Main-Class: " + mainClass.get + "\n"
          ).getBytes
        )
      )
      
      val BasicVersion = """(\d+)\.(\d+)\.?(\d+)?""".r
      val versionString = version match {
        case BasicVersion(major, _, _) => "-v" + major
        case _ => version.toString
      }

      IO.withTemporaryDirectory { tmpDir =>
        for (dependency <- dependencies) {
          println("Unzipping " + dependency + " to " + tmpDir)

          try {
            if (dependency.getName.toLowerCase.endsWith("jar")) {
              IO.unzip(dependency, tmpDir)
            } else if (dependency.isDirectory) {
              IO.copyDirectory(dependency, tmpDir, true)
            } else {
              IO.copyFile(dependency, tmpDir)
            }
          } catch {
            case t: Throwable => println("Error extracting classes from dependency " + dependency)
          }
        }

        new File(tmpDir, "META-INF/MANIFEST.MF").delete

        val destName = name + versionString + ".jar"
        val destJar = new File(tmpDir, destName)
        try {
          val files = allFiles(tmpDir).map(f => (f, f.getPath.replace(tmpDir.getPath + "/", "")))
          IO.jar(files, destJar, manifest)
          IO.copyFile(destJar, new File(target, destName))
        } catch {
          case t: Throwable => t.printStackTrace
        }
      }
    }
  }

  private def allFiles(file: File): Vector[File] = {
    if (file.exists) {
      if (file.isDirectory) Vector(file.listFiles: _*).flatMap(allFiles)
      else Vector(file)
    } else {
      Vector.empty[File]
    }
  }
}


// vim: set ts=4 sw=4 et:
