package viscel.core

import akka.actor.{ ActorSystem, Props, Actor }
import akka.io.IO
import com.typesafe.scalalogging.slf4j.Logging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._
import spray.can.Http
import spray.client.pipelining._
import spray.http.Uri
import viscel._
import viscel.store._
import spray.http.HttpHeaders.`Content-Type`
import spray.http.HttpHeaders.Location
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.ContentType
import scalax.io._
import scala.collection.JavaConversions._
import org.neo4j.graphdb.Direction

trait PlaceholderPageRunner extends Logging {

	def createElementNodes(page: PageDescription): Seq[ElementNode] = Neo.txs {
		page match {
			case pp: PagePointer =>
				logger.info(s"create element for ${pp.loc}")
				Seq(ElementNode.create("origin" -> pp.loc.toString))
			case fp: FullPage => fp.elements.map { ed =>
				logger.info(s"create element for ${ed}")
				ElementNode.create(ed.toMap.toSeq: _*)
			}
		}
	}

	def apply(page: PageDescription): Future[(PageDescription, Seq[ElementNode])] = Future.successful { (page, createElementNodes(page)) }

}

