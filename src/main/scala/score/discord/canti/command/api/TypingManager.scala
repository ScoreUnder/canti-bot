package score.discord.canti.command.api

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.wrappers.Scheduler
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
      // Set off a series of typing notifications until completeWith is called
      // or the typing notification limit is reached (~80 seconds)
      typingAttemptsLeft = 10
      updateTypingNotification()
    }

  private def updateTypingNotification()(using Scheduler): Future[Unit] =
    val future =
      for
        // Wait for last typing notification to be sent (if not already)
        _ <- typingFuture
        // ...then send a new one
        _ <- channel.sendTyping.nn.queueFuture()
      yield
        // Schedule a new typing notification to be sent in 8 seconds
        // (the typing notification lasts for 10 seconds)
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
          // Wait for the typing notification to be sent, but don't require
          // it to succeed. This ordering is because we likely want to dismiss
          // the typing notification with the result of the action, and doing
          // it out of order will re-summon the typing notification.
          _ <- typingFuture.recover { case _ => () }
          // ...then proceed with the actual action
          v <- f
        yield v
      typingAttemptsLeft = 0
      typingFuture = Future.unit
      result
    }
