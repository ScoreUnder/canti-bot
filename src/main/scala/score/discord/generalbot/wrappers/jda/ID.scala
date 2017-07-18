package score.discord.generalbot.wrappers.jda

import slick.lifted.MappedTo

final class ID[+T](val value: Long) extends AnyVal with MappedTo[Long]

object ID {
  def fromString[T](string: String) = new ID[T](try {
    java.lang.Long.parseUnsignedLong(string)
  } catch {
    case _: NumberFormatException => string.toLong
  })
}
