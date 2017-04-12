package score.discord.generalbot.command

import net.dv8tion.jda.core.entities.Message

import scala.collection.GenIterable

trait Command {
  def name: String

  def aliases: GenIterable[String]

  def description: String

  def isAdminOnly: Boolean

  def execute(message: Message, args: String): Unit
}
