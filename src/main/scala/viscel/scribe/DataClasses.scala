package viscel.scribe

import java.time.Instant

import cats.syntax.either._
import io.circe.generic.extras.auto._
import io.circe.generic.extras._
import io.circe.{Decoder, Encoder, Json => cJson}
import viscel.shared.Blob

sealed trait ScribeDataRow {
	/** reference that spawned this entry */
	def ref: Vurl
	def matchesRef(o: ScribeDataRow): Boolean = ref == o.ref
	def differentContent(o: ScribeDataRow): Boolean = (this, o) match {
		case (ScribePage(ref1, loc1, _, contents1), ScribePage(ref2, loc2, _, contents2)) =>
			!(ref1 == ref2 && loc1 == loc2 && contents1 == contents2)
		case (ScribeBlob(ref1, loc1, _, blob1), ScribeBlob(ref2, loc2, _, blob2)) =>
			!(ref1 == ref2 && loc1 == loc2 && blob1 == blob2)
		case _ => true
	}
}

/*@key("Page")*/ case class ScribePage(
	/* reference that spawned this entry */
	ref: Vurl,
	/* location that was finally resolved and downloaded */
	loc: Vurl,
	date: Instant,
	contents: List[WebContent]
) extends ScribeDataRow {
	def articleCount: Int = contents.count(_.isInstanceOf[ArticleRef])
}

/*@key("Blob")*/ case class ScribeBlob(
	/* reference that spawned this entry */
	ref: Vurl,
	/* location that was finally resolved and downloaded */
	loc: Vurl,
	date: Instant,
	blob: Blob
) extends ScribeDataRow


sealed trait ReadableContent
case class Article(article: ArticleRef, blob: Option[Blob]) extends ReadableContent

sealed trait WebContent
/*@key("Chapter")*/ case class Chapter(name: String) extends WebContent with ReadableContent
/*@key("Article")*/ case class ArticleRef(ref: Vurl, origin: Vurl, data: Map[String, String] = Map()) extends WebContent
/*@key("Link")*/ case class Link(ref: Vurl, policy: Policy = Normal, data: List[String] = Nil) extends WebContent


sealed trait Policy
/*@key("Normal")*/ case object Normal extends Policy
/*@key("Volatile")*/ case object Volatile extends Policy


object ScribePicklers {

	implicit val config: Configuration = Configuration.default.withDefaults.withDiscriminator("$" + "type")

	implicit val webContentReader: Decoder[WebContent] = semiauto.deriveDecoder[WebContent].prepare{ cursor =>
		val t = cursor.downField("$type")
		t.as[String] match {
			case Right("Article") => t.set(io.circe.Json.fromString("ArticleRef")).up
			case _ => cursor
		}
	}

	implicit val webContentWriter: Encoder[WebContent] = semiauto.deriveEncoder[WebContent].mapJson{js =>
		js.hcursor.get[String]("$type") match {
			case Right("ArticleRef") => js.deepMerge(cJson.obj("$type" -> cJson.fromString("Article")))
			case _ => js
		}
	}


	implicit val appendlogReader: Decoder[ScribeDataRow] = semiauto.deriveDecoder[ScribeDataRow].prepare{ cursor =>
		val t = cursor.downField("$type")
		t.as[String] match {
			case Right("Blob") => t.set(io.circe.Json.fromString("ScribeBlob")).up
			case Right("Page") => t.set(io.circe.Json.fromString("ScribePage")).up
			case _ => cursor
		}
	}
	implicit val appendLogWriter: Encoder[ScribeDataRow] = semiauto.deriveEncoder[ScribeDataRow].mapJson{js =>
		js.hcursor.get[String]("$type") match {
			case Right("ScribeBlob") => js.deepMerge(cJson.obj("$type" -> cJson.fromString("Blob")))
			case Right("ScribePage") => js.deepMerge(cJson.obj("$type" -> cJson.fromString("Page")))
			case _ => js
		}
	}

	implicit val instantWriter: Encoder[Instant] = Encoder.encodeString.contramap[Instant](_.toString)

	implicit val instantReader: Decoder[Instant] = Decoder.decodeString.emap { str =>
		Either.catchNonFatal(Instant.parse(str)).leftMap(t => "Instant: " + t.getMessage)
	}

}
