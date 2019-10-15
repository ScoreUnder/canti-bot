package score.discord.generalbot.wrappers

import scala.language.implicitConversions

class Tap[T](val me: T) extends AnyVal {
  def tap(f: T => Unit): T = {
    f(me)
    me
  }
}

object Tap {
  implicit def toTap[T](me: T): Tap[T] = new Tap[T](me)
}
