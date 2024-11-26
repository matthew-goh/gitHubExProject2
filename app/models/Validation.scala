package models

import play.api.data.validation._

object Validation {
  def regexConstraint(regex: scala.util.matching.Regex, errorMessage: String): Constraint[String] = {
    Constraint[String]("Can optionally include a folder path, e.g. folder1/folder2/filename.txt") { input =>
      regex.findFirstMatchIn(input) match {
        case Some(_) => Valid
        case None    => Invalid(errorMessage)
      }
    }
  }
}
