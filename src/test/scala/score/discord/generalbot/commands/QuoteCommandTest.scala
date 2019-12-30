package score.discord.generalbot.commands

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.scalatest._
import score.discord.generalbot.collections.{MessageCache, NullCacheBackend, ReplyCache}
import score.discord.generalbot.command.QuoteCommand
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.jdamocks.{FakeJda, FakeUser}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class QuoteCommandTest extends FlatSpec with Matchers {
  implicit val messageOwnership: MessageOwnership = new MessageOwnership(new NullCacheBackend)

  val jda = new FakeJda
  val guild = jda.makeGuild()
  val quoteeChannel = guild.makeTextChannel("funny-stuff")
  val quoterChannel = guild.makeTextChannel("bot")
  val quoteeAuthor = new FakeUser("Philosoraptor", jda.nextId)
  val quoterAuthor = new FakeUser("Snoopy", jda.nextId)
  guild.registerMember(quoteeAuthor)
  guild.registerMember(quoterAuthor)
  quoteeChannel.addMessage("Red herring one", quoterAuthor)
  val quoteeMessageData = "This is a test message"
  val quoteeMessage = quoteeChannel.addMessage(quoteeMessageData, quoteeAuthor)
  quoteeChannel.addMessage("Red herring two", quoterAuthor)
  val quotee2MessageData = "Second test message"
  val quotee2Message = quoteeChannel.addMessage(quotee2MessageData, quoteeAuthor)
  val quotee3MessageData = "Message in the same channel as the command"
  val quotee3Message = quoterChannel.addMessage(quotee3MessageData, quoteeAuthor)

  implicit val messageCache: MessageCache = new MessageCache
  implicit val replyCache: ReplyCache = new ReplyCache
  messageCache.onEvent(new MessageReceivedEvent(null, 0, quotee2Message))
  val cmd = new QuoteCommand

  def quoteCommandTest(invocation: String, expected: String): Unit = {
    val quotingMessage = quoterChannel.addMessage(invocation, quoterAuthor)

    val future = cmd.executeFuture(quotingMessage, invocation.drop(invocation.indexOf(" ") + 1))
    Await.result(future, Duration.Inf)

    quoterChannel.retrieveMessageById(quoterChannel.getLatestMessageId).complete()
      .getEmbeds.get(0).getDescription should include(expected)
  }

  "The &quote command" should "understand id + channel mention" in {
    quoteCommandTest(s"&quote ${quoteeMessage.getIdLong} ${quoteeChannel.getAsMention}", quoteeMessageData)
  }

  it should "understand long-style message quotes" in {
    quoteCommandTest(s"&quote ${quoteeChannel.getIdLong}-${quoteeMessage.getIdLong}", quoteeMessageData)
  }

  it should "find cached messages" in {
    quoteCommandTest(s"&quote ${quotee2Message.getIdLong}", quotee2MessageData)
  }

  it should "find messages in the same channel" in {
    quoteCommandTest(s"&quote ${quotee3Message.getIdLong}", quotee3MessageData)
  }
}
