package score.discord.canti.wrappers.jda

final class ID[+T](val value: Long) extends AnyVal {
  override def toString = value.toString
}

object ID {
  def fromString[T](string: String) = new ID[T](try {
    java.lang.Long.parseUnsignedLong(string)
  } catch {
    case _: NumberFormatException => string.toLong
  })
}
