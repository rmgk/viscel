package viscel.collection

import scalax.file.Path
import spray.http.{HttpResponse, HttpEntity, MediaTypes}

class Blobs {

	val folder = Path.fromString("../../viscel/cache").toRealPath()

	lazy val fileList: IndexedSeq[String] = folder.***.iterator.map{p => p.segments.takeRight(2).mkString}.filter{_.length == 40}.toIndexedSeq.sorted

	def display = HttpResponse(
		entity = HttpEntity(MediaTypes.`text/html`,
			<html>
				<body>
					<h1>Filelist</h1>
					<ul>
					{ fileList map {s => <li><a href={"/b/" + s}>{s}</a></li>} }
					</ul>
				</body>
			</html>.toString()
		)
	)
}
