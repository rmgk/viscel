package viscel.tests

import org.scalacheck.Arbitrary
import org.scalatest.FreeSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import viscel.shared.Vid
import viscel.store.v4.DataRow
import viscel.store.{BlobData, Book}
import viscel.tests.DataGenerators._

class BookTests extends FreeSpec with ScalaCheckDrivenPropertyChecks {

  val empty = Book(Vid.from("Test"), "Testbook")

  "empty" - {
    "add blob" in {
      val blob = Arbitrary.arbitrary[BlobData].sample.get
      val one = empty.addBlob(blob)
      assert(one.hasBlob(blob.ref))
      assert(one.allBlobs().toList === List(blob))

      assert(one.addBlob(blob) === one, "adding is idempotent")
    }
    "add page" in forAll { page: DataRow =>
      val (one, count) = empty.addPage(page)
      assert(one.hasPage(page.ref))
      assert(one.allLinks.map(_.link).toList === List(page))
      assert(count.isDefined)

      assert(one.addPage(page) === (one -> None), "adding is idempotent")

    }
  }


  "loading" - {
    "from entries behaves as adding individually" in forAll { rows: List[DataRow] =>
      val load = Book.fromEntries(empty.id, empty.name, rows)
      val addAll = rows.foldLeft(empty) {
        case (b, pageData) => b.addPage(pageData)._1
      }
      assert(load === addAll)
    }
    "adding pages, no count no changes" in forAll { rows: List[DataRow] =>
      val duplicated = scala.util.Random.shuffle(rows reverse_::: rows)
      duplicated.foldLeft(empty) { case (b, pageData) =>
        val (next, count) = b.addPage(pageData)
        if (count.isEmpty) {
          assert(next === b)
        }
        next
      }

    }
  }


}
