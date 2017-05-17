package score.discord.generalbot.command

import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.functionality.Commands
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.BotMessages
import score.discord.generalbot.util.ParseUtils._
import score.discord.generalbot.wrappers.jda.Conversions._

class RestrictCommand(commands: Commands)(implicit messageOwnership: MessageOwnership) extends Command.ServerAdminOnly {
  override def name = "restrict"

  override def aliases = Nil

  override def description = "Restrict a command to a certain role."

  override def longDescription =
    s"""Allow only a single role to execute a given command.
       |Administrative roles will always be allowed to execute that command.
       |If you want to disable a command for normal users, set the required role to an administrator role.
       |If you want to enable a command for all users, set the role to everyone.
       |Usage:
       |`${commands.prefix}$name cmd everyone` - Allow everyone access to `${commands.prefix}cmd`
       |`${commands.prefix}$name cmd moderator` - Allow only the `moderator` group to access `${commands.prefix}cmd`
       |`${commands.prefix}$name cmd` - Display the restrictions present on `${commands.prefix}cmd`
    """.stripMargin

  override def execute(message: Message, args: String) {
    def searchCommand(cmdName: String) = commands.get(cmdName.stripPrefix(commands.prefix)) match {
      case Some(cmd: Command.ServerAdminDiscretion) => Right(cmd)
      case Some(_) => Left(BotMessages error "That command can't be restricted per-server yet. Sorry.")
      case None => Left(BotMessages error "No command by that name." addField("Searched for", cmdName, true))
    }

    val result = args.trim.split(" +", 2) match {
      case Array(cmdName, roleName) =>
        for {
          command <- searchCommand(cmdName)
          guild = message.getGuild
          everyone = guild.getPublicRole
          role <- roleName match {
            case "everyone" => Right(everyone)
            case _ => findRole(message.getGuild, roleName)
          }
        } yield {
          val previous = commands.permissionLookup(command, guild).getOrElse(everyone)

          if (role == everyone)
            commands.permissionLookup.remove(command, guild)
          else
            commands.permissionLookup(command, guild) = role

          BotMessages
            .okay("Command restriction altered")
            .addField("Previous role", previous.mention, true)
            .addField("New role", role.mention, true)
            .addField("Command altered", command.name, true)
        }

      case Array("") =>
        Left(BotMessages error "Please refer to the help page for this command.")

      case Array(cmdName) =>
        for {
          command <- searchCommand(cmdName)
          role <- commands.permissionLookup(command, message.getGuild)
            .toRight(BotMessages error "That command has not yet been restricted by role.")
        } yield {
          BotMessages plain s"Currently, the role required to execute `${commands.prefix}${command.name}` is ${role.mention}"
        }
    }

    message.getChannel.sendOwned(result.fold(identity, identity).toMessage, message.getAuthor)
  }
}
