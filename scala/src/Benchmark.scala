// import org.scalameter.api._
// import scala.concurrent.ExecutionContext.Implicits.global
// import scala.concurrent.future
// import viscel.store._

// object SimpleBenchmark extends PerformanceTest.Quickbenchmark {

// 	val un = UserNode("ragnar").get

// 	val cols = Gen.enumeration("c")(CollectionNode.list: _*)

// 	measure method "bookmarks" config (
// 		exec.benchRuns -> 1) in {
// 			using(cols) in { un.bookmark(_) }
// 		}

// 	measure method "bookmarks no tx" config (
// 		exec.benchRuns -> 1) in {
// 			using(cols) in { un.bookmarkntx(_) }
// 		}

// }
