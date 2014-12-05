package viscel

import org.scalactic.ErrorMessage
import rescala.Evt
import rescala.propagation.Engines.default
import spray.http.HttpResponse
import viscel.narration.Narrator
import viscel.store.Collection

import scala.util.Try

object Deeds {
	val narratorHint = Evt[Narrator]()
	val responses = Evt[Try[HttpResponse]]()
	val jobResult = Evt[List[ErrorMessage]]()

	val sessionDownloads = responses.count()
	val sessionUiRequests = narratorHint.count()
}
