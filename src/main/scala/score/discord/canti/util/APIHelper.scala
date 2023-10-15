package score.discord.canti.util

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.exceptions.{
  ErrorResponseException, InsufficientPermissionException, PermissionException
}
import net.dv8tion.jda.api.requests.{ErrorResponse, RestAction}
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.Conversions.{richMessage, richMessageChannel}
import score.discord.canti.wrappers.jda.MessageConversions.{*, given}
import score.discord.canti.wrappers.jda.MessageReceiver
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Try
import scala.util.chaining.*

/** Miscellaneous functions useful when dealing with JDA's API calls */
object APIHelper:
  private val logger = loggerOf[this.type]

  /** Curried function to report an exception to the console.
    *
    * @param whatFailed
    *   what you were doing to cause the exception, described for the bot owner
    * @param exception
    *   the exception to print
    */
  def failure(whatFailed: String)(exception: Throwable): Unit =
    logger.error(s"API call failed when $whatFailed", exception)

  private def describeFailure(whatFailed: String, exception: Throwable): String =
    exception match
      case e: InsufficientPermissionException if e.getChannelId != 0 =>
        s"Error when $whatFailed: I don't have permission for that. Missing `${e.getPermission.nn.getName}` on <#${e.getChannelId}>."
      case e: PermissionException =>
        s"Error when $whatFailed: I don't have permission for that. Missing `${e.getPermission.nn.getName}`."
      case Error(x) => s"Error when $whatFailed: ${x.getMeaning}"
      case _        => s"Unknown error occurred when $whatFailed"

  /** Similar to [[APIHelper#failure]], but also sends an "unknown error" message in chat.
    *
    * @param whatFailed
    *   what you were doing to cause the exception, described for the users and bot owner
    * @param receiver
    *   the receiver to accept the error message
    * @param exception
    *   the exception to print
    */
  def loudFailure(whatFailed: String, receiver: MessageReceiver)(exception: Throwable): Unit =
    failure(whatFailed)(exception)
    receiver.sendMessage(
      BotMessages.error(describeFailure(whatFailed, exception)): MessageCreateFromX
    )

  /** Tries to run apiCall, then queues the result if successful.
    *
    * @param apiCall
    *   API call to queue, by name (exceptions are caught)
    * @param onFail
    *   function to run on failure
    * @tparam T
    *   type of object returned by API call
    * @return
    *   Future corresponding to the success/failure of the API call
    */
  def tryRequest[T](apiCall: => RestAction[? <: T], onFail: Throwable => Unit): Future[T] =
    tryRequest(apiCall).tap(_.failed.foreach(onFail))

  /** Tries to run apiCall, then queues the result if successful.
    *
    * @param apiCall
    *   API call to queue, by name (exceptions are caught)
    * @tparam T
    *   type of object returned by API call
    * @return
    *   Future corresponding to the success/failure of the API call
    */
  def tryRequest[T](apiCall: => RestAction[? <: T]): Future[T] =
    Try(apiCall).fold(Future.failed, _.queueFuture())

  object Error:
    def unapply(arg: ErrorResponseException): Option[ErrorResponse] = arg.getErrorResponse.?
end APIHelper
