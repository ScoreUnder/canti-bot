package score.discord.canti.command.api

import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.Permission
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.Conversions.richSnowflake

trait CommandPermissions:
  def canExecute(origin: CommandInvoker): Boolean

  def description: String

object CommandPermissions:
  object ServerAdminOnly extends CommandPermissions:
    override def canExecute(origin: CommandInvoker): Boolean =
      origin.member.exists(_.hasPermission(Permission.MANAGE_SERVER))

    override def description = "Only server admins may use this command."

  object Anyone extends CommandPermissions:
    override def canExecute(origin: CommandInvoker): Boolean = true

    override def description = "Anyone may use this command."

  class OneUserOnly(userId: ID[User]) extends CommandPermissions:
    override def canExecute(origin: CommandInvoker) =
      origin.user.id == userId

    override def description = s"This command may only be run by <@$userId>"
