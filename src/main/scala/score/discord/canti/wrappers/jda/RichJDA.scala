package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

object RichJDA:
  extension (jda: JDA)
    /** A list of guilds this bot (shard) is in */
    def guilds: mutable.Buffer[Guild] = jda.getGuilds.asScala
