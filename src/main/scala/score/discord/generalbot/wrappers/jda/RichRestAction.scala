package score.discord.generalbot.wrappers.jda

import net.dv8tion.jda.core.requests.RestAction

import scala.concurrent.Promise

class RichRestAction[T](val orig: RestAction[T]) extends AnyVal {
  def queueFuture() = {
    val promise = Promise[T]()
    orig.queue({ result => promise success result }, { result => promise failure result })
    promise.future
  }
}
