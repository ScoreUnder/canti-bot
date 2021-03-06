package score.discord.canti.command

import cps.*
import cps.monads.FutureAsyncMonad
import net.dv8tion.jda.api.entities.Message
import score.discord.canti.Furigana
import score.discord.canti.collections.ReplyCache
import score.discord.canti.command.FuriganaCommand.{FURI_PATTERN, sendFuriMessage}
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.{APIHelper, BotMessages, CommandHelper}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.RichMessage.!
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichSnowflake.id

import java.util.Collections
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.chaining.*

class FuriganaCommand(using MessageOwnership, ReplyCache) extends Command.Anyone:
  override def name = "furigana"

  override def aliases = List("furi", "fg", "f")

  override def description = "Render text with furigana as an image"

  override def longDescription(invocation: String): String =
    s"""Mix text and furigana:
       |`$invocation {郵便局:ゆうびんきょく}に{行:い}きました`
       |This will then be rendered into an image, with the furigana text on top of the corresponding kanji.
    """.stripMargin

  def parseInput(args: String): Seq[(String, String)] =
    FURI_PATTERN
      .findAllMatchIn(args)
      .flatMap { m =>
        val other: String | Null = m.group("other")
        if other == null then Seq((m.group("left"), m.group("right")))
        else other.splitnn("\n", -1).flatMap(line => Seq(("\n", ""), (line, ""))).tail
      }
      .filter(t => !t._1.isEmpty || !t._2.isEmpty)
      .toSeq

  override def execute(message: Message, args: String): Unit =
    if args.isEmpty then
      message ! BotMessages.error("Please provide the text to render as part of the command.")
      return

    async {
      message.getChannel.sendTyping().queue()

      val commandHelper = CommandHelper(message)

      val (origWithoutFuri, furiText) =
        val orig = parseInput(args)
        (
          orig.map(_._1).mkString,
          orig.map(t =>
            (commandHelper.mentionsToPlaintext(t._1), commandHelper.mentionsToPlaintext(t._2))
          )
        )

      await(sendFuriMessage(replyingTo = message, furigana = furiText, plain = origWithoutFuri))
    }.failed foreach APIHelper.loudFailure("rendering furigana", message)

object FuriganaCommand:
  private val FURI_PATTERN = raw"[｛{](?<left>[^：:]*)[：:](?<right>[^｝}]*)[｝}]|(?<other>[^{｛]+)".r

  def sendFuriMessage(
    replyingTo: Message,
    furigana: Iterable[(String, String)],
    plain: String
  )(using messageOwnership: MessageOwnership, replyCache: ReplyCache): Future[Message] =
    replyingTo
      .reply(Furigana.renderPNG(furigana), "furigana.png")
      .mentionRepliedUser(false)
      .append(plain.take(2000))
      .allowedMentions(Collections.emptySet)
      .queueFuture()
      .tap(_.foreach { newMsg =>
        messageOwnership(newMsg) = replyingTo.getAuthor
        replyCache += replyingTo.id -> newMsg.id
      })
