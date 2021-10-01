package score.discord.canti.command

import cps.*
import cps.monads.FutureAsyncMonad
import net.dv8tion.jda.api.entities.{Message, User}
import score.discord.canti.command.api.*
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.{APIHelper, BotMessages}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.RetrievableMessage
import score.discord.canti.wrappers.jda.RichJDA.guilds
import score.discord.canti.wrappers.jda.RichMessage.!
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichUser.{discriminator, name}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.chaining.*

class BotInfoCommand(owner: ID[User]) extends GenericCommand:
  override def name = "botinfo"

  override def description = "Show miscellaneous info about the bot"

  override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
    async {
      val jda = ctx.jda
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
        ctx.invoker.reply(
          BotMessages
            .plain("Some basic bot info")
            .addField("Owner", s"<@$owner>", true)
            .addField("Servers", s"${allGuilds.size}", true)
            .addField("Top servers", topGuilds.mkString("\n"), false)
            .setThumbnail(me.getIconUrl)
        )
      )
    }

  override def argSpec: List[ArgSpec[?]] = Nil

  override val permissions = CommandPermissions.OneUserOnly(owner)
