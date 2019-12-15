package viscel.tests

import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import viscel.crawl.Decider
import viscel.netzi.VRequest
import viscel.tests.DataGenerators._

class DeciderTests extends AnyFreeSpec with ScalaCheckDrivenPropertyChecks {

  val empty = Decider()

  def allDecisions(decider: Decider, acc: List[VRequest] = Nil): (Decider, List[VRequest]) = {
    decider.decide() match {
      case None => (decider, acc)
      case Some((decision, next)) => allDecisions(next, decision :: acc)
    }
  }

  "empty" - {
    "decide" in { assert(empty.decide() === None ) }
    "add" in forAll { req: VRequest =>
      val Some((decision, next)) = empty.addTasks(List(req)).decide()
      assert (decision === req)
      assert(next.decide() === None)
    }
    "add all" in forAll { requests: List[VRequest] =>
      val (decider, decisions) = allDecisions(empty.addTasks(requests))
      assert(requests.toSet === decisions.toSet)
      assert(decider.decide() === None)
      //assert(decider.imageDecisions === requests.count(_.isInstanceOf[CrawlTask.Image]))
    }
  }

}
