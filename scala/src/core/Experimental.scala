// package viscel.core

// import spray.client.pipelining._
// import scala.concurrent._
// import ExecutionContext.Implicits.global
// import spray.http.Uri
// import com.typesafe.scalalogging.slf4j.Logging
// import org.jsoup.nodes.Document
// import viscel.{Element, ElementSeed}

// class Carciphona extends Logging {

// 	val firstUri = "http://carciphona.com/view.php?page=cover&chapter=1&lang="

// 	def first = new Seed(firstUri, 1)

// }

// class Sprout (document: Document, val seed: Seed) extends Logging {
// 	val extractImageUri = """[\w-]+:url\((.*)\)""".r

// 	val (nextUri, imgUri) = {
// 		val linkarea = document.getElementById("link")
// 		val extractImageUri(img) = linkarea.parent.attr("style")
// 		val absimg = Uri.parseAndResolve(img, seed.uri)
// 		val next = linkarea.getElementById("nextarea").attr("abs:href")
// 		logger.info(s"img: $absimg, next: $next")
// 		(next, absimg)
// 	}

// 	val elements: Seq[ElementSeed] = for (img <- List(imgUri)) yield {
// 		new ElementSeed(origin = seed.uri, source = img.toString)
// 	}

// 	val next: Seed = new Seed(nextUri, seed.pos + 1)
// }

// class Seed(val uri: String, val pos: Int) extends (Document => Sprout) {
// 	def apply(doc: Document): Sprout = new Sprout(doc, this)
// }
