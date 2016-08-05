package com.matthicks.extemplify

import java.io.File

case class Config(input: File = new File("src"),
                  output: File = new File("dist"),
                  compressCSS: Boolean = true,
                  lessOutputSubDirectory: String = "css")

object ConfigParser extends scopt.OptionParser[Config]("extemplify") {
  head("extemplify", "1.0.0")
  opt[File]('i', "input").required().valueName("<file>").action((x, c) => c.copy(input = x)).text("input is a required file property")
  opt[File]('o', "output").required().valueName("<file>").action((x, c) => c.copy(output = x)).text("output is a required file property")
  opt[Boolean]('c', "compressCSS").action((x, c) => c.copy(compressCSS = x)).text("compresses the generated CSS content (defaults to true)")
  opt[String]("lessOutputSubDirectory").action((x, c) => c.copy(lessOutputSubDirectory = x)).text("configures the sub-directory used for generated CSS files (defaults to 'css')")
  help("help").text("print this usage text")
}