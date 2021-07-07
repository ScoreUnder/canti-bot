package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.requests.RestAction

import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}

object RichRestAction:
  extension [T](orig: RestAction[T])
    /** Queue this RestAction up for execution through JDA's queue method, wrapping the result in a
      * Future.
      *
      * @return
      *   the Future result of the RestAction
      */
    def queueFuture(): Future[T] =
      val promise = Promise[T]()
      orig.queue(promise.success(_), promise.failure(_))
      promise.future
