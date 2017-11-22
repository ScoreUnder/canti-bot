package score.discord.generalbot.command

import java.io.File
import java.util.concurrent.TimeUnit

import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.MessageBuilder.MentionType
import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.Furigana
import score.discord.generalbot.collections.MessageCache
import score.discord.generalbot.functionality.Commands
import score.discord.generalbot.util.{APIHelper, BotMessages, CommandHelper}
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, TimeoutException, blocking}

class ReadCommand(commands: Commands, messageCache: MessageCache) extends Command.Anyone {
  private val KAKASI_FURIGANA = "kakasi -s -f -iutf8 -outf8 -JH".split(" ")
  private val KAKASI_ROMAJI = "kakasi -s -iutf8 -outf8 -Ja -Ka -Ha -Ea".split(" ")
  private val DICT_FILE = new File("extra_words")
  private val WHITESPACE = "\\s".r
  private val JAPANESE = "[\\p{InHiragana}\\p{InKatakana}\\p{InCJK_Unified_Ideographs}]".r

  override def name = "reading"

  override def aliases = List("read", "r")

  override def description = "Show the romaji and furigana readings of Japanese text"

  override val longDescription =
    s"""When invoked alone (with no message), acts on the previous message in the channel.
       |Otherwise, give this command some text to work with.
       |Example: ${commands.prefix}$name 藁で束ねても男一匹
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
        await(message.getChannel ! BotMessages.error("You need to enter some text first"))
      } else {
        val furiganaFuture = queryKakasi(KAKASI_FURIGANA, input)
        val romajiFuture = queryKakasi(KAKASI_ROMAJI, input)

        val furigana = processFurigana(await(furiganaFuture))
        val image = Furigana.renderPNG(furigana)

        val romaji = await(romajiFuture)
        val romajiMessage =
          new MessageBuilder()
            .append(romaji)
            .stripMentions(message.getJDA, MentionType.EVERYONE, MentionType.HERE)
            .build()

        await(message.getChannel.sendFile(image, "furigana.png", romajiMessage).queueFuture())
      }
    }.failed.foreach(APIHelper.loudFailure("displaying kakasi reading", message.getChannel))
  }

  private def processFurigana(raw: String): Traversable[(String, String)] = {
    val spaces = WHITESPACE.findAllMatchIn(raw).map(_.start).toVector
    (for {
      positions <- (List(0) ++ spaces.map(_ + 1)) zip (spaces ++ List(raw.length))
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
        if (DICT_FILE.exists()) cmd ++ Array(DICT_FILE.getPath)
        else cmd
      val kakasi = Runtime.getRuntime.exec(withDict)
      val os = kakasi.getOutputStream
      async(blocking {
        os.write(text.getBytes("utf-8"))
        os.flush()
        os.close()
      })

      val stdout = async(blocking {
        io.Source.fromInputStream(kakasi.getInputStream).mkString
      })

      val stderr = async(blocking {
        io.Source.fromInputStream(kakasi.getErrorStream).mkString
      })

      val exitCode = blocking(kakasi.waitFor())

      if (exitCode != 0)
        throw new Exception(s"Kakasi failed. stderr: ${await(stderr)}")

      await(stdout)
    }
  }

  def available: Boolean = {
    val result = queryKakasi(KAKASI_ROMAJI, "今テストなう")
    try {
      Await.result(result, Duration(30, TimeUnit.SECONDS))
        .trim == "ima tesuto nau"
    } catch {
      case _: TimeoutException => false
    }
  }
}
