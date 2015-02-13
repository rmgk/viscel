package viscel

import org.neo4j.graphdb.Node
import viscel.database.Implicits.NodeOps
import viscel.database.{Book, NeoCodec, NeoInstance, Ntx, label}
import viscel.scribe.Scribe
import viscel.scribe.database.Codec
import viscel.scribe.narration.{Normal, Volatile}
import viscel.shared.Story
import viscel.shared.Story.More.{Archive, Issue}
import viscel.store.Config
import viscel.store.Config.ConfigNode


import scala.Predef.ArrowAssoc
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

object Upgrader {

	import viscel.scribe.narration.SelectUtil.stringToURL

	def convert(oldNode: Node)(implicit ntx1: Ntx, ntx2: viscel.scribe.database.Ntx): Node = {
		val newNode =
			if (oldNode.hasLabel(label.Collection)) {
				val book = Book(oldNode)
				ntx2.create(viscel.scribe.database.label.Book, "id" -> book.id, "name" -> book.name)
			}
			else if (oldNode.hasLabel(label.More)) {
				val more = NeoCodec.load[Story.More](oldNode)
				val url = stringToURL(more.loc.toString)
				val policiy = more.kind match {
					case Archive | Issue => Volatile
					case _ => Normal
				}
				val data = List(more.kind.name)
				Codec.create(viscel.scribe.narration.More(url, policiy, data))
			}
			else if (oldNode.hasLabel(label.Chapter)) {
				val chapter = NeoCodec.load[Story.Chapter](oldNode)
				Codec.create(viscel.scribe.narration.Asset(None, None, 1, List(chapter.name) ++ chapter.metadata.flatMap { case (k, v) => List(k, v) }))
			}
			else if (oldNode.hasLabel(label.Blob)) {
				val blob = NeoCodec.load[Story.Blob](oldNode)
				Codec.create(viscel.scribe.narration.Blob(blob.sha1, blob.mediatype))
			}
			else if (oldNode.hasLabel(label.Asset)) {
				val asset = NeoCodec.load[Story.Asset](oldNode)
				val newAsset = Codec.create(viscel.scribe.narration.Asset(
					Some(asset.source.toString),
					Some(asset.origin.toString),
					0,
					asset.metadata.flatMap { case (k, v) => List(k, v) }.toList))
				asset.blob.foreach { blob =>
					val bn = Codec.create(viscel.scribe.narration.Blob(blob.sha1, blob.mediatype))
					newAsset.to_=(viscel.scribe.database.rel.blob, bn)
				}
				newAsset
			}
			else throw new IllegalArgumentException("unknown node")

		val newbelow = oldNode.layerBelow.map(n => convert(n))
		viscel.scribe.database.Archive.connectLayer(newbelow)
		newbelow.headOption.foreach(h => newNode.to_=(scribe.database.rel.describes, h))


		newNode
	}


	def doUpgrade(scribe: Scribe, neo1: NeoInstance) = neo1.tx { implicit ntx1 =>
		scribe.neo.tx { implicit ntx2 =>
			ntx1.nodes(label.Collection).map { n => Book.apply(n) }.toList.foreach { book =>
				convert(book.self)
			}
			val cfg1 = Config.get()
			val cfg2 = viscel.scribe.store.Config.get()

			cfg1.self.getPropertyKeys.asScala.foreach{ k =>
				cfg2.self.setProperty(k, cfg1.self.getProperty(k))
			}

		}
	}
}
