package viscel.tests

import viscel.selektiv.Queries
import viscel.shared.DataRow.{Blob, Chapter, Content}

class ReverseTests extends munit.ScalaCheckSuite {

  val exampleList = List[Content](
    Chapter("a"),
    Blob("1", "a"),
    Blob("2", "a"),
    Blob("3", "a"),
    Chapter("b"),
    Blob("1", "b"),
    Blob("2", "b"),
    Blob("3", "b"),
    Chapter("c"),
    Blob("1", "c"),
    Blob("2", "c"),
    Blob("3", "c"),
  )

  val reverseChapter = List[Content](
    Chapter("c"),
    Blob("1", "c"),
    Blob("2", "c"),
    Blob("3", "c"),
    Chapter("b"),
    Blob("1", "b"),
    Blob("2", "b"),
    Blob("3", "b"),
    Chapter("a"),
    Blob("1", "a"),
    Blob("2", "a"),
    Blob("3", "a"),
  )

  val reverseFull = List[Content](
    Chapter("c"),
    Blob("3", "c"),
    Blob("2", "c"),
    Blob("1", "c"),
    Chapter("b"),
    Blob("3", "b"),
    Blob("2", "b"),
    Blob("1", "b"),
    Chapter("a"),
    Blob("3", "a"),
    Blob("2", "a"),
    Blob("1", "a"),
  )

  test("reverse chapters") {
    assertEquals(Queries.chapterReverse(exampleList, reverseInner = false), reverseChapter)
  }
  test("reverse full") {
    assertEquals(Queries.chapterReverse(exampleList, reverseInner = true), reverseFull)
  }

}
