package score.discord.generalbot.command
import java.awt.image._
import java.awt.{Color, Font, RenderingHints}
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.functionality.Commands
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.{APIHelper, BotMessages}
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.annotation.tailrec
import scala.async.Async._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

class FuriganaCommand(commands: Commands)(implicit messageOwnership: MessageOwnership) extends Command.Anyone {
  private val dummyImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
  private val dummyGraphics = dummyImage.createGraphics()
  private val mainFont = new Font("M+ 2c regular", Font.PLAIN, 60)
  private val furiFont = new Font("M+ 2c regular", Font.PLAIN, 30)
  private val backgroundColor = new Color(0x36, 0x39, 0x3e)

  private case class PositionedFurigana(x: Int, y: Int, textWidth: Int, furiWidth: Int, text: String, furigana: String)

  override def name = "furigana"

  override def aliases = List("furi")

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

      val imageMaxWidth = 1000
      val furiYAdjust = 10
      val lineGap = 10

      val mainMetrics = dummyGraphics.getFontMetrics(mainFont)
      val furiMetrics = dummyGraphics.getFontMetrics(furiFont)
      val furiHeight = furiMetrics.getHeight
      val lineHeight = mainMetrics.getHeight + furiHeight + lineGap
      val furiAscent = furiMetrics.getAscent + furiYAdjust
      val mainAscent = mainMetrics.getAscent

      def splitText(text: String, toWidth: Int) = {
        var width, index, prevIndex = 0
        while (width < toWidth && index < text.length) {
          prevIndex = index
          index = text.offsetByCodePoints(index, 1)
          assert(prevIndex != index)

          width += mainMetrics.charWidth(text.codePointAt(prevIndex))
        }
        text take prevIndex
      }

      val positionedFuri = {
        var x = 0
        var y = -furiYAdjust
        def advanceLine() {
          x = 0
          y += lineHeight
        }
        furiText flatMap {
          case (text, furi) =>
            @tailrec
            def process(text: String, furi: String, result: mutable.ArrayBuffer[PositionedFurigana]): Seq[PositionedFurigana] = {
              val textWidth = mainMetrics.stringWidth(text)
              val furiWidth = furiMetrics.stringWidth(furi)
              val width = textWidth max furiWidth

              def addVerbatim() = {
                result += PositionedFurigana(x, y, textWidth, furiWidth, text, furi)
                x += width
                result
              }

              if (text == "\n" && furi.isEmpty) {
                advanceLine()
                result
              } else if (x + width > imageMaxWidth) {
                if (furi.isEmpty) {
                  val maxWidth = imageMaxWidth - x
                  val split = splitText(text, maxWidth)
                  result += PositionedFurigana(x, y, mainMetrics.stringWidth(split), furiWidth, split, furi)
                  advanceLine()
                  process(text drop split.length, furi, result)
                } else {
                  if (x > 0) advanceLine()
                  addVerbatim()
                }
              } else addVerbatim()
            }
            process(text, furi, mutable.ArrayBuffer.empty[PositionedFurigana])
        }
      }

      val image = new BufferedImage(
        positionedFuri.map(f => (f.furiWidth max f.textWidth) + f.x).max,
        positionedFuri.last.y + lineHeight,
        BufferedImage.TYPE_INT_RGB
      )
      val graphics = image.createGraphics()

      graphics.setBackground(backgroundColor)
      graphics.clearRect(0, 0, image.getWidth, image.getHeight)
      graphics.setColor(Color.WHITE)
      graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

      graphics.setFont(furiFont)
      for (PositionedFurigana(x, y, tw, fw, _, furi) <- positionedFuri)
        graphics.drawString(furi, x + ((tw max fw) - fw) / 2, y + furiAscent)

      graphics.setFont(mainFont)
      for (PositionedFurigana(x, y, tw, fw, text, _) <- positionedFuri)
        graphics.drawString(text, x + ((tw max fw) - tw) / 2, y + furiHeight + mainAscent)

      val outputStream = new ByteArrayOutputStream(4096)

      ImageIO.write(image, "PNG", outputStream)
      graphics.dispose()

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
      val newMsg = await(message.getChannel.sendFile(outputStream.toByteArray, "furigana.png", newMessage.build).queueFuture())
      messageOwnership(newMsg) = message.getAuthor
    }.failed foreach APIHelper.loudFailure("rendering furigana", message.getChannel)
  }
}
