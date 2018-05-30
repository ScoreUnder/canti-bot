package score.discord.generalbot.functionality

import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.events._
import net.dv8tion.jda.core.events.guild.member.GuildMemberNickChangeEvent
import net.dv8tion.jda.core.events.guild.voice._
import net.dv8tion.jda.core.events.message._
import net.dv8tion.jda.core.events.message.guild.GenericGuildMessageEvent
import net.dv8tion.jda.core.events.message.react._
import net.dv8tion.jda.core.events.user.GenericUserEvent
import net.dv8tion.jda.core.hooks.EventListener
import org.apache.commons.lang3.time.FastDateFormat
import score.discord.generalbot.wrappers.jda.Conversions._

class EventLogger extends EventListener {
  private[this] val format = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ssZ")

  def log(msg: String): Unit = println(s"${format format System.currentTimeMillis} $msg")

  private def formatMessage(message: Message) =
    message.getContentRaw.split('\n').map("\t" + _).mkString("\n")

  override def onEvent(event: Event): Unit = event match {
    case _: ReadyEvent =>
      log("Bot is ready.")
    case ev: StatusChangeEvent =>
      log(s"Bot status changed to ${ev.getNewStatus}")
    case ev: DisconnectEvent =>
      ev.getCloseCode match {
        case null => log("Disconnected, no reason provided.")
        case code => log(s"Disconnected. code=${code.getCode} meaning=${code.getMeaning}")
      }
    case ev: MessageReceivedEvent =>
      log(s"MESSAGE: ${ev.getMessage.rawId} ${ev.getChannel.unambiguousString} ${ev.getAuthor.unambiguousString}\n" +
        formatMessage(ev.getMessage))
    case ev: MessageDeleteEvent =>
      log(s"DELETED: ${ev.getChannel.unambiguousString} id=${ev.getMessageIdLong}")
    case ev: MessageUpdateEvent =>
      log(s"EDITED: ${ev.getChannel.unambiguousString} ${ev.getAuthor.unambiguousString}\n" +
        formatMessage(ev.getMessage))
    case ev: GuildVoiceJoinEvent =>
      log(s"VOICE JOIN: ${ev.getMember.getUser.unambiguousString} in ${ev.getChannelJoined.unambiguousString}")
    case ev: GuildVoiceLeaveEvent =>
      log(s"VOICE PART: ${ev.getMember.getUser.unambiguousString} from ${ev.getChannelLeft.unambiguousString}")
    case ev: GuildVoiceMoveEvent =>
      log(s"VOICE MOVE: ${ev.getMember.getUser.unambiguousString} from " +
        s"${ev.getChannelLeft.unambiguousString} to ${ev.getChannelJoined.unambiguousString}")
    case ev: GuildMemberNickChangeEvent =>
      log(s"NICK CHANGE: ${ev.getGuild.unambiguousString} ${ev.getMember.getUser.unambiguousString} " +
        s"from ${ev.getPrevNick} to ${ev.getNewNick}")
    case ev: MessageReactionAddEvent =>
      log(s"REACT: ${ev.getUser.unambiguousString} ${ev.getReaction}")
    case ev: MessageReactionRemoveEvent =>
      log(s"UNREACT: ${ev.getUser.unambiguousString} ${ev.getReaction}")
    case ev: MessageReactionRemoveAllEvent =>
      log(s"CLEAR REACT: ${ev.getMessageId}")
    case _: GenericUserEvent | _: GenericGuildMessageEvent =>
    // Ignored (they're pretty boring)
    case ev =>
      log(ev.getClass.toGenericString)
  }

}
