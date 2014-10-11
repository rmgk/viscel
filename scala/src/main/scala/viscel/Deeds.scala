package viscel

import rescala.events.ImperativeEvent
import spray.http.HttpResponse
import viscel.store.Coin

import scala.util.Try

object Deeds {
	val uiCoin = new ImperativeEvent[Coin]()
	val responses = new ImperativeEvent[Try[HttpResponse]]()
	val jobResult = new ImperativeEvent[crawler.Result[Nothing]]()

	val sessionDownloads = responses.count()
	val sessionUiRequests = uiCoin.count()
}
