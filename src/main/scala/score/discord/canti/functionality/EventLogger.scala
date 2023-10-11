package score.discord.canti.functionality

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.*
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import net.dv8tion.jda.api.events.guild.member.{
  GuildMemberJoinEvent, GuildMemberRemoveEvent, GuildMemberRoleAddEvent, GuildMemberRoleRemoveEvent,
  GuildMemberUpdateEvent
}
import net.dv8tion.jda.api.events.guild.voice.*
import net.dv8tion.jda.api.events.http.HttpRequestEvent
import net.dv8tion.jda.api.events.message.*
import net.dv8tion.jda.api.events.message.react.*
import net.dv8tion.jda.api.events.role.update.RoleUpdatePositionEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.user.GenericUserEvent
import net.dv8tion.jda.api.hooks.EventListener
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.StringUtils.formatMessageForLog as formatMessage
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.Conversions.{
  richChannel, richGuild, richMember, richMessageChannel, richUser
}
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.RichGenericMessageEvent.messageId
import score.discord.canti.wrappers.jda.RichSnowflake.rawId

import java.lang.annotation.Annotation
import java.util
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.reflectiveCalls
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.events.channel.update.GenericChannelUpdateEvent

class EventLogger(using messageOwnership: MessageOwnership) extends EventListener:
  private val logger = loggerOf[EventLogger]

  private def logHigherIfMyMessage(ev: GenericMessageEvent, logLine: String): Unit =
    given JDA = ev.getJDA.nn
    messageOwnership(ev.messageId).foreach {
      case None    => logger.trace(logLine)
      case Some(_) => logger.debug(logLine)
    }

  override def onEvent(event: GenericEvent): Unit = event match
    case _: ReadyEvent =>
      logger.info("Bot is ready.")
    case ev: StatusChangeEvent =>
      logger.info(s"Bot status changed to ${ev.getNewStatus}")
    case ev: MessageReceivedEvent =>
      val msg = ev.getMessage.nn
      logger.trace(
        s"MESSAGE: ${msg.nn.rawId} ${ev.getChannel.nn.unambiguousString} ${ev.getAuthor.nn.unambiguousString}\n" +
          formatMessage(msg)
      )
    case ev: MessageDeleteEvent =>
      logger.trace(s"DELETED: ${ev.getChannel.nn.unambiguousString} id=${ev.getMessageIdLong}")
    case ev: MessageUpdateEvent =>
      logger.trace(
        s"EDITED: ${ev.getChannel.nn.unambiguousString} ${ev.getAuthor.nn.unambiguousString}\n" +
          formatMessage(ev.getMessage.nn)
      )
    case ev: GuildVoiceUpdateEvent =>
      val member = ev.getMember.nn
      (ev.getChannelLeft, ev.getChannelJoined) match
        case (null, null) => logger.debug(s"VOICE UPDATE: no change for ${member.unambiguousString}")
        case (null, chan) => logger.debug(s"VOICE JOIN: ${member.unambiguousString} to ${chan.nn.unambiguousString}")
        case (chan, null) => logger.debug(s"VOICE LEAVE: ${member.unambiguousString} from ${chan.nn.unambiguousString}")
        case (from, to) =>
          logger.debug(
            s"VOICE MOVE: ${member.unambiguousString} from " +
              s"${from.nn.unambiguousString} to ${to.nn.unambiguousString}"
          )
    case ev: GuildMemberUpdateNicknameEvent =>
      logger.trace(
        s"NICK CHANGE: ${ev.getGuild.nn.unambiguousString} ${ev.getMember.nn.unambiguousString} " +
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
      logger.debug(s"ROLE ADD: ${ev.getMember.nn.unambiguousString}: ${ev.getRoles}")
    case ev: GuildMemberRoleRemoveEvent =>
      logger.debug(s"ROLE DEL: ${ev.getMember.nn.unambiguousString}: ${ev.getRoles}")
    case ev: GuildVoiceDeafenEvent =>
      logger.debug(s"DEAFEN: ${ev.getMember.nn.unambiguousString} isDeafened=${ev.isDeafened}")
    case ev: GenericChannelUpdateEvent[?] =>
      logger.debug(
        s"CHANNEL UPDATE: ${ev.getPropertyIdentifier} = ${ev.getOldValue} → ${ev.getNewValue} for ${ev.getChannel.nn.unambiguousString} in ${ev.getGuild}"
      )
    case ev: ChannelCreateEvent =>
      logger.debug(s"CHANNEL CREATE: ${ev.getChannel.nn.unambiguousString} in ${ev.getGuild}")
    case ev: ChannelDeleteEvent =>
      logger.debug(s"CHANNEL DELETE: ${ev.getChannel.nn.unambiguousString} in ${ev.getGuild}")
    case ev: HttpRequestEvent =>
      val req = ev.getRequest.nn
      if logger.isTraceEnabled then
        logger.trace(s"HTTP REQUEST: ${req.getRoute}\nResponse: ${ev.getResponseRaw}")
      else logger.debug(s"HTTP REQUEST: ${req.getRoute}")
    case _: GenericUserEvent | _: GuildMemberUpdateEvent |
        _: RoleUpdatePositionEvent | _: GatewayPingEvent | _: GuildVoiceSelfMuteEvent |
        _: GuildVoiceMuteEvent | _: GuildVoiceGuildMuteEvent | _: GuildVoiceSelfDeafenEvent |
        _: GuildVoiceGuildDeafenEvent | _: GuildMemberUpdateNicknameEvent |
        _: GuildMemberRemoveEvent | _: GuildMemberJoinEvent | _: GuildVoiceStreamEvent |
        _: MessageEmbedEvent =>
    // Ignored (they're pretty boring)
    case ev =>
      if !ev.getClass.getAnnotations.nn.exists(_.isInstanceOf[Deprecated]) then
        logger.debug(ev.getClass.toGenericString)
end EventLogger
