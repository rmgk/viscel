package viscel.store

import org.neo4j.graphdb.Node
import org.scalactic.TypeCheckedTripleEquals._
import viscel.description.Story

import scala.collection.mutable

object ArchiveManipulation {

	def fixSkiplist(asset: coin.Asset): Unit = {
		val nextAsset_? = asset.next.flatMap(_.findForward { case asset: coin.Asset => asset })
		nextAsset_? match {
			case None => asset.self.outgoing(rel.skip).foreach(_.delete())
			case Some(nextAsset) => asset.self.to_=(rel.skip, nextAsset.self)
		}
		val prevAsset_? = asset.prev.flatMap(_.findBackward { case asset: coin.Asset => asset })
		prevAsset_? match {
			case None => asset.self.incoming(rel.skip).foreach(_.delete())
			case Some(prevAsset) => prevAsset.self.to_=(rel.skip, asset.self)
		}
	}

	def connectLayer(layer: List[ArchiveNode]) = {
		layer.reduceLeftOption { (prev, next) => prev.narc = next; next }
		layer.lastOption.foreach(_.self.outgoing(rel.narc).foreach(_.delete()))
		layer.headOption.foreach(_.self.incoming(rel.narc).foreach(_.delete()))
		layer
	}

	def create(desc: Story)(implicit neo: Neo): ArchiveNode = {
		desc match {
			case Story.Failed(reason) => throw new IllegalArgumentException(reason.toString())
			case pointer@Story.More(_, _) => Vault.create.page(pointer)
			case chap@Story.Chapter(_, _) => Vault.create.chapter(chap)
			case asset@Story.Asset(_, _, _) => Vault.create.asset(asset)
			case core@Story.Core(_, _, _, _) => Vault.create.core(core)
		}
	}

	def deleteRecursive(node: Node)(implicit neo: Neo): Unit = {

	}

	def replaceLayer(oldLayer: List[ArchiveNode], oldNarration: List[Story], newNarration: List[Story])(implicit neo: Neo): List[ArchiveNode] = {
		val oldMap = mutable.Map(oldNarration zip oldLayer: _*)
		val newLayer = newNarration.map { story =>
			oldMap.get(story) match {
				case None => create(story)
				case Some(oldCoin) =>
					oldMap.remove(story)
					oldCoin
			}
		}
		oldMap.mapValues(_.deleteRecursive())
		connectLayer(newLayer)
	}

	def applyNarration(target: DescribingNode, narration: List[Story])(implicit neo: Neo): List[ArchiveNode] = neo.txs {
		val oldLayer = target.describes.toList.flatMap(_.layer)
		val oldNarration = oldLayer.map(_.story)
		if (oldNarration === narration) oldLayer
		else {
			val newLayer = replaceLayer(oldLayer, oldNarration, narration)
			target.describes = newLayer.headOption
			newLayer.collect { case asset: coin.Asset => fixSkiplist(asset) }
			newLayer
		}
	}
}
