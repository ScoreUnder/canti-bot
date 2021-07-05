package score.discord.canti.collections

import java.util

import net.dv8tion.jda.api.entities.Message
import score.discord.canti.wrappers.jda.ID

class ReplyCache(capacity: Int = 100) {
  private[this] val replies = new util.LinkedHashMap[ID[Message], ID[Message]] {
    override def removeEldestEntry(eldest: util.Map.Entry[ID[Message], ID[Message]]): Boolean =
      size > ReplyCache.this.capacity
  }

  def +=(reply: (ID[Message], ID[Message])): Unit = replies.synchronized {
    // Needs to be cast to Unit here, or `synchronized' will return an
    // ID[Message] which will be automatically unpacked to Long. However if
    // a null is unpacked, it throws an NPE. This cast prevents all that.
    replies.put(reply._1, reply._2): Unit
  }

  def get(origId: ID[Message]): Option[ID[Message]] = replies.synchronized {
    Option(replies.get(origId))
  }
}
