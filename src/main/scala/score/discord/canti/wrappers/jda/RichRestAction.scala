package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.requests.RestAction

import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}

object RichRestAction:
  extension [T](orig: RestAction[T])
    /** Queue this RestAction up for execution through JDA's queue method, wrapping the result in a
      * Future.
      *
      * Caveat: Casts away null unsafely
      *
      * @return
      *   the Future result of the RestAction
      */
    def queueFuture(): Future[T] =
      val promise = Promise[T]()
      orig.queue(
        { (x: T | Null) =>
          promise.success(x.asInstanceOf[T])
          ()
        },
        { (x: Throwable | Null) =>
          if x == null then
            promise.failure(NullPointerException("Got null exception from RestAction"))
          else promise.failure(x)
          ()
        }
      )
      promise.future
