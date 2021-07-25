package score.discord.canti.command

import cps.*
import score.discord.canti.util.FutureAsyncMonadButGood
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import score.discord.canti.Furigana
import score.discord.canti.collections.ReplyCache
import score.discord.canti.command.FuriganaCommand.*
import score.discord.canti.command.api.{ArgSpec, ArgType, CommandInvocation, CommandPermissions}
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.{APIHelper, BotMessages, CommandHelper}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.Scheduler
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.{OutgoingMessage, RetrievableMessage}
import score.discord.canti.wrappers.jda.RichMessage.!
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichSnowflake.id

import java.util.Collections
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.chaining.*

class FuriganaCommand(using MessageOwnership, ReplyCache, Scheduler) extends GenericCommand:
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

  private val dummyMessage = MessageBuilder("dummy").build

  override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
    async {
      ctx.invoker.replyLater(false)

      val commandHelper = CommandHelper(ctx.invoker.originatingMessage.getOrElse(dummyMessage))

      val (origWithoutFuri, furiText) =
        val orig = parseInput(ctx.args(furiTextArg))
        (
          orig.map(_._1).mkString,
          orig.map(t =>
            (commandHelper.mentionsToPlaintext(t._1), commandHelper.mentionsToPlaintext(t._2))
          )
        )

      await(ctx.invoker.reply(makeFuriMessage(furigana = furiText, plain = origWithoutFuri)))
    }

  override def permissions = CommandPermissions.Anyone

  private val furiTextArg =
    ArgSpec("furiText", "The text to render as furigana", ArgType.GreedyString)

  override val argSpec = List(furiTextArg)
end FuriganaCommand

object FuriganaCommand:
  private val FURI_PATTERN = raw"[｛{](?<left>[^：:]*)[：:](?<right>[^｝}]*)[｝}]|(?<other>[^{｛]+)".r

  def makeFuriMessage(furigana: Iterable[(String, String)], plain: String): OutgoingMessage =
    Furigana
      .renderPNG(furigana)
      .fold {
        OutgoingMessage(BotMessages.error("No characters were visible in the output").toMessage)
      } { pngData =>
        OutgoingMessage(plain.take(2000).toMessage, files = List("furigana.png" -> pngData))
      }
