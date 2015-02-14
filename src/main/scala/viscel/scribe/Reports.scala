package viscel.scribe

trait Report {
	def describe: String
}

case class TextReport(override val describe: String) extends Report
case class Precondition(override val describe: String) extends Report