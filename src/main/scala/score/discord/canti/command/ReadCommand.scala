package score.discord.canti.command

import cps._
import cps.monads.FutureAsyncMonad
import net.dv8tion.jda.api.entities.Message
import score.discord.canti.collections.{MessageCache, ReplyCache}
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.{APIHelper, BotMessages, CommandHelper}
import score.discord.canti.wrappers.jda.Conversions._

import java.io.{File, IOException}
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, TimeoutException, blocking}
import scala.io.Codec

class ReadCommand(messageCache: MessageCache)(implicit messageOwnership: MessageOwnership, replyCache: ReplyCache) extends Command.Anyone {
  private val KAKASI_FURIGANA = "kakasi -s -f -ieuc -oeuc -JH".split(" ")
  private val KAKASI_ROMAJI = "kakasi -s -ieuc -oeuc -Ja -Ka -Ha -Ea -ka -ja".split(" ")
  private val DICT_FILE = new File("extra_words")
  private given Codec = Codec("EUC-JP").onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
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

  override def execute(message: Message, args: String): Unit = {
    async {
      val rawInput = args.trim match {
        case "" =>
          val chanId = message.getChannel.id
          messageCache
            .find(d => d.chanId == chanId && JAPANESE.findFirstMatchIn(d.text).isDefined)
            .map(_.text)
            .getOrElse("")
        case text => text
      }
      val input = CommandHelper(message).mentionsToPlaintext(rawInput)
      if (input.isEmpty) {
        await(message ! BotMessages.error("You need to enter some text first"))
      } else {
        val furiganaFuture = queryKakasi(KAKASI_FURIGANA, input)
        val romajiFuture = queryKakasi(KAKASI_ROMAJI, input)

        val furigana = processFurigana(await(furiganaFuture))

        await(FuriganaCommand.sendFuriMessage(message, furigana, await(romajiFuture)))
      }
    }.failed.foreach(APIHelper.loudFailure("displaying kakasi reading", message))
  }

  private def processFurigana(raw: String): Iterable[(String, String)] = {
    val spaces = WHITESPACE.findAllMatchIn(raw).map(_.start).toVector
    (for {
      positions <- (0 +: spaces.map(_ + 1)) zip (spaces :+ raw.length)
      (start, end) = positions
    } yield {
      val elem = raw.slice(start, end)
      val splitAt =
        if (elem.endsWith("]")) elem.lastIndexOf("[")
        else -1
      val furigana =
        if (splitAt == -1) (elem, "")
        else (elem take splitAt, elem.substring(splitAt + 1, elem.length - 1))
      val space = raw.slice(end, end + 1)
      List(furigana, (space, ""))
    }).flatten
  }

  private def queryKakasi(cmd: Array[String], text: String): Future[String] = {
    async {
      val withDict =
        if (DICT_FILE.exists()) cmd :+ DICT_FILE.getPath
        else cmd
      val kakasi = Runtime.getRuntime.exec(withDict)
      val os = kakasi.getOutputStream
      Future {
        val encoded = summon[Codec].encoder.encode(CharBuffer.wrap(text))
        val encodedArr = new Array[Byte](encoded.remaining())
        encoded.get(encodedArr)
        blocking {
          os.write(encodedArr)
          os.flush()
          os.close()
        }
      }

      val stdout = Future(blocking {
        io.Source.fromInputStream(kakasi.getInputStream).mkString
      })

      val stderr = Future(blocking {
        io.Source.fromInputStream(kakasi.getErrorStream).mkString
      })

      val exitCode = blocking(kakasi.waitFor())

      if (exitCode != 0)
        throw new Exception(s"Kakasi failed. stderr: ${await(stderr)}")

      await(stdout)
    }
  }

  def available: Boolean = {
    try {
      val result = queryKakasi(KAKASI_ROMAJI, "今テストなう")
      try {
        Await.result(result, Duration(30, TimeUnit.SECONDS))
          .trim == "ima tesuto nau"
      } catch {
        case _: TimeoutException => false
      }
    } catch {
      case _: IOException => false
    }
  }
}
