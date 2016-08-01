package viscel.scribe.database


import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._

class Books(path: Path) {
	def all(): List[Book] = Files.list(path).iterator().asScala.filter(Files.isRegularFile(_)).map(new Book(_)).toList
}
