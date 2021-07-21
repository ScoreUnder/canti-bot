package score.discord.canti.functionality

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import score.discord.canti.collections.{MessageCache, ReplyCache}
import score.discord.canti.command.Command
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.StringUtils.formatMessageForLog
import score.discord.canti.util.{APIHelper, BotMessages}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.Conversions.{
  richMessage, richMessageChannel, richSnowflake, richUser
}
import score.discord.canti.wrappers.jda.matching.Events.{NonBotMessage, NonBotMessageEdit}

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.chaining.*

object Commands:
  /** Normalises a command name string into a form suitable to be looked up as a key in the command
    * map. Not guaranteed to behave similarly between versions.
    *
    * @param name
    *   command name
    * @return
    *   name in normalised form
    */
  private def normaliseCommandName(name: String): String = name.lowernn

  object CommandOrdering extends Ordering[Command]:
    def compare(c1: Command, c2: Command): Int =
      normaliseCommandName(c1.name) compare normaliseCommandName(c2.name)
end Commands

class Commands(using MessageCache, ReplyCache, MessageOwnership) extends EventListener:
  import Commands.*

  private val logger = loggerOf[Commands]
  // All commands and aliases, indexed by name
  private val commands = mutable.HashMap[String, Command]()
  // Commands list excluding aliases
  private val commandList = mutable.TreeSet[Command]()(using CommandOrdering)
  // String prepended before a command
  val prefix = "&"

  /** Register a command with this command registry. The command may then be retrieved via its main
    * name or any of its aliases, and will be available to the command-dispatching event listener.
    *
    * @param command
    *   command to register
    */
  def register(command: Command): Unit =
    commands(normaliseCommandName(command.name)) = command
    for alias <- command.aliases do commands(normaliseCommandName(alias)) = command
    commandList += command

  /** Retrieve a command by name from this command registry.
    *
    * @param commandName
    *   name or alias of command
    * @return
    *   optional command
    */
  def get(commandName: String): Option[Command] =
    commands.get(normaliseCommandName(commandName))

  /** Retrieve all registered commands from this command registry.
    *
    * @return
    *   collection of all commands
    */
  def all: Seq[Command] = commandList.toList

  /** Determines whether a command corresponding to a given message can be executed on that guild by
    * that member, and if not returns a human-readable error message.
    *
    * @param cmd
    *   command to check
    * @param message
    *   command message
    * @return
    *   either error or command
    */
  def canRunCommand(cmd: Command, message: Message): Either[String, Command] =
    Either.cond(cmd.checkPermission(message), cmd, cmd.permissionMessage)

  /** Splits a raw message into command name and arguments. No validation is done to check that the
    * name is correct in any way.
    *
    * @param messageRaw
    *   message to parse command from
    * @param requirePrefix
    *   whether command prefix is necessary
    * @return
    *   optionally (name, args) of command
    */
  def splitCommand(messageRaw: String, requirePrefix: Boolean = true): Option[(String, String)] =
    val hasPrefix = messageRaw.startsWith(prefix)
    if requirePrefix && !hasPrefix then None
    else
      val unprefixed = if hasPrefix then messageRaw.drop(prefix.length) else messageRaw
      val split = unprefixed.splitnn("[\\s　]", 2)

      val cmdName = split(0)
      val cmdExtra = if split.length < 2 then "" else split(1)

      Some((cmdName, cmdExtra))

  /** Parses a command string into the command object and argument string.
    *
    * @param input
    *   command string
    * @return
    *   optionally (command, args)
    */
  def parseCommand(input: String): Option[(Command, String)] = for
    (cmdName, cmdExtra) <- splitCommand(input)
    cmd <- get(cmdName)
  yield (cmd, cmdExtra)

  def runIfAllowed(message: Message, cmd: Command, cmdExtra: String): Either[String, Command] =
    canRunCommand(cmd, message).tap {
      case Right(_)  => cmd.execute(message, cmdExtra)
      case Left(err) => message ! BotMessages.error(err)
    }

  private def logIfMaybeCommand(logPrefix: String, message: Message): Unit =
    if message.getContentRaw.startsWith(prefix) then
      logger.debug(
        s"$logPrefix: ${message.rawId} ${message.getAuthor.unambiguousString} ${message.getChannel.unambiguousString}\n${formatMessageForLog(message)}"
      )

  private def logCommandInvocation(message: Message, cmd: Command): Unit =
    logger.debug(
      s"Running command '${cmd.name}' on behalf of ${message.getAuthor.unambiguousString} in ${message.getChannel.unambiguousString}"
    )

  override def onEvent(event: GenericEvent): Unit = event match
    case NonBotMessage(message) =>
      logIfMaybeCommand("COMMAND?", message)
      for (cmd, cmdExtra) <- parseCommand(message.getContentRaw) do
        logCommandInvocation(message, cmd)
        runIfAllowed(message, cmd, cmdExtra)
    case NonBotMessageEdit(oldMsg, newMsg) =>
      logIfMaybeCommand("COMMAND EDIT?", newMsg)
      for (cmd, cmdExtra) <- parseCommand(newMsg.getContentRaw) do
        parseCommand(oldMsg.text) match
          case None =>
            logger.debug(s"Editing non-command to command")
            logCommandInvocation(newMsg, cmd)
            runIfAllowed(newMsg, cmd, cmdExtra)
          case Some((`cmd`, _)) =>
            logger.debug(s"Editing old command in $oldMsg (same command)")
            logCommandInvocation(newMsg, cmd)
            canRunCommand(cmd, newMsg) match
              case Right(_) =>
                cmd.executeForEdit(newMsg, summon[ReplyCache].get(oldMsg.messageId), cmdExtra)
              case Left(_) => // Do not print error for edits to command with no perms
          case Some((_, _)) =>
            logger.debug(s"Editing old command in $oldMsg (different command)")
            logCommandInvocation(newMsg, cmd)
            for
              _ <- runIfAllowed(newMsg, cmd, cmdExtra)
              replyId <- summon[ReplyCache].get(oldMsg.messageId)
            do newMsg.getChannel.deleteMessage(replyId)
    case _ =>
end Commands
