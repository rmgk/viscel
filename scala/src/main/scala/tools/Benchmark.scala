// package viscel.tools

// import org.scalameter.api._
// import scala.concurrent.ExecutionContext.Implicits.global
// import scala.concurrent.future
// import viscel.store._

// object SimpleBenchmark extends PerformanceTest.Quickbenchmark {

// 	val un = UserNode("ragnar").get

// 	val cols = Gen.enumeration("c")(Neo.txs { Collections.list.toIndexedSeq }: _*)

// 	measure method "bookmarks" config (
// 		exec.benchRuns -> 1) in {
// 			using(cols) in { un.getBookmark(_) }
// 		}

// 	measure method "bookmarks no tx" config (
// 		exec.benchRuns -> 1) in {
// 			using(cols) in { un.getBookmark(_) }
// 		}

// 	measure method "collection get first by number" config (
// 		exec.benchRuns -> 1) in {
// 			using(cols) in { _(1) }
// 		}

// }
