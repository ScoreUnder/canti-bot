package score.discord.generalbot.wrappers

import java.util.concurrent.{Callable, ScheduledExecutorService, TimeUnit}

import scala.concurrent.duration.Duration

class Scheduler(me: ScheduledExecutorService) {
  def schedule[T](delay: Duration)(f: => T) =
    me.schedule((() => f): Callable[T], delay.length, delay.unit)

  def schedule[T](initialDelay: Duration, delay: Duration)(f: => Unit) =
    me.scheduleWithFixedDelay(() => f, initialDelay.toNanos, delay.toNanos, TimeUnit.NANOSECONDS)

  def scheduleAtRate[T](initialDelay: Duration, delay: Duration)(f: => Unit) =
    me.scheduleAtFixedRate(() => f, initialDelay.toNanos, delay.toNanos, TimeUnit.NANOSECONDS)

  def submit[T](f: => T) = me.submit(() => f)

  def isShutdown = me.isShutdown
}
