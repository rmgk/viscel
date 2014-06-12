package viscel.core

import com.typesafe.scalalogging.slf4j.StrictLogging
import spray.http.Uri
import viscel._
import viscel.store._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

trait ArchiveRunner extends NetworkPrimitives with StrictLogging {

	def core: Core
	def collection: CollectionNode

	def chapterRunner(cn: ChapterNode, lc: LinkedChapter, fb: Uri => Boolean) = new ChapterRunner() {
		def chapterNode = cn
		def forbidden = fb
		def chapter = lc
		def pageRunner = //new PlaceholderPageRunner() {}.apply(_)
			new FullPageRunner() {
				def iopipe = ArchiveRunner.this.iopipe
				def wrapPage = core.wrapPage
			}
	}

	def getChapter(lc: LinkedChapter, cn: ChapterNode, fb: Uri => Boolean): Future[Unit] = {
		chapterRunner(cn, lc, fb).update()
			.recoverWith {
				case e: NormalStatus =>
					logger.info(s"chapter ${cn.name} finished normally: ${e.getMessage}")
					Future.successful(())
			}
	}

	def getForbidden(chapters: Seq[LinkedChapter]): Uri => Boolean =
		collection.children.flatMap { _.children }.map { en => Uri(en[String]("origin")) }.toSet ++
			chapters.map { _.first.loc }.toSet

	def getAllChapters(cl: List[LinkedChapter]): Future[Unit] = cl match {
		case List() => Future.successful(())
		case c :: tcl =>
			logger.info(s"create chapter ${c.name} for ${collection.id}")
			val cnode = ChapterNode.create(c.name, c.props.toSeq: _*)
			collection.append(cnode)
			getChapter(c, cnode, getForbidden(tcl))
				.flatMap { _ => getAllChapters(tcl) }
	}

	def updateChapters(chapters: Seq[LinkedChapter]): Future[Unit] = {
		def matchChapter(chapter: LinkedChapter, cn: ChapterNode): Boolean = cn.name == chapter.name

		val dbChapters = collection.children.sortBy { _.position }
		if (dbChapters.size > chapters.size) return Future.failed(FailRun("collection knows more chapters than found"))

		val chapterPairs = chapters.zip(dbChapters)
		val mismatch = chapterPairs.find { case (lc, cn) => !matchChapter(lc, cn) }
		if (mismatch != None) return Future.failed(FailRun(s"mismatching chapters (${mismatch.get._1.name}) (${mismatch.get._2.name})"))

		val newChapters = chapters.drop(chapterPairs.size)

		chapterPairs.lastOption.map {
			case (lc, cn) => getChapter(lc, cn, getForbidden(newChapters))
		}.getOrElse(Future.successful(()))
			.flatMap { _ => getAllChapters(newChapters.toList) }

	}

	def getChapters(archive: ArchiveDescription, chapters: Seq[ChapterDescription] = Seq()): Future[Seq[ChapterDescription]] = {
		val fullArchive = archive match {
			case fa: FullArchive => Future.successful(fa)
			case ap: ArchivePointer =>
				document(ap.loc)
					.flatMap { core.wrapArchive(_).toFuture }
		}
		fullArchive.flatMap { fa =>
			val allChapters = chapters ++ fa.chapters
			fa.next match {
				case Some(archive) => getChapters(archive, allChapters)
				case None => Future.successful(allChapters)
			}
		}
	}

	def update(): Future[Unit] = getChapters(core.archive).map { _.asInstanceOf[Seq[LinkedChapter]] }.flatMap { updateChapters }
}

