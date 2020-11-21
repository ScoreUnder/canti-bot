package score.discord.generalbot.util

import net.dv8tion.jda.api.entities.{Message, MessageChannel}
import net.dv8tion.jda.api.exceptions.{ErrorResponseException, PermissionException}
import net.dv8tion.jda.api.requests.{ErrorResponse, RestAction}
import score.discord.generalbot.collections.ReplyCache
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import scala.util.chaining._

/** Miscellaneous functions useful when dealing with JDA's API calls */
object APIHelper {
  /** Curried function to report an exception to the console.
    *
    * @param whatFailed what you were doing to cause the exception, described for the bot owner
    * @param exception  the exception to print
    */
  def failure(whatFailed: String)(exception: Throwable): Unit = {
    System.err.println(s"API call failed when $whatFailed")
    exception.printStackTrace()
  }

  /** Similar to [[APIHelper#failure]], but also sends an "unknown error" message in chat.
    *
    * @param whatFailed what you were doing to cause the exception, described for the users and bot owner
    * @param channel    the channel to send the "unknown error" message to
    * @param exception  the exception to print
    */
  def loudFailure(whatFailed: String, channel: MessageChannel)(exception: Throwable): Unit = {
    failure(whatFailed)(exception)
    channel ! BotMessages.error(
      exception match {
        case _: PermissionException => s"Error when $whatFailed: I don't have permission for that"
        case Error(x) => s"Error when $whatFailed: ${x.getMeaning}"
        case _ => s"Unknown error occurred when $whatFailed"
      })
  }

  /** Similar to [[APIHelper#failure]], but also sends an "unknown error" message in chat.
    *
    * @param whatFailed what you were doing to cause the exception, described for the users and bot owner
    * @param message    the message to reply with "unknown error" to
    * @param exception  the exception to print
    */
  def loudFailure(whatFailed: String, message: Message)(exception: Throwable)(implicit messageOwnership: MessageOwnership, replyCache: ReplyCache): Unit = {
    failure(whatFailed)(exception)
    message ! BotMessages.error(
      exception match {
        case _: PermissionException => s"Error when $whatFailed: I don't have permission for that"
        case Error(x) => s"Error when $whatFailed: ${x.getMeaning}"
        case _ => s"Unknown error occurred when $whatFailed"
      })
  }

  /** Similar to loudFailure with a MessageChannel, but uses
    * [[APIHelper#failure]] if no MessageChannel is provided.
    *
    * @param whatFailed   what you were doing to cause the exception, described for the users and bot owner
    * @param channelMaybe Some channel to send the "unknown error" message to, or None
    * @param exception    the exception to print
    */
  def loudFailure(whatFailed: String, channelMaybe: Option[MessageChannel])(exception: Throwable): Unit = {
    channelMaybe match {
      case Some(channel) => loudFailure(whatFailed, channel)(exception)
      case None => failure(whatFailed)(exception)
    }
  }

  /** Tries to run apiCall, then queues the result if successful.
    *
    * @param apiCall API call to queue, by name (exceptions are caught)
    * @param onFail  function to run on failure
    * @tparam T type of object returned by API call
    * @return Future corresponding to the success/failure of the API call
    */
  def tryRequest[T](apiCall: => RestAction[T], onFail: Throwable => Unit): Future[T] =
    tryRequest(apiCall).tap(_.failed.foreach(onFail))

  /** Tries to run apiCall, then queues the result if successful.
    *
    * @param apiCall API call to queue, by name (exceptions are caught)
    * @tparam T type of object returned by API call
    * @return Future corresponding to the success/failure of the API call
    */
  def tryRequest[T](apiCall: => RestAction[T]): Future[T] =
    Try(apiCall).fold(Future.failed, _.queueFuture())

  object Error {
    def unapply(arg: ErrorResponseException): Option[ErrorResponse] = Option(arg.getErrorResponse)
  }
}
