package visceljs.connection


import loci.registry.Registry
import loci.transmitter.RemoteRef
import rescala.default._

class RegistryState(registry: Registry) {
  lazy val remotes: Signal[Map[RemoteRef, RemoteRefState]] = {
    val remotesSig = Var(registry.remotes.map(r => r -> new RemoteRefState(r)).toMap)
    registry.remoteLeft.foreach(r => remotesSig.transform(_ - r))
    registry.remoteJoined.foreach(r => remotesSig.transform(_.updated(r, new RemoteRefState(r))))
    remotesSig
  }
}

class RemoteRefState(remoteRef: RemoteRef) {
  val connected: Signal[Boolean] = {
    Events.fromCallback[Boolean](cb => remoteRef.disconnected.foreach(_ => cb(false)))
          .event.latest(remoteRef.connected)
  }
}
