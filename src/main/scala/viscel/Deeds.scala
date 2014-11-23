package viscel

import org.scalactic.ErrorMessage
import rescala.events.ImperativeEvent
import spray.http.HttpResponse
import viscel.store.{Collection, Coin}

import scala.util.Try

object Deeds {
	val uiCollection = new ImperativeEvent[Collection]()
	val responses = new ImperativeEvent[Try[HttpResponse]]()
	val jobResult = new ImperativeEvent[List[ErrorMessage]]()

	val sessionDownloads = responses.count()
	val sessionUiRequests = uiCollection.count()
}
