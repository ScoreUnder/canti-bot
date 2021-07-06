package score.discord.canti.util

import java.text.NumberFormat
import java.time.Duration

import scala.util.chaining.given

object TimeUtils:
  val timeUnits = Vector(
    "ns" -> 1L,
    "µs" -> 1000L,
    "ms" -> 1000000L,
    "s" -> 1000000000L,
    "m" -> 60000000000L,
    "h" -> 3600000000000L
  )

  private val decimalFormat = NumberFormat.getNumberInstance.tap(_ setMinimumFractionDigits 0)

  /** Formats a duration in a human-readable way. Takes the highest time unit
    * possible and displays as a fraction of that.
    *
    * {{{
    *   scala> TimeUtils.formatTimeDiff(Duration.ofSeconds(40593))
    *   res9: String = 11.276h
    * }}}
    *
    * @param length duration to format
    * @return human-readable version of duration
    */
  def formatTimeDiff(length: Duration): String =
    val nanos = length.toNanos
    val absNanos = Math.abs(nanos)
    timeUnits.reverseIterator.find { absNanos >= _._2 } match
      case Some((unit, size)) => s"${decimalFormat.format(nanos.toDouble / size.toDouble)}$unit"
      case None => nanos.toString
