package score.discord.generalbot.functionality

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events._
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import net.dv8tion.jda.api.events.guild.member.{GuildMemberRoleAddEvent, GuildMemberRoleRemoveEvent, GuildMemberUpdateEvent}
import net.dv8tion.jda.api.events.guild.voice._
import net.dv8tion.jda.api.events.http.HttpRequestEvent
import net.dv8tion.jda.api.events.message._
import net.dv8tion.jda.api.events.message.guild.GenericGuildMessageEvent
import net.dv8tion.jda.api.events.message.react._
import net.dv8tion.jda.api.events.user.GenericUserEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.slf4j.LoggerFactory
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.StringUtils.{formatMessageForLog => formatMessage}
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.reflectiveCalls

class EventLogger(implicit messageOwnership: MessageOwnership) extends EventListener {
  private[this] val logger = LoggerFactory.getLogger(classOf[EventLogger])

  private def logHigherIfMyMessage(ev: GenericMessageEvent, logLine: String): Unit = {
    implicit val jda: JDA = ev.getJDA
    messageOwnership(new ID[Message](ev.getMessageIdLong)).foreach {
      case None => logger.trace(logLine)
      case Some(_) => logger.debug(logLine)
    }
  }

  override def onEvent(event: GenericEvent): Unit = event match {
    case _: ReadyEvent =>
      logger.info("Bot is ready.")
    case ev: StatusChangeEvent =>
      logger.info(s"Bot status changed to ${ev.getNewStatus}")
    case ev: DisconnectEvent =>
      val reason = Option(ev.getCloseCode)
        .map(code => s"code=${code.getCode} meaning=${code.getMeaning}")
        .getOrElse("no reason provided")
      logger.warn(s"Disconnected, $reason")
    case ev: MessageReceivedEvent =>
      logger.trace(s"MESSAGE: ${ev.getMessage.rawId} ${ev.getChannel.unambiguousString} ${ev.getAuthor.unambiguousString}\n" +
        formatMessage(ev.getMessage))
    case ev: MessageDeleteEvent =>
      logger.trace(s"DELETED: ${ev.getChannel.unambiguousString} id=${ev.getMessageIdLong}")
    case ev: MessageUpdateEvent =>
      logger.trace(s"EDITED: ${ev.getChannel.unambiguousString} ${ev.getAuthor.unambiguousString}\n" +
        formatMessage(ev.getMessage))
    case ev: GuildVoiceJoinEvent =>
      logger.debug(s"VOICE JOIN: ${ev.getMember.getUser.unambiguousString} in ${ev.getChannelJoined.unambiguousString}")
    case ev: GuildVoiceLeaveEvent =>
      logger.debug(s"VOICE PART: ${ev.getMember.getUser.unambiguousString} from ${ev.getChannelLeft.unambiguousString}")
    case ev: GuildVoiceMoveEvent =>
      logger.debug(s"VOICE MOVE: ${ev.getMember.getUser.unambiguousString} from " +
        s"${ev.getChannelLeft.unambiguousString} to ${ev.getChannelJoined.unambiguousString}")
    case ev: GuildMemberUpdateNicknameEvent =>
      logger.trace(s"NICK CHANGE: ${ev.getGuild.unambiguousString} ${ev.getMember.getUser.unambiguousString} " +
        s"from ${ev.getOldNickname} to ${ev.getNewNickname}")
    case ev: MessageReactionAddEvent =>
      logHigherIfMyMessage(ev, s"REACT: ${ev.getUser.unambiguousString} ${ev.getReaction}")
    case ev: MessageReactionRemoveEvent =>
      logHigherIfMyMessage(ev, s"UNREACT: ${ev.getUser.unambiguousString} ${ev.getReaction}")
    case ev: MessageReactionRemoveAllEvent =>
      logHigherIfMyMessage(ev, s"CLEAR REACT: ${ev.getMessageId}")
    case ev: GuildMemberRoleAddEvent =>
      logger.debug(s"ROLE ADD: ${ev.getMember.getUser.unambiguousString}: ${ev.getRoles}")
    case ev: GuildMemberRoleRemoveEvent =>
      logger.debug(s"ROLE DEL: ${ev.getMember.getUser.unambiguousString}: ${ev.getRoles}")
    case _: GenericUserEvent
         | _: GenericGuildMessageEvent
         | _: GuildMemberUpdateEvent
         | _: GatewayPingEvent
         | _: GuildVoiceSelfMuteEvent
         | _: GuildVoiceMuteEvent
         | _: GuildVoiceGuildMuteEvent
         | _: GuildMemberUpdateNicknameEvent
         | _: MessageEmbedEvent
         | _: HttpRequestEvent =>
    // Ignored (they're pretty boring)
    case ev =>
      logger.debug(ev.getClass.toGenericString)
  }

}
