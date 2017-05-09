package score.discord.generalbot.util

import net.dv8tion.jda.core.entities.MessageChannel
import score.discord.generalbot.wrappers.jda.Conversions._

object APIHelper {
  /** Curried function to report an exception to the console.
    *
    * @param whatFailed what you were doing to cause the exception, described for the bot owner
    * @param exception  the exception to print
    */
  def failure(whatFailed: String)(exception: Throwable) {
    System.err.println(s"API call failed when $whatFailed")
    exception.printStackTrace()
  }

  /** Similar to failure, but also sends an "unknown error" message in chat.
    *
    * @see failure(String)(Throwable)
    * @param whatFailed what you were doing to cause the exception, described for the users and bot owner
    * @param channel    the channel to send the "unknown error" message to
    * @param exception  the exception to print
    */
  def loudFailure(whatFailed: String, channel: MessageChannel)(exception: Throwable) {
    failure(whatFailed)(exception)
    channel ! BotMessages.error(s"Unknown error occurred when $whatFailed")
  }
}
