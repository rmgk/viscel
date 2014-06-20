package viscel.core

import viscel.store.{CollectionNode, ArchiveNode}

object Messages {
	case class Run(core: Core)
	case class Done(core: Core, timeout: Boolean = false, failed: Boolean = false)
	case class ArchiveHint(node: ArchiveNode)
	case class CollectionHint(node: CollectionNode)
}
