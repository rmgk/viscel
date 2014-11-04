package viscel.database

import java.nio.file.{Files, Paths}

import org.neo4j.graphdb.Node
import viscel.crawler.AbsUri
import viscel.narration.Story.{Blob, Asset, Chapter, More}
import viscel.narration.{Narrator, Story}
import viscel.store.{Vault, Coin}
import scala.pickling._
import scala.pickling.json._
import scala.Predef.implicitly
import scala.Predef.classOf

object ArchiveExport {

	def export(node: Node)(implicit ntx: Ntx): List[Story] = node match {
		case Coin.isAsset(a) => a.story.copy(blob = a.blob.map(b => Story.Blob(b.sha1, b.mediastring))) :: Nil
		case Coin.isPage(p) =>
			val below = Traversal.layerBelow(p.self).flatMap(export)
			p.story.copy(narration = below) :: Nil
		case Coin.hasStory(c) => c.story :: Nil
		case Coin.isCollection(c) => Traversal.layerBelow(c.self).flatMap(export)
		case _ => List()
	}

	def exportID(id: String)(implicit ntx: Ntx): List[Story] = {
		Vault.find.collection(id).toList.map(_.self).flatMap(export)
	}

	import argonaut._
	import argonaut.Argonaut._

	implicit def encodeAbsuri: EncodeJson[AbsUri] = jencode1L((p: AbsUri) => p.toString)("uri")
	implicit def decodeAbsuri: DecodeJson[AbsUri] = jdecode1L(AbsUri.fromString)("uri")

	implicit def encodeStory: EncodeJson[Story] = EncodeJson {
		case m @ Story.More(_, _, _) => Json("More" := MoreCodec.encode(m))
		case m @ Story.Chapter(_, _) => Json("Chapter" := ChapterCodec.encode(m))
		case m @ Story.Asset(_, _, _, _) => Json("Asset" := AssetCodec.encode(m))
		case other => s"unhandled story $other".asJson
	}

	implicit def decodeStory: DecodeJson[Story] = DecodeJson { c =>
		tagged("More", c, MoreCodec.Decoder) |||
			tagged("Chapter", c, ChapterCodec.Decoder) |||
			tagged("Asset", c, AssetCodec.Decoder)
	}

	def MoreCodec: CodecJson[More] = casecodec3(Story.More.apply, Story.More.unapply)("loc", "pagetype", "narration")
	def ChapterCodec: CodecJson[Chapter] = casecodec2(Story.Chapter.apply, Story.Chapter.unapply)("name", "metadata")
	def AssetCodec: CodecJson[Asset] = casecodec4(Story.Asset.apply, Story.Asset.unapply)("source", "origin", "metadata", "blob")
	implicit def BlobCodec: CodecJson[Blob] = casecodec2(Story.Blob.apply, Story.Blob.unapply)("sha1", "mediatype")

	def tagged[A](tag: String, c: HCursor, decoder: DecodeJson[A]): DecodeResult[A] =
		(c --\ tag).hcursor.fold(DecodeResult.fail[A]("Invalid tagged type", c.history))(decoder.decode)



	def jsonID(id: String)(implicit ntx: Ntx): Json = {
		exportID(id).asJson
	}

	def exportAll(implicit ntx: Ntx): Unit = {
		val dir = Paths.get("export")
		Files.createDirectories(dir)
		for (col <- Util.listCollections) {
			val cdir = dir.resolve(col.id)
			Files.write(cdir, export(col.self).asJson.nospaces.getBytes("UTF-8"))
		}

	}

	def decodeJson(json: String) = Parse.decodeEither[List[Story]](json)

}
