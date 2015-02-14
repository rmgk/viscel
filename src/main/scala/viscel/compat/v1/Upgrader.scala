package viscel.compat.v1

import org.neo4j.graphdb.Node
import org.scalactic.{Bad, ErrorMessage, Every, Good, One, Or}
import viscel.compat.v1.Story.More.{Archive, Issue}
import viscel.compat.v1.database.Implicits.NodeOps
import viscel.compat.v1.database.{Book, NeoCodec, NeoInstance, Ntx, label}
import viscel.compat.v1.{Story => StoryV1}
import viscel.narration.Data
import viscel.scribe.Scribe
import viscel.scribe.database.Codec
import viscel.scribe.narration.{Asset, More, Normal, Story => StoryV2, Volatile}
import viscel.{Log, scribe}

import scala.Predef.ArrowAssoc
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.immutable.Map

object Upgrader {

	import viscel.scribe.narration.SelectMore.stringToURL


	def translateStory(story: StoryV1): StoryV2 Or Every[ErrorMessage] = story match {
		case StoryV1.More(loc, kind) =>
			val url = stringToURL(loc.toString)
			val policiy = kind match {
				case Archive | Issue => Volatile
				case _ => Normal
			}
			val data = List(kind.name)
			Good(More(url, policiy, data))
		case StoryV1.Chapter(name, meta) =>
			Good(Data.Chapter(name, meta))
		case StoryV1.Asset(source, origin, metadata, _) =>
			Good(Data.Article(
				blob = source.toString,
				origin = origin.toString,
				data = metadata))
		case StoryV1.Failed(messages) => Bad(Every.from(messages).getOrElse(One("unnamed error")))
		case StoryV1.Blob(sha1, mime) => Bad(One("can not convert blobs"))
	}


	def convert(oldNode: Node)(implicit ntx1: Ntx, ntx2: viscel.scribe.database.Ntx): Node = {
		val newNode =
			if (oldNode.hasLabel(label.Collection)) {
				val book = Book(oldNode)
				ntx2.create(viscel.scribe.database.label.Book, "id" -> book.id, "name" -> book.name)
			}
			else if (oldNode.hasLabel(label.More)) {
				Codec.create(translateStory(NeoCodec.load[StoryV1.More](oldNode)).get)
			}
			else if (oldNode.hasLabel(label.Chapter)) {
				Codec.create(translateStory(NeoCodec.load[StoryV1.Chapter](oldNode)).get)
			}
			else if (oldNode.hasLabel(label.Asset)) {
				val asset = NeoCodec.load[StoryV1.Asset](oldNode)
				val newAsset = Codec.create(translateStory(asset).get)
				asset.blob.foreach { blob =>
					val bn = Codec.create(viscel.scribe.narration.Blob(blob.sha1, blob.mediatype))
					newAsset.to_=(viscel.scribe.database.rel.blob, bn)
				}
				newAsset
			}
			else throw new IllegalArgumentException(s"unknown nodetype ${ oldNode.getLabels.asScala.toList }")

		val newbelow = oldNode.layerBelow.map(n => convert(n))
		viscel.scribe.database.Archive.connectLayer(newbelow)
		newbelow.headOption.foreach(h => newNode.to_=(scribe.database.rel.describes, h))


		newNode
	}


	def doUpgrade(scribe: Scribe, neo1: NeoInstance) = {
		Log.info("loading books")
		val books = neo1.tx { implicit ntx1 =>
			scribe.neo.tx { implicit ntx2 =>

				val cfg1 = Config.get()
				val cfg2 = viscel.scribe.store.Config.get()

				cfg1.self.getPropertyKeys.asScala.foreach { k =>
					cfg2.self.setProperty(k, cfg1.self.getProperty(k))
				}
				cfg2.self.setProperty("version", 2)

				ntx1.nodes(label.Collection).map { n => Book.apply(n) }.toList
			}
		}

		Log.info("staring conversion")

		books.foreach { book =>
			neo1.tx { implicit ntx1 =>
				Log.info(s"upgrading ${ book.name }")
				scribe.neo.tx { implicit ntx2 =>
					convert(book.self)
				}
			}
		}
		Log.info("all done")
	}
}
