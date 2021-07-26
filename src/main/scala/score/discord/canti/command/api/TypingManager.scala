package score.discord.canti.command.api

import net.dv8tion.jda.api.entities.MessageChannel
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.wrappers.Scheduler
import score.discord.canti.wrappers.jda.MessageConversions.MessageFromX
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.language.postfixOps

class TypingManager(channel: MessageChannel):
  private var typingAttemptsLeft = 0

  private var typingFuture = Future.unit

  def sendTypingNotification()(using Scheduler): Future[Unit] =
    synchronized {
      typingAttemptsLeft = 10
      updateTypingNotification()
    }

  private def updateTypingNotification()(using Scheduler): Future[Unit] =
    val future =
      for
        _ <- typingFuture
        _ <- channel.sendTyping.queueFuture()
      yield
        summon[Scheduler].schedule(8 seconds) {
          TypingManager.this.synchronized {
            if typingAttemptsLeft > 0 then
              typingAttemptsLeft -= 1
              updateTypingNotification()
          }
        }
        ()
    typingFuture = future
    future

  def completeWith[T](f: => Future[T]): Future[T] =
    synchronized {
      val result =
        for
          _ <- typingFuture.recover { case _ => () }
          v <- f
        yield v
      typingAttemptsLeft = 0
      typingFuture = Future.unit
      result
    }
