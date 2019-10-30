package score.discord.generalbot.util

import java.text.NumberFormat
import java.time.Duration

import scala.util.chaining._

object TimeUtils {
  val timeUnits = Vector(
    "ns" -> 1L,
    "µs" -> 1000L,
    "ms" -> 1000000L,
    "s" -> 1000000000L,
    "m" -> 60000000000L,
    "h" -> 3600000000000L
  )

  private val decimalFormat = NumberFormat.getNumberInstance.tap(_ setMinimumFractionDigits 0)

  def formatTimeDiff(length: Duration): String = {
    val nanos = length.toNanos
    val absNanos = Math.abs(nanos)
    timeUnits.reverseIterator.find { absNanos >= _._2 } match {
      case Some((unit, size)) => s"${decimalFormat.format(nanos.toDouble / size.toDouble)}$unit"
      case None => nanos.toString
    }
  }
}
