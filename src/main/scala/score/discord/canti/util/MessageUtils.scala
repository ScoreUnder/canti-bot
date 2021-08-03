package score.discord.canti.util

import score.discord.canti.wrappers.NullWrappers.*

object MessageUtils:
  /** Sanitise a message such that all mentions become inactive, including mass-mentions such as
    * `@everyone`.
    *
    * @param message
    *   text to sanitise
    * @return
    *   sanitised text
    */
  def blockMentionsNaive(message: String): String =
    message.replacenn("@", "@\u200C")

  private val FORMAT_MATCHER = s"(?<!\\\\)([*_~`])".r

  /** Sanitise a message such that all formatting becomes plaintext when viewed by the official
    * Discord client.
    *
    * @param message
    *   text to sanitise
    * @return
    *   sanitised text
    */
  def escapeFormatting(message: String): String =
    FORMAT_MATCHER.replaceAllIn(message, "\\\\$1")

  /** Sanitise a message such that all formatting and mentions become plaintext when viewed by the
    * official Discord client.
    *
    * @param message
    *   text to sanitise
    * @return
    *   sanitised text
    */
  def sanitise(message: String): String =
    escapeFormatting(blockMentionsNaive(message))

  /** Sanitise a message such that it contains no way to break out of double-backtick (or
    * triple-backtick) code blocks.
    *
    * @param message
    *   text to sanitise
    * @return
    *   sanitised text
    */
  def sanitiseCode(message: String): String =
    message.replacenn("`", "`\u200C")

  /** Backtick-quote a string safely */
  def quote(s: String) =
    s"``${sanitiseCode(s)}``"
end MessageUtils
