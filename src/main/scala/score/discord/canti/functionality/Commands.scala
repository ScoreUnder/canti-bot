package score.discord.canti.functionality

import com.codedx.util.MapK
import net.dv8tion.jda.api.entities.{ChannelType, Message, MessageChannel}
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import score.discord.canti.collections.{MessageCache, ReplyCache}
import score.discord.canti.command.api.{
  ArgSpec, CommandInvocation, CommandInvoker, EditedMessageInvoker, MessageInvoker
}
import score.discord.canti.command.GenericCommand
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.StringUtils.formatMessageForLog
import score.discord.canti.util.{APIHelper, BotMessages}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.Conversions.{
  richMessage, richMessageChannel, richSnowflake, richUser
}
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.matching.Events.{NonBotMessage, NonBotMessageEdit}

import java.time.Instant
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.chaining.*

object Commands:
  private val logger = loggerOf[Commands]

  /** Normalises a command name string into a form suitable to be looked up as a key in the command
    * map. Not guaranteed to behave similarly between versions.
    *
    * @param name
    *   command name
    * @return
    *   name in normalised form
    */
  private def normaliseCommandName(name: String): String = name.lowernn

  object CommandOrdering extends Ordering[GenericCommand]:
    def compare(c1: GenericCommand, c2: GenericCommand): Int =
      normaliseCommandName(c1.name) compare normaliseCommandName(c2.name)

  /** Determines whether a command corresponding to a given message can be executed on that guild by
    * that member, and if not returns a human-readable error message.
    *
    * @param cmd
    *   command to check
    * @param origin
    *   entity invoking the command
    * @return
    *   either error or command
    */
  def canRunCommand(cmd: GenericCommand, origin: CommandInvoker): Either[String, GenericCommand] =
    val permission = cmd.permissions
    Either.cond(permission.canExecute(origin), cmd, permission.description)

  def runIfAllowed(
    invocation: CommandInvocation,
    cmd: GenericCommand
  ): Either[String, GenericCommand] =
    logger.debug(s"Command invocation $invocation")
    canRunCommand(cmd, invocation.invoker).tap {
      case Right(_) =>
        cmd
          .execute(invocation)
          .failed
          .foreach(
            APIHelper.loudFailure(
              s"running ${invocation.prefix}${invocation.name}",
              invocation.invoker.asMessageReceiver
            )
          )
      case Left(err) => invocation.invoker.reply(BotMessages.error(err))
    }

  def logCommandInvocation(invoker: CommandInvoker, cmd: GenericCommand): Unit =
    logger.debug(
      s"Running command '${cmd.name}' on behalf of ${invoker.user.unambiguousString} in ${invoker.channel
        .fold("<unknown>")(_.unambiguousString)}"
    )
end Commands

class Commands(using MessageCache, ReplyCache, MessageOwnership) extends EventListener:
  import Commands.*

  // All commands and aliases, indexed by name
  private val commands = mutable.HashMap[String, GenericCommand]()
  // Commands list excluding aliases
  private val commandList = mutable.TreeSet[GenericCommand]()(using CommandOrdering)
  // String prepended before a command
  val prefix = "&"

  /** Register a command with this command registry. The command may then be retrieved via its main
    * name or any of its aliases, and will be available to the command-dispatching event listener.
    *
    * @param command
    *   command to register
    */
  def register(command: GenericCommand): Unit =
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
  def get(commandName: String): Option[GenericCommand] =
    commands.get(normaliseCommandName(commandName))

  /** Retrieve all registered commands from this command registry.
    *
    * @return
    *   collection of all commands
    */
  def all: Seq[GenericCommand] = commandList.toList

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

  type ArgMap = MapK[ArgSpec, [T] =>> T]
  sealed trait ParseResult
  case object NotACommand extends ParseResult
  final case class ParseSuccess(command: GenericCommand, name: String, args: ArgMap)
      extends ParseResult
  final case class ParseFailure(command: GenericCommand, name: String, message: String)
      extends ParseResult

  /** Parses a command string into the command object and argument map.
    *
    * @param invoker
    *   the invoker of this command
    * @param input
    *   command string
    * @return
    *   ParseResult corresponding to command
    */
  def parseCommand(invoker: CommandInvoker, input: String): ParseResult =
    parseCommandOnly(input).fold(NotACommand) { case (cmd, cmdName, cmdExtra) =>
      parseArgList(invoker, cmd.argSpec, cmdExtra)
        .fold(ParseFailure(cmd, cmdName, _), ParseSuccess(cmd, cmdName, _))
    }

  def parseCommandOnly(input: String): Option[(GenericCommand, String, String)] =
    for
      (cmdName, cmdExtra) <- splitCommand(input)
      cmd <- get(cmdName)
    yield (cmd, cmdName, cmdExtra)

  @tailrec
  final def parseArgList(
    invoker: CommandInvoker,
    argSpecs: List[ArgSpec[?]],
    s: String,
    acc: ArgMap = MapK.empty
  ): Either[String, ArgMap] =
    argSpecs match
      case Nil => Right(acc)
      case spec :: specs =>
        spec.argType.fromString(invoker, s) match
          case Some((value, remaining)) =>
            val newAcc = acc + (spec, value)
            parseArgList(invoker, specs, remaining, newAcc)
          case None if spec.required =>
            Left(s"You must specify `${spec.name}` -- ${spec.description}")
          case None =>
            parseArgList(invoker, specs, s, acc)

  private def logIfMaybeCommand(logPrefix: String, message: Message): Unit =
    if message.getContentRaw.startsWith(prefix) then
      logger.debug(
        s"$logPrefix: ${message.rawId} ${message.getAuthor.unambiguousString} ${message.getChannel.unambiguousString}\n${formatMessageForLog(message)}"
      )

  private def logAndInvoke(
    cmd: GenericCommand,
    name: String,
    args: ArgMap,
    invoker: CommandInvoker
  ): Unit =
    val invocation = CommandInvocation(prefix, name, args, invoker)
    logCommandInvocation(invoker, cmd)
    runIfAllowed(invocation, cmd)

  override def onEvent(event: GenericEvent): Unit = event match
    case NonBotMessage(message) =>
      logIfMaybeCommand("COMMAND?", message)
      val invoker = MessageInvoker(message)
      parseCommand(invoker, message.getContentRaw) match
        case NotACommand =>
        case ParseFailure(_, name, failure) =>
          logger.debug(s"Command $name parse failure: $failure")
          message ! BotMessages.error(failure)
        case ParseSuccess(cmd, name, args) =>
          logAndInvoke(cmd, name, args, invoker)

    case NonBotMessageEdit(oldMsg, newMsg) =>
      logIfMaybeCommand("COMMAND EDIT?", newMsg)

      def prepareInvoker(cmd: GenericCommand): CommandInvoker =
        summon[ReplyCache].get(oldMsg.messageId) match
          case Some(replyId) =>
            parseCommandOnly(oldMsg.text) match
              case Some((oldCmd, _, _)) if oldCmd.canBeEdited =>
                EditedMessageInvoker(newMsg, replyId)
              case Some(_) =>
                newMsg.getChannel.deleteMessage(replyId)
                MessageInvoker(newMsg)
              case None => MessageInvoker(newMsg)
          case _ => MessageInvoker(newMsg)

      parseCommand(MessageInvoker(newMsg), newMsg.getContentRaw) match
        case NotACommand =>
        case ParseFailure(cmd, name, failure) =>
          logger.debug(s"Command $name parse failure: $failure")
          prepareInvoker(cmd).reply(BotMessages.error(failure))
        case ParseSuccess(cmd, name, args) =>
          logAndInvoke(cmd, name, args, prepareInvoker(cmd))

    case _ =>
end Commands
