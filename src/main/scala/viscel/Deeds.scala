package viscel

import org.scalactic.ErrorMessage
import rescala.Evt
import rescala.propagation.Engines.default
import spray.http.HttpResponse
import viscel.store.Collection

import scala.util.Try

object Deeds {
	val uiCollection = Evt[Collection]()
	val responses = Evt[Try[HttpResponse]]()
	val jobResult = Evt[List[ErrorMessage]]()

	val sessionDownloads = responses.count()
	val sessionUiRequests = uiCollection.count()
}
