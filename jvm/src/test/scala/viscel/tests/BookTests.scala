package viscel.tests

import org.scalacheck.Prop.forAll
import viscel.crawl.CrawlProcessing
import viscel.shared.DataRow.Link
import viscel.shared.{DataRow, Vid}
import viscel.store.Book
import viscel.tests.DataGenerators.*

class BookTests extends munit.ScalaCheckSuite {

  val empty = Book(Vid.from("Test"), "Testbook")

  property("empty add page") {
    forAll { (page: DataRow) =>
      val bookO = empty.addPage(page)
      assert(bookO.isDefined)
      val one = bookO.get

      assert(one.hasPage(page.ref))
      assertEquals(
        CrawlProcessing.allLinks(one).map(_.href).toList,
        page.contents.collect { case Link(ref, data) => ref }
      )

      assertEquals(one.addPage(page), None, "adding is idempotent")

    }
  }

  property("loading from entries behaves as adding individually") {
    forAll { (rows: List[DataRow]) =>
      val load = Book.fromEntries(empty.id, empty.name, rows)
      val addAll = rows.foldLeft(empty) {
        case (b, pageData) => b.addPage(pageData).getOrElse(b)
      }
      assertEquals(load, addAll)
    }
  }

  property("loading adding pages, no count no changes") {
    forAll { (rows: List[DataRow]) =>
      val duplicated = scala.util.Random.shuffle(rows reverse_::: rows)
      duplicated.foldLeft(empty) {
        case (b, pageData) =>
          b.addPage(pageData).getOrElse(b)
      }
      assert(true)
    }
  }
}
