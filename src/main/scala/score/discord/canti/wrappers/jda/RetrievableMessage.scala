package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.InteractionHook
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture

import scala.concurrent.Future

trait RetrievableMessage:
  def retrieve(): Future[Message]

private class RetrievedMessage(message: Message) extends RetrievableMessage:
  override def retrieve() = Future.successful(message)

private class InteractionHookMessage(origin: InteractionHook) extends RetrievableMessage:
  override def retrieve() = origin.retrieveOriginal.nn.queueFuture()

object RetrievableMessage:
  def apply(message: Message): RetrievedMessage = RetrievedMessage(message)
  def apply(origin: InteractionHook): InteractionHookMessage = InteractionHookMessage(origin)
