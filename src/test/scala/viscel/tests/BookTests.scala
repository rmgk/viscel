package viscel.tests

import org.scalacheck.Arbitrary
import org.scalatest.FreeSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import viscel.shared.Vid
import viscel.store.{BlobData, Book, PageData, ScribeDataRow}
import viscel.tests.DataGenerators._

class BookTests extends FreeSpec with GeneratorDrivenPropertyChecks {

  val empty = Book(Vid.from("Test"), "Testbook")

  "empty" - {
    "add blob" in {
      val blob = Arbitrary.arbitrary[BlobData].sample.get
      val one = empty.addBlob(blob)
      assert(one.hasBlob(blob.ref))
      assert(one.allBlobs().toList === List(blob))

      assert(one.addBlob(blob) === one, "adding is idempotent")
    }
    "add page" in forAll { page: PageData =>
      val (one, count) = empty.addPage(page)
      assert(one.hasPage(page.ref))
      assert(one.allPages().toList === List(page))
      assert(count.isDefined)

      assert(one.addPage(page) === (one, None), "adding is idempotent")

    }
  }


  "loading" - {
    "from entries behaves as adding individually" in forAll { rows: List[ScribeDataRow] =>
      val load = Book.fromEntries(empty.id, empty.name, rows)
      val addAll = rows.foldLeft(empty) {
        case (b, blobData: BlobData) => b.addBlob(blobData)
        case (b, pageData: PageData) => b.addPage(pageData)._1
      }
      assert(load === addAll)
    }
    "adding pages, no count no changes" in forAll { rows: List[ScribeDataRow] =>
      val duplicated = scala.util.Random.shuffle(rows reverse_::: rows)
      duplicated.foldLeft(empty) {
        case (b, blobData: BlobData) => b.addBlob(blobData)
        case (b, pageData: PageData) =>
          val (next, count) = b.addPage(pageData)
          if (count.isEmpty) {
            assert(next === b)
          }
          next
      }

    }
  }




}
