package score.discord.generalbot

import sun.misc.Unsafe
import score.discord.generalbot.wrappers.Tap._

object MyUnsafe {
  lazy val theUnsafe: Unsafe =
    classOf[Unsafe]
      .getDeclaredField("theUnsafe")
      .tap(_.setAccessible(true))
      .get(null).asInstanceOf[Unsafe]
}
