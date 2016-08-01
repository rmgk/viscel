package viscel.scribe.database

import java.nio.file.{Files, Path}

class Book(path: Path) {
	def size(i: Int): Int = 0

	lazy val name: String = upickle.default.read[String](Files.lines(path).findFirst().get())

	lazy val id: String = path.getFileName.toString

}
