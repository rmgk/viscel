package viscel.crawler

import viscel.store.{ArchiveNode, CollectionNode}
import viscel.cores.Core

object Messages {

	case class Run(core: Core)

	case class Done(core: Core, timeout: Boolean = false, failed: Boolean = false)

	case class ArchiveHint(node: ArchiveNode)

	case class CollectionHint(node: CollectionNode)

}
