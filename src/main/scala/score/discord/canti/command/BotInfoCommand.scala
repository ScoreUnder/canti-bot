package score.discord.canti.command

import cps.*
import cps.monads.FutureAsyncMonad
import net.dv8tion.jda.api.entities.{Message, User}
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.{APIHelper, BotMessages}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.RichJDA.guilds
import score.discord.canti.wrappers.jda.RichMessage.!
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichUser.{discriminator, name}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions

class BotInfoCommand(override val userId: ID[User])(using MessageOwnership, ReplyCache)
    extends Command.OneUserOnly:
  override def name = "botinfo"

  override def description = "Show miscellaneous info about the bot"

  override def execute(message: Message, args: String): Unit =
    async {
      val jda = message.getJDA
      val allGuilds = jda.guilds
      val topGuilds = allGuilds.sortBy(-_.getMemberCache.size).take(10).map { guild =>
        val memberCount = guild.getMemberCache.size
        val owner = guild.getOwner.?.map(_.getUser).fold(s"unknown user ${guild.getOwnerIdLong}")(
          user => s"${user.name}#${user.discriminator}"
        )
        s"${guild.getName} ($memberCount users; owner: $owner)"
      }
      val me = await(jda.retrieveApplicationInfo.queueFuture())
      await(
        message ! BotMessages
          .plain("Some basic bot info")
          .addField("Owner", s"<@$userId>", true)
          .addField("Servers", s"${allGuilds.size}", true)
          .addField("Top servers", topGuilds.mkString("\n"), false)
          .setThumbnail(me.getIconUrl)
      )
    }.failed.foreach(APIHelper.loudFailure("getting bot info", message))
