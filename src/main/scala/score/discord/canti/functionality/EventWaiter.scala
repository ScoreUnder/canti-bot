package score.discord.canti.functionality

import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import score.discord.canti.util.APIHelper

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class EventWaiter extends EventListener {
  type Instant = Long // nanoTime
  type EventHandler = PartialFunction[GenericEvent, Unit]

  case class ExpiringEvent(expiry: Instant = System.nanoTime() + 10.minutes.toNanos, handler: EventHandler) {
    def expired: Boolean = expiry <= System.nanoTime()
  }

  private[this] val queuedEvents = mutable.ArrayBuffer.empty[ExpiringEvent]

  def queue(handler: EventHandler): Unit = {
    queuedEvents.synchronized {
      queuedEvents += ExpiringEvent(handler = handler)
    }
  }

  def cleanupOneEvent(): Unit = {
    queuedEvents.synchronized {
      if (queuedEvents.nonEmpty && queuedEvents(0).expired) {
        queuedEvents.remove(index = 0)
      }
    }
  }

  override def onEvent(event: GenericEvent): Unit = {
    if (queuedEvents.nonEmpty) {
      val activeHandlers = queuedEvents.synchronized {
        cleanupOneEvent()
        val activeHandlers = queuedEvents.filter(ev => ev.handler.isDefinedAt(event)).toSet
        queuedEvents.filterInPlace(ev => !activeHandlers.contains(ev))
        activeHandlers
      }

      for (ev@ExpiringEvent(_, handler) <- activeHandlers if !ev.expired) {
        Future {
          handler(event)
        }.failed.foreach(APIHelper.failure("processing delayed event"))
      }
    }
  }
}
