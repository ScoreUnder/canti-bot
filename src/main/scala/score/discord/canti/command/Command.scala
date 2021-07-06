package score.discord.canti.command

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.{ISnowflake, Message, User}
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.RichMessage.guild
import score.discord.canti.wrappers.jda.RichSnowflake.id

trait Command extends BaseCommand:
  def aliases: Seq[String] = Nil

  def longDescription(invocation: String): String = ""

  def checkPermission(message: Message): Boolean

  def permissionMessage: String

  def execute(message: Message, args: String): Unit

  def executeForEdit(message: Message, myMessageOption: Option[ID[Message]], args: String): Unit = {}

object Command:
  trait ServerAdminOnly extends Command:
    override def checkPermission(message: Message) =
      message.guild.exists(_ getMember message.getAuthor hasPermission Permission.MANAGE_SERVER)

    override def permissionMessage = "Only server admins may use this command."

  trait Anyone extends Command:
    override def checkPermission(message: Message) = true

    override def permissionMessage = "Anyone may use this command."

  trait OneUserOnly extends Command:
    override def checkPermission(message: Message) =
      message.getAuthor.id == userId

    override def permissionMessage = s"This command may only be run by <@$userId>"

    def userId: ID[User]
