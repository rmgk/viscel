package viscel.benchmarks

import java.nio.file.Paths
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import viscel.server.ContentLoader
import viscel.server.ContentLoader.LinearResult
import viscel.shared.{DataRow, Vid}
import viscel.store.Book
import viscel.store.v4.RowStoreV4


@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime, Mode.SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
class ContentLoading {

  @Param(Array(""))
  var bookId: String = _

  @Param(Array(""))
  var dbpath: String = _

  var services: RowStoreV4 = _
  var vid     : Vid        = _

  var loadedContents: (String, Seq[DataRow]) = _

  var book: Book = _

  var pages: LinearResult = _

  @Setup
  def setup(): Unit = {
    services = new RowStoreV4(Paths.get(dbpath))
    vid = Vid.from(bookId)
    loadedContents = services.load(vid)
    book = Book.fromEntries(vid, loadedContents._1, loadedContents._2)
    pages = ContentLoader.linearizedPages(book)
  }

  @Benchmark
  def loadContents(bh: Blackhole) = {
    services.load(vid)
  }

  @Benchmark
  def parseBook(bh: Blackhole) = {
    Book.fromEntries(vid, loadedContents._1, loadedContents._2)
  }

  @Benchmark
  def linearizeBook(bh: Blackhole) = {
    ContentLoader.linearizedPages(book)
  }

  @Benchmark
  def pagesToContents(bh: Blackhole) = {
    ContentLoader.pagesToContents(pages)
  }


}
