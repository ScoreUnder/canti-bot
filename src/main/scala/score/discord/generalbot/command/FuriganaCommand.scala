package score.discord.generalbot.command

import java.util.Collections

import net.dv8tion.jda.api.entities.Message
import score.discord.generalbot.Furigana
import score.discord.generalbot.collections.ReplyCache
import score.discord.generalbot.command.FuriganaCommand.{FURI_PATTERN, sendFuriMessage}
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.{APIHelper, BotMessages, CommandHelper}
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.chaining._

class FuriganaCommand(implicit messageOwnership: MessageOwnership, replyCache: ReplyCache) extends Command.Anyone {
  override def name = "furigana"

  override def aliases = List("furi", "fg", "f")

  override def description = "Render text with furigana as an image"

  override def longDescription(invocation: String): String =
    s"""Mix text and furigana:
       |`$invocation {郵便局:ゆうびんきょく}に{行:い}きました`
       |This will then be rendered into an image, with the furigana text on top of the corresponding kanji.
    """.stripMargin

  def parseInput(args: String): Seq[(String, String)] =
    FURI_PATTERN.findAllMatchIn(args)
      .flatMap { m =>
        m.group("other") match {
          case null => Seq((m.group("left"), m.group("right")))
          case other => other.split("\n", -1).flatMap(line => Seq(("\n", ""), (line, ""))).tail
        }
      }
      .filter(t => !t._1.isEmpty || !t._2.isEmpty)
      .toSeq

  override def execute(message: Message, args: String): Unit = {
    if (args.isEmpty) {
      message.!(BotMessages error "Please provide the text to render as part of the command.")
      return
    }

    async {
      message.getChannel.sendTyping().queue()

      val commandHelper = CommandHelper(message)

      val (origWithoutFuri, furiText) = {
        val orig = parseInput(args)
        (orig.map(_._1).mkString, orig.map(t => (
          commandHelper.mentionsToPlaintext(t._1),
          commandHelper.mentionsToPlaintext(t._2))))
      }

      await(sendFuriMessage(replyingTo = message, furigana = furiText, plain = origWithoutFuri))
    }.failed foreach APIHelper.loudFailure("rendering furigana", message)
  }
}

object FuriganaCommand {
  private val FURI_PATTERN = raw"[｛{](?<left>[^：:]*)[：:](?<right>[^｝}]*)[｝}]|(?<other>[^{｛]+)".r

  def sendFuriMessage(replyingTo: Message, furigana: Iterable[(String, String)], plain: String)
                     (implicit messageOwnership: MessageOwnership, replyCache: ReplyCache): Future[Message] = {
    replyingTo.reply(Furigana.renderPNG(furigana), "furigana.png")
      .mentionRepliedUser(false)
      .append(plain.take(2000))
      .allowedMentions(Collections.emptySet)
      .queueFuture()
      .tap(_.foreach { newMsg =>
        messageOwnership(newMsg) = replyingTo.getAuthor
        replyCache += replyingTo.id -> newMsg.id
      })
  }
}
