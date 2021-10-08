package rescala.extra.distributables

import loci.registry.{Binding, Registry}
import loci.transmitter.RemoteRef
import rescala.extra.lattices.Lattice
import rescala.default._
import viscel.shared.Log

import scala.concurrent.Future

object LociDist {

  def distribute[A: Lattice](
      signal: Signal[A],
      registry: Registry
  )(binding: Binding[A => Unit, A => Future[Unit]]) =
    distributePerRemote(_ => signal, registry)(binding)

  def distributePerRemote[A: Lattice](
      signalFun: RemoteRef => Signal[A],
      registry: Registry
  )(binding: Binding[A => Unit, A => Future[Unit]]): Unit = {

    Log.Server.info(s"starting new distribution for »${binding.name}«")

    registry.bindSbj(binding)((remoteRef: RemoteRef, newValue: A) => {
        val signal: Signal[A] = signalFun(remoteRef)
        val signalName           = signal.name.str
        //println(s"received value for $signalName: ${newValue.hashCode()}")
        scheduler.forceNewTransaction(signal) { admissionTicket =>
          admissionTicket.recordChange(new InitialChange {
            override val source = signal
            override def writeValue(b: source.Value, v: source.Value => Unit): Boolean = {
              val merged = b.map(Lattice[A].merge(_, newValue)).asInstanceOf[source.Value]
              if (merged != b) {
                v(merged)
                true
              } else false
            }
          })
        }
        Log.Server.info(s"update for $signalName complete")
      }
    )

    var observers = Map[RemoteRef, Observe]()

    def registerRemote(remoteRef: RemoteRef): Unit = {
      val signal: Signal[A] = signalFun(remoteRef)
      val signalName           = signal.name.str
      println(s"registering new remote $remoteRef for $signalName")
      val remoteUpdate: A => Future[Unit] = {
        Log.Server.info(s"calling lookup on »${binding.name}«")
        registry.lookup(binding, remoteRef)
      }
      observers += (remoteRef -> signal.observe { s =>
        //println(s"calling remote observer on $remoteRef for $signalName")
        if (remoteRef.connected) remoteUpdate(s)
        else observers(remoteRef).remove()
      })
    }

    registry.remotes.foreach(registerRemote)
    registry.remoteJoined.foreach(registerRemote)
    registry.remoteLeft.foreach { remoteRef =>
      println(s"removing remote $remoteRef")
      observers(remoteRef).remove()
    }
  }

}
