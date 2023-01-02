package viscel.tests

import org.scalacheck.Prop.forAll
import viscel.crawl.Decider
import viscel.netzi.VRequest
import viscel.tests.DataGenerators.*

class DeciderTests extends munit.ScalaCheckSuite {

  val empty = Decider()

  def allDecisions(decider: Decider, acc: List[VRequest] = Nil): (Decider, List[VRequest]) = {
    decider.decide() match {
      case None                   => (decider, acc)
      case Some((decision, next)) => allDecisions(next, decision :: acc)
    }
  }

  test("decide") {
    assertEquals(empty.decide(), None)
  }

  property("add") {
    forAll { (req: VRequest) =>
      val Some((decision, next)) = empty.addTasks(List(req)).decide(): @unchecked
      assertEquals(decision, req)
      assertEquals(next.decide(), None)
    }
  }

  property("add all") {
    forAll { (requests: List[VRequest]) =>
      val (decider, decisions) = allDecisions(empty.addTasks(requests))
      assertEquals(requests.toSet, decisions.toSet)
      assertEquals(decider.decide(), None)
      // assert(decider.imageDecisions === requests.count(_.isInstanceOf[CrawlTask.Image]))
    }
  }

}
