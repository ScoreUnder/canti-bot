package score.discord.generalbot

import java.util.concurrent.Executors

import score.discord.generalbot.collections.{MessageCache, NullCacheBackend, ReplyCache}
import score.discord.generalbot.functionality.Commands
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.jdamocks.{FakeJda, FakeUser}
import score.discord.generalbot.wrappers.Scheduler

object TestFixtures {
  def default = new {
    val jda = new FakeJda
    val guild = jda.makeGuild()
    val exampleChannel = guild.makeTextChannel("funny-stuff")
    val botChannel = guild.makeTextChannel("bot")
    val commandUser = new FakeUser("Snoopy", jda.nextId)
    val secondaryUser = new FakeUser("Philosoraptor", jda.nextId)
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

    val implicits = new {
      implicit val messageOwnership: MessageOwnership = new MessageOwnership(new NullCacheBackend)
      implicit val messageCache: MessageCache = new MessageCache
      implicit val replyCache: ReplyCache = new ReplyCache
      implicit val scheduler: Scheduler = new Scheduler(Executors.newSingleThreadScheduledExecutor())
    }

    val commands = {
      import implicits._
      new Commands
    }
  }
}
