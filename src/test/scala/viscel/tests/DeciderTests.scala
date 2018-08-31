package viscel.tests

import org.scalatest.FreeSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import viscel.crawl.{CrawlTask, Decider}
import viscel.tests.DataGenerators._

class DeciderTests extends FreeSpec with GeneratorDrivenPropertyChecks {

  val empty = Decider()

  def allDecisions(decider: Decider, acc: List[CrawlTask] = Nil): (Decider, List[CrawlTask]) = {
    decider.decide() match {
      case None => (decider, acc)
      case Some((decision, next)) => allDecisions(next, decision :: acc)
    }
  }

  "empty" - {
    "decide" in { assert(empty.decide() === None ) }
    "add" in forAll { req: CrawlTask =>
      val Some((decision, next)) = empty.addTasks(List(req)).decide()
      assert (decision === req)
      assert(next.decide() === None)
    }
    "add all" in forAll { requests: List[CrawlTask] =>
      val (decider, decisions) = allDecisions(empty.addTasks(requests))
      assert(requests.toSet === decisions.toSet)
      assert(decider.decide() === None)
      assert(decider.imageDecisions === requests.count(_.isInstanceOf[CrawlTask.Image]))
    }
  }

}
