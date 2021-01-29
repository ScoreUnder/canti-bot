package score.discord.canti.wrappers

import java.util.concurrent.{Callable, ScheduledExecutorService, ScheduledFuture, TimeUnit}

import scala.concurrent.duration.Duration

class Scheduler(me: ScheduledExecutorService) {
  def asJava: ScheduledExecutorService = me

  def schedule[T](delay: Duration)(f: => T): ScheduledFuture[T] =
    me.schedule((() => f): Callable[T], delay.length, delay.unit)

  def schedule[T](initialDelay: Duration, delay: Duration)(f: => Unit): ScheduledFuture[_] =
    me.scheduleWithFixedDelay(() => f, initialDelay.toNanos, delay.toNanos, TimeUnit.NANOSECONDS)
}
