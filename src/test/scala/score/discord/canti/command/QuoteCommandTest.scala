package score.discord.canti.command

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import score.discord.canti.TestFixtures

class QuoteCommandTest extends AnyFlatSpec with should.Matchers {
  val fixture = TestFixtures.default

  import fixture._
  import implicits._

  val quoterChannel = botChannel
  val quoteeChannel = exampleChannel

  commands.register(new QuoteCommand)

  def quoteCommandTest(invocation: String, expected: String): Unit =
    testCommand(invocation).getEmbeds.get(0).getDescription should include(expected)

  "The &quote command" should "understand id + channel mention" in {
    quoteCommandTest(s"&quote ${quoteeMessage.getIdLong} ${quoteeChannel.getAsMention}", quoteeMessageData)
  }

  it should "understand long-style message quotes" in {
    quoteCommandTest(s"&quote ${quoteeChannel.getIdLong}-${quoteeMessage.getIdLong}", quoteeMessageData)
  }

  it should "understand URL message quotes" in {
    quoteCommandTest(s"&quote https://canary.discord.com/channels/${guild.getId}/${quoteeChannel.getIdLong}/${quoteeMessage.getIdLong}", quoteeMessageData)
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
