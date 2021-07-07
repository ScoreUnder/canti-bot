package score.discord.canti.wrappers.jda

object Conversions:
  given richChannelAction: RichChannelAction.type = RichChannelAction

  given richGenericComponentInteractionCreateEvent
    : RichGenericComponentInteractionCreateEvent.type = RichGenericComponentInteractionCreateEvent

  given richGenericMessageEvent: RichGenericMessageEvent.type = RichGenericMessageEvent

  given richGuild: RichGuild.type = RichGuild

  given richGuildChannel: RichGuildChannel.type = RichGuildChannel

  given richJDA: RichJDA.type = RichJDA

  given richMember: RichMember.type = RichMember

  given richMessage: RichMessage.type = RichMessage

  given richMessageChannel: RichMessageChannel.type = RichMessageChannel

  given richRestAction: RichRestAction.type = RichRestAction

  given richRole: RichRole.type = RichRole

  given richSnowflake: RichSnowflake.type = RichSnowflake

  given richUser: RichUser.type = RichUser

  given richVoiceChannel: RichVoiceChannel.type = RichVoiceChannel
