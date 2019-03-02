package de.rmgk

import java.nio.file.Paths
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import viscel.Services
import viscel.server.ContentLoader
import viscel.shared.Vid
import viscel.store.{Book, ReadableContent, ScribeDataRow}


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

  var services: Services = _
  var vid     : Vid      = _

  var loadedContents: (String, Seq[ScribeDataRow]) = _

  var book: Book = _

  var pages: List[ReadableContent] = _

  @Setup
  def setup(): Unit = {
    services = new Services(Paths.get(dbpath), Paths.get("./blobs/"), "localhost", 2358)
    vid = Vid.from(bookId)
    loadedContents = services.rowStore.load(vid)
    book = Book.fromEntries(vid, loadedContents._1, loadedContents._2)
    pages = ContentLoader.linearizedPages(book)
  }

  @Benchmark
  def loadContents(bh: Blackhole) = {
    services.rowStore.load(vid)
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
