package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.api.requests.RestAction

import scala.concurrent.{Future, Promise}

class RichRestAction[T](val orig: RestAction[T]) extends AnyVal {
  /** Queue this RestAction up for execution through JDA's queue method, wrapping the result in a Future.
    *
    * @return the Future result of the RestAction
    */
  def queueFuture(): Future[T] = {
    val promise = Promise[T]()
    orig.queue(promise.success _, promise.failure _)
    promise.future
  }
}
