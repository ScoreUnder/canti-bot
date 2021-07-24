package score.discord.canti

import java.util.concurrent.Executors

import net.dv8tion.jda.api.entities.Message
import score.discord.canti.collections.{MessageCache, NullCacheBackend, ReplyCache}
import score.discord.canti.command.api.*
import score.discord.canti.functionality.Commands
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.jdamocks.{FakeJda, FakeUser}
import score.discord.canti.wrappers.Scheduler

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object TestFixtures:
  class DefaultFixture private[TestFixtures]:
    val jda = FakeJda()
    val guild = jda.makeGuild()
    val exampleChannel = guild.makeTextChannel("funny-stuff")
    val botChannel = guild.makeTextChannel("bot")
    val commandUser = FakeUser(jda, "Snoopy", jda.nextId)
    val secondaryUser = FakeUser(jda, "Philosoraptor", jda.nextId)
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
    given scheduler: Scheduler = Scheduler(Executors.newSingleThreadScheduledExecutor().nn)

    val commands = Commands()

    def testCommand(invocation: String): Message =
      val quotingMessage = botChannel.addMessage(invocation, commandUser)
      val invoker = MessageInvoker(quotingMessage)

      import commands.*
      val future = parseCommand(invoker, invocation) match
        case ParseSuccess(cmd, name, args) =>
          cmd.execute(CommandInvocation(prefix, name, args, invoker))
        case ParseFailure(_, _, _) => throw UnsupportedOperationException("Bad invocation of command")
        case NotACommand           => throw IllegalArgumentException("Bad command")

      Await.result(future.flatMap(_.retrieve()), Duration.Inf)

  def default = DefaultFixture()
