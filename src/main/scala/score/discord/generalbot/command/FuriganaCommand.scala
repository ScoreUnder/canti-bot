package score.discord.generalbot.command
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.Furigana
import score.discord.generalbot.functionality.Commands
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.{APIHelper, BotMessages}
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.async.Async._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

class FuriganaCommand(commands: Commands)(implicit messageOwnership: MessageOwnership) extends Command.Anyone {

  override def name = "furigana"

  override def aliases = List("furi", "fg")

  override def description = "Render text with furigana as an image"

  override def longDescription =
    s"""Mix text and furigana:
      |`${commands.prefix}$name {郵便局:ゆうびんきょく}に{行:い}きました`
      |This will then be rendered into an image, with the furigana text on top of the corresponding kanji.
    """.stripMargin

  override def execute(message: Message, args: String) {
    if (args.isEmpty) {
      message.getChannel.sendOwned(
        BotMessages error "Please provide the text to render as part of the command.",
        owner = message.getAuthor
      )
      return
    }

    def parseInput() = {
      var input = args
      val arr = mutable.ArrayBuffer.empty[(String, String)]
      while (!input.isEmpty) {
        {
          val plain = input.takeWhile(!"{｛".contains(_))
          input = input drop (plain.length + 1)
          if (!plain.isEmpty)
            arr ++= plain.split("\n", -1).view.flatMap(line => List(("\n", ""), (line, ""))).tail
        }

        {
          val literal = input.takeWhile(!":：".contains(_))
          input = input drop (literal.length + 1)
          val phonetic = input.takeWhile(!"}｝".contains(_))
          input = input drop (phonetic.length + 1)
          if (!literal.isEmpty || !phonetic.isEmpty)
            arr += ((literal, phonetic))
        }
      }
      arr
    }

    val maybeGuild = Option(message.getGuild)
    def mentionsToPlaintext(input: String) = {
      import net.dv8tion.jda.core.MessageBuilder.MentionType._
      maybeGuild match {
        case Some(guild) =>
          new MessageBuilder().append(input).stripMentions(guild, USER, ROLE, CHANNEL).getStringBuilder.toString
        case None =>
          input
      }
    }

    async {
      message.getChannel.sendTyping().queue()

      val (origWithoutFuri, furiText) = {
        val orig = parseInput()
        (orig.map(_._1).mkString, orig.map(t => (mentionsToPlaintext(t._1), mentionsToPlaintext(t._2))))
      }

      val imageBytes = Furigana.renderPNG(furiText)

      import net.dv8tion.jda.core.MessageBuilder.MentionType._
      val newMessage = new MessageBuilder()
        .append(origWithoutFuri)
      maybeGuild match {
        case Some(guild) =>
          // Allow channel mentions - why not?
          // Also, work around a JDA bug by putting EVERYONE/HERE at the end
          newMessage.stripMentions(guild, USER, ROLE, EVERYONE, HERE)
        case None =>
      }
      newMessage.getStringBuilder.insert(0, s"${message.getAuthor.mention} ")
      val newMsg = await(message.getChannel.sendFile(imageBytes, "furigana.png", newMessage.build).queueFuture())
      messageOwnership(newMsg) = message.getAuthor
    }.failed foreach APIHelper.loudFailure("rendering furigana", message.getChannel)
  }
}
