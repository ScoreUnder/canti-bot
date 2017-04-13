package score.discord.generalbot.command

import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.Config

import scala.collection.GenIterable

trait Command {
  def name: String

  def aliases: GenIterable[String]

  def description: String

  def checkPermission(message: Message): Boolean

  def execute(message: Message, args: String): Unit
}

object Command {

  trait ServerAdminOnly extends Command {
    override def checkPermission(message: Message) =
      Option(message.getGuild).exists(_ getMember message.getAuthor hasPermission Permission.MANAGE_SERVER)
  }

  trait Anyone extends Command {
    override def checkPermission(message: Message) = true
  }

  trait BotOwnerOnly extends Command {
    override def checkPermission(message: Message) =
      message.getAuthor.getIdLong == Config.BOT_OWNER
  }

}
