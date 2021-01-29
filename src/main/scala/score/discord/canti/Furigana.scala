package score.discord.canti

import java.awt.image.BufferedImage
import java.awt.{Color, Font, FontMetrics, RenderingHints}
import java.io.ByteArrayOutputStream

import javax.imageio.ImageIO

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

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
  def renderPNG(furiText: Iterable[(String, String)]): Array[Byte] = {
    val furiYAdjust = 0
    val lineGap = 10

    val mainMetrics = dummyGraphics.getFontMetrics(mainFont)
    val furiMetrics = dummyGraphics.getFontMetrics(furiFont)
    val furiHeight = furiMetrics.getHeight
    val lineHeight = mainMetrics.getHeight + furiHeight + lineGap
    val furiAscent = furiMetrics.getAscent + furiYAdjust
    val mainAscent = mainMetrics.getAscent

    val positionedFuri = positionText(furiText, -furiYAdjust, lineHeight, mainMetrics, furiMetrics)

    val image = new BufferedImage(
      positionedFuri.map(f => (f.furiWidth max f.textWidth) + f.x).max,
      positionedFuri.head.y + lineHeight,
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

    val outputStream = new ByteArrayOutputStream(32768)

    ImageIO.write(image, "PNG", outputStream)
    graphics.dispose()

    outputStream.toByteArray
  }

  private def positionText(furiText: Iterable[(String, String)], initialY: Int, lineHeight: Int, mainMetrics: FontMetrics, furiMetrics: FontMetrics): Iterable[PositionedFurigana] = {
    val imageMaxWidth = 1000

    def splitText(text: String, toWidth: Int) = {
      val numCodePoints = text.codePoints().iterator().asScala.to(LazyList)
        .scanLeft(0) { (width, chr) => width + mainMetrics.charWidth(chr) }
        .lastIndexWhere { width => width < toWidth }
      text take text.offsetByCodePoints(0, numCodePoints)
    }

    @tailrec
    def process(x: Int, y: Int, vals: List[(String, String)], acc: List[PositionedFurigana]): List[PositionedFurigana] =
      vals match {
        case Nil => acc
        case ("\n", "") :: next => process(0, y + lineHeight, next, acc)
        case (text, "") :: next =>
          val width = mainMetrics.stringWidth(text)
          val maxWidth = imageMaxWidth - x
          if (width > maxWidth) {
            val split = splitText(text, maxWidth)
            val (spaceSplit, remain) = split.lastIndexWhere(_.isWhitespace) match {
              case -1 => (split, text drop split.length)
              case i => (split take i, text drop (i+1))
            }
            val result = PositionedFurigana(x, y, mainMetrics.stringWidth(spaceSplit), 0, spaceSplit, "")
            process(0, y + lineHeight, (remain, "") :: next, result :: acc)
          } else {
            val result = PositionedFurigana(x, y, width, 0, text, "")
            process(x + width, y, next, result :: acc)
          }
        case (text, furi) :: next =>
          val textWidth = mainMetrics.stringWidth(text)
          val furiWidth = furiMetrics.stringWidth(furi)
          val width = textWidth max furiWidth
          if (x + width > imageMaxWidth && x > 0) {
            process(0, y + lineHeight, vals, acc)
          } else {
            val result = PositionedFurigana(x, y, textWidth, furiWidth, text, furi)
            process(x + width, y, next, result :: acc)
          }
      }

    process(0, initialY, furiText.toList, Nil)
  }
}
