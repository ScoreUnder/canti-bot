package score.discord.canti.util

import net.dv8tion.jda.api.entities.{Message, MessageChannel}
import net.dv8tion.jda.api.exceptions.{ErrorResponseException, PermissionException}
import net.dv8tion.jda.api.requests.{ErrorResponse, RestAction}
import org.slf4j.LoggerFactory
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.Conversions.{richMessage, richMessageChannel}
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Try
import scala.util.chaining.*

/** Miscellaneous functions useful when dealing with JDA's API calls */
object APIHelper:
  private val logger = LoggerFactory.getLogger(getClass).nn

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
      case e: PermissionException =>
        s"Error when $whatFailed: I don't have permission for that. Missing `${e.getPermission.nn.getName}`."
      case Error(x) => s"Error when $whatFailed: ${x.getMeaning}"
      case _        => s"Unknown error occurred when $whatFailed"

  /** Similar to [[APIHelper#failure]], but also sends an "unknown error" message in chat.
    *
    * @param whatFailed
    *   what you were doing to cause the exception, described for the users and bot owner
    * @param reply
    *   a function which accepts the "unknown error" message to queue as a reply somewhere
    * @param exception
    *   the exception to print
    */
  def loudFailure(whatFailed: String, reply: Message => Unit)(exception: Throwable): Unit =
    failure(whatFailed)(exception)
    reply(BotMessages.error(describeFailure(whatFailed, exception)).toMessage)

  /** Similar to [[APIHelper#failure]], but also sends an "unknown error" message in chat.
    *
    * @param whatFailed
    *   what you were doing to cause the exception, described for the users and bot owner
    * @param channel
    *   the channel to send the "unknown error" message to
    * @param exception
    *   the exception to print
    */
  def loudFailure(whatFailed: String, channel: MessageChannel)(exception: Throwable): Unit =
    loudFailure(whatFailed, channel ! _)(exception)

  /** Similar to [[APIHelper#failure]], but also sends an "unknown error" message in chat.
    *
    * @param whatFailed
    *   what you were doing to cause the exception, described for the users and bot owner
    * @param message
    *   the message to reply with "unknown error" to
    * @param exception
    *   the exception to print
    */
  def loudFailure(whatFailed: String, message: Message)(
    exception: Throwable
  )(using MessageOwnership, ReplyCache): Unit =
    loudFailure(whatFailed, message ! _)(exception)

  /** Similar to loudFailure with a MessageChannel, but uses [[APIHelper#failure]] if no
    * MessageChannel is provided.
    *
    * @param whatFailed
    *   what you were doing to cause the exception, described for the users and bot owner
    * @param channelMaybe
    *   Some channel to send the "unknown error" message to, or None
    * @param exception
    *   the exception to print
    */
  def loudFailure(whatFailed: String, channelMaybe: Option[MessageChannel])(
    exception: Throwable
  ): Unit =
    channelMaybe match
      case Some(channel) => loudFailure(whatFailed, channel)(exception)
      case None          => failure(whatFailed)(exception)

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
