package score.discord.canti.wrappers

import org.slf4j.LoggerFactory

import scala.reflect.ClassTag

object NullWrappers:
  def loggerOf[T: ClassTag] = LoggerFactory.getLogger(summon[ClassTag[T]].runtimeClass).nn

  extension [T](me: T | Null)
    inline def ? : Option[T] =
      if me != null then Some(me) else None

    inline def ??[U >: T](inline default: => U): U =
      if me != null then me else default

    inline def ?<>[L](inline left: => L): Either[L, T] =
      if me != null then Right(me) else Left(left)

  extension [T <: AnyRef](inline me: Array[T | Null])
    inline def unsafeNonNullArray: Array[T] =
      me.asInstanceOf[Array[T]]

  extension [T <: AnyRef](inline me: Array[T])
    inline def unsafeNullableArray: Array[T | Null] =
      me.asInstanceOf[Array[T | Null]]

  import scala.language.unsafeNulls
  extension (inline me: String)
    inline def splitnn(inline regex: String, inline limit: Int = 0): Array[String] =
      me.split(regex, limit)

    inline def trimnn: String =
      me.trim

    inline def replacenn(inline regex: String, inline replacement: String): String =
      me.replace(regex, replacement)

    inline def lowernn: String =
      me.toLowerCase
