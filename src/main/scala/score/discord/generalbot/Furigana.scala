package score.discord.generalbot

import java.awt.{Color, Font, RenderingHints}

import scala.annotation.tailrec
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

import scala.collection.mutable

object Furigana {
  private val dummyImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
  private val dummyGraphics = dummyImage.createGraphics()
  private val mainFont = new Font("M+ 2c regular", Font.PLAIN, 60)
  private val furiFont = new Font("M+ 2c regular", Font.PLAIN, 30)
  private val backgroundColor = new Color(0x36, 0x39, 0x3e)

  private case class PositionedFurigana(x: Int, y: Int, textWidth: Int, furiWidth: Int, text: String, furigana: String)

  /** Render text into a PNG with the phonetic reading listed above the
    * literal text.
    *
    * @param furiText List of (Literal, Reading) pairs comprising the text
    * @return PNG data
    */
  def renderPNG(furiText: Traversable[(String, String)]): Array[Byte] = {
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

    outputStream.toByteArray
  }

}
