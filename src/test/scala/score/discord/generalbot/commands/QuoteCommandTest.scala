package score.discord.generalbot.commands

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.scalatest._
import score.discord.generalbot.TestFixtures
import score.discord.generalbot.command.QuoteCommand

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class QuoteCommandTest extends FlatSpec with Matchers {
  val fixture = TestFixtures.default

  import fixture._
  import implicits._

  val quoterChannel = botChannel
  val quoterAuthor = commandUser
  val quoteeChannel = exampleChannel

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
    // ensure message cache is populated with the message to find
    messageCache.onEvent(new MessageReceivedEvent(jda, 0, quotee2Message))

    quoteCommandTest(s"&quote ${quotee2Message.getIdLong}", quotee2MessageData)
  }

  it should "find messages in the same channel" in {
    quoteCommandTest(s"&quote ${quotee3Message.getIdLong}", quotee3MessageData)
  }
}
