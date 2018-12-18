package score.discord.generalbot.jdamocks

import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture, TimeUnit}
import java.util.function.Consumer

import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.requests.RequestFuture
import net.dv8tion.jda.core.requests.restaction.MessageAction
import score.discord.generalbot.MyUnsafe

object FakeMessageAction {
  def apply(message: Message): MessageAction = {
    val fake = MyUnsafe.theUnsafe.allocateInstance(classOf[FakeMessageAction]).asInstanceOf[FakeMessageAction]
    fake.message = message
    fake
  }
}

private class FakeMessageAction extends MessageAction(null, null, null) {
  var message: Message = _

  override def queue(): Unit = {}

  override def queue(success: Consumer[Message]): Unit = success.accept(message)

  override def queue(success: Consumer[Message], failure: Consumer[Throwable]): Unit = success.accept(message)

  override def submit(): RequestFuture[Message] = ???

  override def submit(shouldQueue: Boolean): RequestFuture[Message] = ???

  override def complete(): Message = message

  override def complete(shouldQueue: Boolean): Message = message

  override def submitAfter(delay: Long, unit: TimeUnit): ScheduledFuture[Message] = ???

  override def submitAfter(delay: Long, unit: TimeUnit, executor: ScheduledExecutorService): ScheduledFuture[Message] = ???

  override def completeAfter(delay: Long, unit: TimeUnit): Message = ???

  override def queueAfter(delay: Long, unit: TimeUnit): ScheduledFuture[_] = ???

  override def queueAfter(delay: Long, unit: TimeUnit, success: Consumer[Message]): ScheduledFuture[_] = ???

  override def queueAfter(delay: Long, unit: TimeUnit, success: Consumer[Message], failure: Consumer[Throwable]): ScheduledFuture[_] = ???

  override def queueAfter(delay: Long, unit: TimeUnit, executor: ScheduledExecutorService): ScheduledFuture[_] = ???

  override def queueAfter(delay: Long, unit: TimeUnit, success: Consumer[Message], executor: ScheduledExecutorService): ScheduledFuture[_] = ???

  override def queueAfter(delay: Long, unit: TimeUnit, success: Consumer[Message], failure: Consumer[Throwable], executor: ScheduledExecutorService): ScheduledFuture[_] = ???
}
