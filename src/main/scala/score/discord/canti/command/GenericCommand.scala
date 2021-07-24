package score.discord.canti.command

import score.discord.canti.command.api.{ArgSpec, CommandInvocation, CommandPermissions}
import score.discord.canti.wrappers.jda.RetrievableMessage

import scala.concurrent.Future

trait GenericCommand:
  def name: String

  def description: String

  def aliases: Seq[String] = Nil

  def longDescription(invocation: String): String = ""

  def permissions: CommandPermissions

  def argSpec: List[ArgSpec[?]]

  def canBeEdited: Boolean = true

  def execute(ctx: CommandInvocation): Future[RetrievableMessage]
