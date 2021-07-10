package score.discord.canti.functionality

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.*
import net.dv8tion.jda.api.events.channel.text.{TextChannelCreateEvent, TextChannelDeleteEvent}
import net.dv8tion.jda.api.events.channel.text.update.{
  GenericTextChannelUpdateEvent, TextChannelUpdateNameEvent, TextChannelUpdateSlowmodeEvent
}
import net.dv8tion.jda.api.events.channel.voice.{VoiceChannelCreateEvent, VoiceChannelDeleteEvent}
import net.dv8tion.jda.api.events.channel.voice.update.{
  GenericVoiceChannelUpdateEvent, VoiceChannelUpdateNameEvent
}
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import net.dv8tion.jda.api.events.guild.member.{
  GuildMemberJoinEvent, GuildMemberRemoveEvent, GuildMemberRoleAddEvent, GuildMemberRoleRemoveEvent,
  GuildMemberUpdateEvent
}
import net.dv8tion.jda.api.events.guild.voice.*
import net.dv8tion.jda.api.events.http.HttpRequestEvent
import net.dv8tion.jda.api.events.message.*
import net.dv8tion.jda.api.events.message.guild.GenericGuildMessageEvent
import net.dv8tion.jda.api.events.message.react.*
import net.dv8tion.jda.api.events.role.update.RoleUpdatePositionEvent
import net.dv8tion.jda.api.events.user.GenericUserEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.slf4j.LoggerFactory
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.StringUtils.{formatMessageForLog as formatMessage}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.Conversions.{
  richGuild, richMember, richMessageChannel, richUser, richVoiceChannel
}
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.RichGenericMessageEvent.messageId
import score.discord.canti.wrappers.jda.RichSnowflake.rawId

import java.lang.annotation.Annotation
import java.util
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.reflectiveCalls

class EventLogger(using messageOwnership: MessageOwnership) extends EventListener:
  private[this] val logger = LoggerFactory.getLogger(classOf[EventLogger]).nn

  private def logHigherIfMyMessage(ev: GenericMessageEvent, logLine: String): Unit =
    given JDA = ev.getJDA
    messageOwnership(ev.messageId).foreach {
      case None    => logger.trace(logLine)
      case Some(_) => logger.debug(logLine)
    }

  override def onEvent(event: GenericEvent): Unit = event match
    case _: ReadyEvent =>
      logger.info("Bot is ready.")
    case ev: StatusChangeEvent =>
      logger.info(s"Bot status changed to ${ev.getNewStatus}")
    case ev: DisconnectEvent =>
      val reason = ev.getCloseCode.?.map(code => s"code=${code.getCode} meaning=${code.getMeaning}")
        .getOrElse("no reason provided")
      logger.warn(s"Disconnected, $reason")
    case ev: MessageReceivedEvent =>
      logger.trace(
        s"MESSAGE: ${ev.getMessage.rawId} ${ev.getChannel.unambiguousString} ${ev.getAuthor.unambiguousString}\n" +
          formatMessage(ev.getMessage)
      )
    case ev: MessageDeleteEvent =>
      logger.trace(s"DELETED: ${ev.getChannel.unambiguousString} id=${ev.getMessageIdLong}")
    case ev: MessageUpdateEvent =>
      logger.trace(
        s"EDITED: ${ev.getChannel.unambiguousString} ${ev.getAuthor.unambiguousString}\n" +
          formatMessage(ev.getMessage)
      )
    case ev: GuildVoiceJoinEvent =>
      logger.debug(
        s"VOICE JOIN: ${ev.getMember.unambiguousString} in ${ev.getChannelJoined.unambiguousString}"
      )
    case ev: GuildVoiceLeaveEvent =>
      logger.debug(
        s"VOICE PART: ${ev.getMember.unambiguousString} from ${ev.getChannelLeft.unambiguousString}"
      )
    case ev: GuildVoiceMoveEvent =>
      logger.debug(
        s"VOICE MOVE: ${ev.getMember.unambiguousString} from " +
          s"${ev.getChannelLeft.unambiguousString} to ${ev.getChannelJoined.unambiguousString}"
      )
    case ev: GuildMemberUpdateNicknameEvent =>
      logger.trace(
        s"NICK CHANGE: ${ev.getGuild.unambiguousString} ${ev.getMember.unambiguousString} " +
          s"from ${ev.getOldNickname} to ${ev.getNewNickname}"
      )
    case ev: MessageReactionAddEvent =>
      logHigherIfMyMessage(
        ev,
        s"REACT: ${ev.getUser.?.fold(s"User?(${ev.getUserId})")(_.unambiguousString)} ${ev.getReaction}"
      )
    case ev: MessageReactionRemoveEvent =>
      logHigherIfMyMessage(
        ev,
        s"UNREACT: ${ev.getUser.?.fold(s"User?(${ev.getUserId})")(_.unambiguousString)} ${ev.getReaction}"
      )
    case ev: MessageReactionRemoveAllEvent =>
      logHigherIfMyMessage(ev, s"CLEAR REACT: ${ev.getMessageId}")
    case ev: GuildMemberRoleAddEvent =>
      logger.debug(s"ROLE ADD: ${ev.getMember.unambiguousString}: ${ev.getRoles}")
    case ev: GuildMemberRoleRemoveEvent =>
      logger.debug(s"ROLE DEL: ${ev.getMember.unambiguousString}: ${ev.getRoles}")
    case ev: GuildVoiceDeafenEvent =>
      logger.debug(s"DEAFEN: ${ev.getMember.unambiguousString} isDeafened=${ev.isDeafened}")
    case ev: GenericTextChannelUpdateEvent[?] =>
      logger.debug(
        s"TEXT CHANNEL UPDATE: ${ev.getPropertyIdentifier} = ${ev.getOldValue} → ${ev.getNewValue} for ${ev.getChannel.unambiguousString} in ${ev.getGuild}"
      )
    case ev: GenericVoiceChannelUpdateEvent[?] =>
      logger.debug(
        s"VOICE CHANNEL UPDATE: ${ev.getPropertyIdentifier} = ${ev.getOldValue} → ${ev.getNewValue} for ${ev.getChannel.unambiguousString} in ${ev.getGuild}"
      )
    case ev: VoiceChannelCreateEvent =>
      logger.debug(s"VOICE CHANNEL CREATE: ${ev.getChannel.unambiguousString} in ${ev.getGuild}")
    case ev: VoiceChannelDeleteEvent =>
      logger.debug(s"VOICE CHANNEL DELETE: ${ev.getChannel.unambiguousString} in ${ev.getGuild}")
    case ev: TextChannelCreateEvent =>
      logger.debug(s"TEXT CHANNEL CREATE: ${ev.getChannel.unambiguousString} in ${ev.getGuild}")
    case ev: TextChannelDeleteEvent =>
      logger.debug(s"TEXT CHANNEL DELETE: ${ev.getChannel.unambiguousString} in ${ev.getGuild}")
    case _: GenericUserEvent | _: GenericGuildMessageEvent | _: GuildMemberUpdateEvent |
        _: RoleUpdatePositionEvent | _: GatewayPingEvent | _: GuildVoiceSelfMuteEvent |
        _: GuildVoiceMuteEvent | _: GuildVoiceGuildMuteEvent | _: GuildVoiceSelfDeafenEvent |
        _: GuildVoiceGuildDeafenEvent | _: GuildMemberUpdateNicknameEvent |
        _: GuildMemberRemoveEvent | _: GuildMemberJoinEvent | _: GuildVoiceStreamEvent |
        _: MessageEmbedEvent | _: HttpRequestEvent =>
    // Ignored (they're pretty boring)
    case ev =>
      if !ev.getClass.getAnnotations.nn.exists(_.isInstanceOf[Deprecated]) then
        logger.debug(ev.getClass.toGenericString)
end EventLogger
