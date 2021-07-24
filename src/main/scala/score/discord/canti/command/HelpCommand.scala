package score.discord.canti.command

import cps.*
import cps.monads.FutureAsyncMonad
import net.dv8tion.jda.api.Permission.*
import net.dv8tion.jda.api.{EmbedBuilder, JDA}
import score.discord.canti.BotMeta
import score.discord.canti.command.api.{
  ArgSpec, ArgType, CommandInvocation, CommandInvoker, CommandPermissions
}
import score.discord.canti.functionality.Commands
import score.discord.canti.util.BotMessages
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.RetrievableMessage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

class HelpCommand(commands: Commands) extends GenericCommand:
  val pageSize = 10

  override def name = "help"

  override def aliases = List("h")

  override def description = "Show descriptions for all commands, or view one command in detail"

  override def permissions = CommandPermissions.Anyone

  sealed trait PageOrCommand
  private case class Page(num: Int) extends PageOrCommand
  private case class Command(name: String) extends PageOrCommand

  private val pageOrCommandType =
    import ArgType.*
    val pageType = Integer.map(i => Page(i.toInt))
    val commandType = GreedyString.map(Command(_))
    Disjunction(pageType, commandType)

  private val pageOrCommandArg = ArgSpec(
    "pageOrCommand",
    "Page number or command name to look up",
    pageOrCommandType,
    required = false
  )

  override val argSpec = List(pageOrCommandArg)

  override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
    async {
      val response =
        ctx.args.get(pageOrCommandArg).getOrElse(Page(1)) match
          case Page(page)       => showHelpPage(ctx.invoker, page)
          case Command(cmdName) => showCommandHelp(cmdName)
      await(ctx.invoker.reply(response.fold(BotMessages.error, identity)))
    }

  private def inviteLink(using jda: JDA) =
    jda.getInviteUrl(MANAGE_ROLES, MANAGE_CHANNEL, MESSAGE_MANAGE, VOICE_MOVE_OTHERS)

  private def showCommandHelp(command: String) =
    val unprefixed = command.stripPrefix(commands.prefix)
    commands
      .get(unprefixed)
      .toRight("Expected a page number or command name, but got something else.")
      .map(command =>
        BotMessages `plain`
          s"""**Names:** `${(List(command.name) ++ command.aliases).mkString("`, `")}`
             |**Restrictions:** ${command.permissions.description}
             |**Parameters:** ${stringifyArgspec(command.argSpec)}
             |${command.description}
             |
             |${command.longDescription(commands.prefix + unprefixed)}""".stripMargin.trimnn
      )

  private def showHelpPage(invoker: CommandInvoker, page: Int) =
    given JDA = invoker.user.getJDA

    val myCommands = commands.all.filter(_.permissions.canExecute(invoker))
    val pageOffset = pageSize * (page - 1)
    val numPages = (myCommands.length + pageSize - 1) / pageSize

    if page < 1 then Left("ಠ_ಠ")
    else if page > numPages then
      Left(
        s"There are only $numPages pages, but you asked for page $page. That page does not exist."
      )
    else
      val helpList = myCommands.slice(pageOffset, pageOffset + pageSize)
      val embed = EmbedBuilder()
      embed.appendDescription(
        "You can erase most replies this bot sends to you by reacting with ❌ or 🚮.\n" +
          s"**Commands (page $page of $numPages):**\n"
      )

      for command <- helpList do
        embed `appendDescription` s"`${commands.prefix}${command.name}`: ${command.description}\n"

      embed.appendDescription(
        "[Source code 🗒️](https://github.com/ScoreUnder/canti-bot) \\| " +
          s"[Invite to your server 📥]($inviteLink) \\| " +
          s"${BotMeta.NAME} ${BotMeta.VERSION}"
      )

      Right(embed)

  private def stringifyArgspec(argSpec: List[ArgSpec[?]]) =
    if argSpec.isEmpty then "None"
    else
      argSpec
        .map { sp =>
          s"`${sp.name}`${if sp.required then "" else " (optional)"} -- ${sp.description}"
        }
        .mkString("\n", "\n", "\n")
end HelpCommand
