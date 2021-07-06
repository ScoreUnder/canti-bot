package score.discord.canti

import java.util.concurrent.Executors

import net.dv8tion.jda.api.entities.Message
import score.discord.canti.collections.{MessageCache, NullCacheBackend, ReplyCache}
import score.discord.canti.command.ReplyingCommand
import score.discord.canti.functionality.Commands
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.jdamocks.{FakeJda, FakeUser}
import score.discord.canti.wrappers.Scheduler

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object TestFixtures:
  class DefaultFixture private[TestFixtures]:
    val jda = FakeJda()
    val guild = jda.makeGuild()
    val exampleChannel = guild.makeTextChannel("funny-stuff")
    val botChannel = guild.makeTextChannel("bot")
    val commandUser = FakeUser("Snoopy", jda.nextId)
    val secondaryUser = FakeUser("Philosoraptor", jda.nextId)
    guild.registerMember(commandUser)
    guild.registerMember(secondaryUser)
    exampleChannel.addMessage("Red herring one", commandUser)
    val quoteeMessageData = "This is a test message"
    val quoteeMessage = exampleChannel.addMessage(quoteeMessageData, secondaryUser)
    exampleChannel.addMessage("Red herring two", commandUser)
    val quotee2MessageData = "Second test message"
    val quotee2Message = exampleChannel.addMessage(quotee2MessageData, secondaryUser)
    val quotee3MessageData = "Message in the same channel as the command"
    val quotee3Message = botChannel.addMessage(quotee3MessageData, secondaryUser)

    given messageOwnership: MessageOwnership = MessageOwnership(NullCacheBackend())
    given messageCache: MessageCache = MessageCache()
    given replyCache: ReplyCache = ReplyCache()
    given scheduler: Scheduler = Scheduler(Executors.newSingleThreadScheduledExecutor())

    val commands = Commands()

    def testCommand(invocation: String): Message =
      val quotingMessage = botChannel.addMessage(invocation, commandUser)
      val (cmdName, args) = commands.splitCommand(invocation)
        .getOrElse(throw IllegalArgumentException("Command does not start with prefix"))

      val future = commands.get(cmdName) match
        case Some(cmd: ReplyingCommand) =>
          cmd.executeFuture(quotingMessage, args)
        case Some(_) => throw UnsupportedOperationException("Non-ReplyingCommand being tested")
        case None => throw IllegalArgumentException("Bad command")

      Await.result(future, Duration.Inf)

  def default = DefaultFixture()
