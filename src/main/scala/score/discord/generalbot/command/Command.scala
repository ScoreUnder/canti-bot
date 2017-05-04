package score.discord.generalbot.command

import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.{ISnowflake, Message}
import score.discord.generalbot.Config
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.collection.GenIterable

trait Command {
  def name: String

  def aliases: GenIterable[String]

  def description: String

  def longDescription: String = ""

  def checkPermission(message: Message): Boolean

  def permissionMessage: String

  def execute(message: Message, args: String): Unit
}

object Command {

  trait ServerAdminOnly extends Command {
    override def checkPermission(message: Message) =
      Option(message.getGuild).exists(_ getMember message.getAuthor hasPermission Permission.MANAGE_SERVER)

    override def permissionMessage = "Only server admins may use this command."
  }

  trait Anyone extends Command {
    override def checkPermission(message: Message) = true

    override def permissionMessage = "Anyone may use this command."
  }

  trait ServerAdminDiscretion extends Anyone with ISnowflake

  trait OneUserOnly extends Command {
    override def checkPermission(message: Message) =
      message.getAuthor.id == Config.BOT_OWNER

    override def permissionMessage = s"This command may only be run by <@$userId>"

    def userId: Long
  }
}
