package viscel

import rescala.events.ImperativeEvent
import viscel.store.Coin

object Deeds {
	val uiCoin = new ImperativeEvent[Coin]()
}
