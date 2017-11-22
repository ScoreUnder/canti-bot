package score.discord.generalbot.command

import net.dv8tion.jda.core.entities.{Message, User}
import score.discord.generalbot.util.{APIHelper, BotMessages}
import score.discord.generalbot.wrappers.jda.ID
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global

class BotInfoCommand(override val userId: ID[User]) extends Command.OneUserOnly {
  override def name = "botinfo"

  override def aliases = Nil

  override def description = "Show miscellaneous info about the bot"

  override def execute(message: Message, args: String): Unit = {
    async {
      val jda = message.getJDA
      val allGuilds = jda.guilds
      val topGuilds = allGuilds.sortBy(_.getMemberCache.size).take(10).map { guild =>
        val memberCount = guild.getMemberCache.size
        val owner = guild.getOwner.getUser
        s"${guild.getName} ($memberCount users; owner: ${owner.name}#${owner.discriminator})"
      }
      val me = await(jda.asBot().getApplicationInfo.queueFuture())
      await(message.getChannel ! BotMessages.plain("Some basic bot info")
        .addField("Owner", s"<@$userId>", true)
        .addField("Servers", s"${allGuilds.size}", true)
        .addField("Top servers", topGuilds.mkString("\n"), false)
        .setThumbnail(me.getIconUrl))
    }.failed.foreach(APIHelper.loudFailure("getting bot info", message.getChannel))
  }
}
