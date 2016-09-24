package com.matthicks.extemplify

import java.io.File

import com.outr.scribe.formatter.{Formatter, FormatterBuilder}
import com.outr.scribe.{LogHandler, Logger, Logging}
import org.apache.commons.io.FileUtils
import org.powerscala.io._

import scala.annotation.tailrec
import scala.sys.process._

object Extemplify {
  private val ConfigRegex = """(?s)---(.+)---""".r
  private val KeyValueRegex = """(.+):(.+)""".r
  private val TitleRegex = """(?s)<title>(.+?)</title>""".r
  private val VarRegex = """[$][{](.+)[}]""".r

  def main(args: Array[String]): Unit = {
    Logger.Root.clearHandlers()
    Logger.Root.addHandler(LogHandler(formatter = FormatterBuilder().date().string(" - ").message.newLine))
    ConfigParser.parse(args, Config()) match {
      case Some(config) => execute(config)
      case None => // parser output an error
    }
  }

  def execute(config: Config): Unit = {
    val e = new Extemplify(config.input, config.output, config.compressCSS, config.lessOutputSubDirectory, config.sassOutputSubDirectory)
    e.execute()
  }
}

class Extemplify(template: File, output: File, compressCSS: Boolean, lessOutputDirectory: String, sassOutputDirectory: String) extends Logging {
  import Extemplify._

  var partials = Map.empty[String, String]
  val pagesDirectory = new File(template, "pages")
  val partialsDirectory = new File(template, "partials")
  val assetsDirectory = new File(template, "assets")
  val lessDirectory = new File(template, "less")
  val sassDirectory = new File(template, "sass")

  def execute(): Unit = {
    // Delete the output directory
    logger.info(s"Deleting anything in ${output.getName}...")
    FileUtils.deleteDirectory(output)

    // Generate pages
    pagesDirectory.listFiles.foreach {
      case f if f.getName.endsWith(".html") => processPage(f)
      case _ => // Ignore others
    }
    // Copy assets
    if (assetsDirectory.exists()) {
      logger.info("Copying Assets directory...")
      assetsDirectory.listFiles().foreach {
        case d if d.isDirectory => FileUtils.copyDirectory(d, new File(output, d.getName), true)
        case f => FileUtils.copyFileToDirectory(f, output)
      }
    }
    // Compile LESS files
    if (lessDirectory.exists()) {
      lessDirectory.listFiles().foreach {
        case f if f.isFile && f.getName.endsWith(".less") => {
          val filename = s"${f.getName.substring(0, f.getName.length - 5)}.css"
          compileLess(f, new File(output, s"$lessOutputDirectory/$filename"), compressCSS)
        }
        case _ => // Ignore others
      }
    }
    // Compile SASS files
    if (sassDirectory.exists()) {
      sassDirectory.listFiles().foreach {
        case f if f.isFile && (f.getName.endsWith(".sass") || f.getName.endsWith(".scss")) && !f.getName.startsWith("_") => {
          val filename = s"${f.getName.substring(0, f.getName.length - 5)}.css"
          compileSass(f, new File(output, s"$sassOutputDirectory/$filename"), compressCSS)
        }
        case _ => // Ignore others
      }
    }
  }

  private def compileLess(input: File, output: File, compress: Boolean): Unit = {
    logger.info(s"Compiling LESS file ${input.getName}...")
    val command = "node_modules/less/bin/lessc"
    val exitCode = Seq(command, input.getAbsolutePath, output.getAbsolutePath) ! LoggingProcessLogger
    if (exitCode != 0) {
      throw new RuntimeException(s"Failed to compile LESS code!")
    }
  }

  private def compileSass(input: File, output: File, compress: Boolean): Unit = {
    logger.info(s"Compiling SASS file ${input.getName}...")
    val command = "node_modules/node-sass/bin/node-sass"
    val exitCode = Seq(command, input.getAbsolutePath, output.getAbsolutePath) ! LoggingProcessLogger
    if (exitCode != 0) {
      throw new RuntimeException(s"Failed to compile SASS code!")
    }
  }

  private def processPage(file: File): Unit = {
    logger.info(s"Processing page ${file.getName}...")
    val page = IO.stream(file, new StringBuilder).toString
    val options = ConfigRegex.findFirstMatchIn(page) match {
      case Some(r) => r.group(1).trim.split("""\n""").map {
        case KeyValueRegex(key, value) => key.trim -> value.trim
      }.toMap
      case None => Map.empty[String, String]
    }
    var pageContent = if (options.isEmpty) {
      page
    } else {
      val offset = page.indexOf("---", page.indexOf("---") + 4) + 3
      page.substring(offset).trim
    }
    var title: Option[String] = None
    options.keys.foreach {
      case "title" => title = options.get("title")
      case "around" => {
        val around = loadPartial(options("around"))
        pageContent = around.replace("${content}", pageContent)
      }
      case _ => // Probably a variable
    }
    title match {
      case Some(t) => pageContent = TitleRegex.replaceAllIn(pageContent, s"<title>$t</title>")
      case None => // No title change
    }
    pageContent = loadVariables(file, pageContent, options)
    output.mkdirs()
    val out = new File(output, file.getName)
    IO.stream(pageContent, out)
  }

  /**
    * Calls itself recursively as it finds replacements since partial joins could add additional variables.
    */
  @tailrec
  private def loadVariables(file: File, pageContent: String, variables: Map[String, String]): String = {
    val updated = VarRegex.replaceAllIn(pageContent, regexMatch => {
      regexMatch.group(1) match {
        case KeyValueRegex(key, value) => key match {
          case "include" => loadPartial(value).replace("$", """\$""")
          case _ => throw new RuntimeException(s"Unknown key for variable: $key (value: $value) at ${file.getName}:${regexMatch.start}")
        }
        case variable => variables.getOrElse(variable, "")
      }
    })
    if (updated == pageContent) {
      pageContent
    } else {
      loadVariables(file, updated, variables)
    }
  }

  private def loadPartial(name: String): String = partials.get(name) match {
    case Some(partial) => partial
    case None => {
      val file = new File(partialsDirectory, s"$name.html")
      val partial = IO.stream(file, new StringBuilder).toString
      partials += name -> partial
      partial
    }
  }
}

object LoggingProcessLogger extends ProcessLogger {
  override def out(s: => String): Unit = System.out.println(s)

  override def err(s: => String): Unit = System.err.println(s)

  override def buffer[T](f: => T): T = f
}