package viscel

import viscel.scribe.Narrator

object Deeds {
	var narratorHint: (Narrator, Boolean) => Unit = (n, b) => ()
}
