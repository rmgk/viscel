package viscel.store

import org.neo4j.graphdb.Node
import viscel.database.Ntx


trait NeoCodec[T] {
	def read(node: Node)(implicit ntx: Ntx): T
	def write(value: T)(implicit ntx: Ntx): Node
}

object NeoCodecs extends viscel.generated.NeoCodecs