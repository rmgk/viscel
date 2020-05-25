package viscel.tests

import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import viscel.shared.{DataRow, Vid}
import viscel.store.Book
import viscel.tests.DataGenerators._

class BookTests extends AnyFreeSpec with ScalaCheckDrivenPropertyChecks {

  val empty = Book(Vid.from("Test"), "Testbook")

  "empty" - {
    "add page" in forAll { page: DataRow =>
      val bookO = empty.addPage(page)
      assert(bookO.isDefined)
      val one = bookO.get

      assert(one.hasPage(page.ref))
      assert(one.allLinks.map(_.href).toList === page.contents)

      assert(one.addPage(page) === None, "adding is idempotent")

    }
  }


  "loading" - {
    "from entries behaves as adding individually" in forAll { rows: List[DataRow] =>
      val load = Book.fromEntries(empty.id, empty.name, rows)
      val addAll = rows.foldLeft(empty) {
        case (b, pageData) => b.addPage(pageData).getOrElse(b)
      }
      assert(load === addAll)
    }
    "adding pages, no count no changes" in forAll { rows: List[DataRow] =>
      val duplicated = scala.util.Random.shuffle(rows reverse_::: rows)
      duplicated.foldLeft(empty) { case (b, pageData) =>
        b.addPage(pageData).getOrElse(b)
      }

    }
  }


}
