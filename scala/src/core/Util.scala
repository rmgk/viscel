package viscel.core

import scala.util.Try

object Util {
	def validateSeq[A](xs: Seq[Try[A]]): Try[Seq[A]] =
		xs.find(_.isFailure) match {
			case Some(f) => f.asInstanceOf[Try[Seq[A]]]
			case None => Try { xs.map(_.get) }
		}
}
