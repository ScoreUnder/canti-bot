package score.discord.canti.command

import cps.*
import cps.monads.FutureAsyncMonad
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.JDA
import score.discord.canti.collections.{MessageCache, ReplyCache}
import score.discord.canti.command.api.{ArgSpec, ArgType, CommandInvocation, CommandPermissions}
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.{APIHelper, BotMessages, CommandHelper}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.Scheduler
import score.discord.canti.wrappers.jda.RetrievableMessage
import score.discord.canti.wrappers.jda.RichMessage.!
import score.discord.canti.wrappers.jda.RichSnowflake.id

import java.io.{File, IOException}
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, TimeoutException, blocking}
import scala.io.Codec
import scala.language.implicitConversions

class ReadCommand(messageCache: MessageCache)(using Scheduler) extends GenericCommand:
  private val KAKASI_FURIGANA = "kakasi -s -f -ieuc -oeuc -JH".splitnn(" ")
  private val KAKASI_ROMAJI = "kakasi -s -ieuc -oeuc -Ja -Ka -Ha -Ea -ka -ja".splitnn(" ")
  private val DICT_FILE = File("extra_words")

  private given Codec = Codec("EUC-JP")
    .onMalformedInput(CodingErrorAction.REPLACE.nn)
    .onUnmappableCharacter(CodingErrorAction.REPLACE.nn)

  private val WHITESPACE = "\\s".r
  private val JAPANESE = "[\\p{InHiragana}\\p{InKatakana}\\p{InCJK_Unified_Ideographs}]".r

  override def name = "reading"

  override def aliases = List("read", "r")

  override def description = "Show the romaji and furigana readings of Japanese text"

  override def longDescription(invocation: String): String =
    s"""When invoked alone (with no message), acts on the previous message in the channel.
       |Otherwise, give this command some text to work with.
       |Example: $invocation 藁で束ねても男一匹
    """.stripMargin

  override val permissions = CommandPermissions.Anyone

  private val textArg =
    ArgSpec("text", "The text to annotate with furigana", ArgType.GreedyString, required = false)

  override val argSpec = List(textArg)

  override def canBeEdited = false

  override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
    async {
      given JDA = ctx.jda
      ctx.invoker.replyLater(false)

      val rawInput = ctx.args.get(textArg) match
        case None =>
          ctx.invoker.channel
            .map(_.id)
            .flatMap(chanId =>
              messageCache
                .find(d => d.chanId == chanId && JAPANESE.findFirstMatchIn(d.text).isDefined)
                .map(_.text)
            )
            .getOrElse("")
        case Some(text) => text

      val guild = ctx.invoker.member.toOption.map(_.getGuild)
      val input = CommandHelper.mentionsToPlaintext(guild, rawInput)
      if input.isEmpty then
        await(ctx.invoker.reply(BotMessages.error("You need to enter some text first")))
      else
        val furiganaFuture = queryKakasi(KAKASI_FURIGANA, input)
        val romajiFuture = queryKakasi(KAKASI_ROMAJI, input)

        val furigana = processFurigana(await(furiganaFuture))

        await(ctx.invoker.reply(FuriganaCommand.makeFuriMessage(furigana, await(romajiFuture))))
    }

  private def processFurigana(raw: String): Iterable[(String, String)] =
    val spaces = WHITESPACE.findAllMatchIn(raw).map(_.start).toVector
    for
      positions <- (0 +: spaces.map(_ + 1)) zip (spaces :+ raw.length)
      (start, end) = positions
      item <-
        val rawFuri = raw.slice(start, end)
        val furigana = parseSingleFurigana(rawFuri)
        val space = raw.slice(end, end + 1)
        List(furigana, (space, ""))
    yield item

  private def parseSingleFurigana(elem: String): (String, String) =
    val splitAt =
      if elem.endsWith("]") then elem.lastIndexOf("[")
      else -1
    if splitAt == -1 then (elem, "")
    else (elem take splitAt, elem.slice(splitAt + 1, elem.length - 1))

  private def queryKakasi(cmd: Array[String], text: String): Future[String] =
    async {
      val withDict: Array[String | Null] =
        if DICT_FILE.exists() then cmd :+ DICT_FILE.getPath
        else cmd.unsafeNullableArray
      val kakasi = Runtime.getRuntime.nn.exec(withDict).nn
      val os = kakasi.getOutputStream.nn
      Future {
        val encoded = summon[Codec].encoder.encode(CharBuffer.wrap(text)).nn
        val encodedArr = new Array[Byte](encoded.remaining())
        encoded.get(encodedArr)
        blocking {
          os.write(encodedArr)
          os.flush()
          os.close()
        }
      }

      val stdout = Future(blocking {
        io.Source.fromInputStream(kakasi.getInputStream.nn).mkString
      })

      val stderr = Future(blocking {
        io.Source.fromInputStream(kakasi.getErrorStream.nn).mkString
      })

      val exitCode = blocking(kakasi.waitFor())

      if exitCode != 0 then throw Exception(s"Kakasi failed. stderr: ${await(stderr)}")

      await(stdout)
    }

  def available: Boolean =
    try
      val result = queryKakasi(KAKASI_ROMAJI, "今テストなう")
      try Await.result(result, Duration(30, TimeUnit.SECONDS)).trim == "ima tesuto nau"
      catch case _: TimeoutException => false
    catch case _: IOException => false
end ReadCommand
