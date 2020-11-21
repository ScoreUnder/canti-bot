package score.discord.generalbot.functionality

import net.dv8tion.jda.api.entities.{Message, MessageChannel, TextChannel, User}
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import score.discord.generalbot.collections.{AsyncMap, ReplyCache}
import score.discord.generalbot.command.Command
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.{APIHelper, BotMessages}
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.ID
import score.discord.generalbot.wrappers.jda.matching.Events.{MessageDelete, NonBotReact}
import score.discord.generalbot.wrappers.jda.matching.React

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.chaining._

class Spoilers(spoilerTexts: AsyncMap[ID[Message], String], commands: Commands, conversations: Conversations)(implicit messageOwnership: MessageOwnership, replyCache: ReplyCache) extends EventListener {
  val spoilerEmote = "🔍"

  commands register new Command.Anyone {
    override def name = "spoiler"

    override val aliases = List("sp", "spoil", "hide")

    override def description = "Hide a spoiler"

    override def longDescription(invocation: String) =
      s"""Hides your message with a short bot message not revealing its contents.
         |Others can click the magnifying glass on the message to see its contents.
         |Example usage:
         |```
         |$invocation
         |```
         |This will prompt you for the contents in DM.
         |
         |You can also give the spoiler inline (though someone might see it before it gets deleted):
         |```
         |$invocation The rabbit hole goes even deeper
         |```
         |You can add a short preview by starting the spoiler with "Hint:". This ends at the end of the line. It works in DM and inline.
         |```
         |$invocation Hint: Star Wars spoilers
         |Use the force, Luke!
         |```
      """.stripMargin

    override def execute(message: Message, args: String): Unit = {
      async {
        APIHelper.tryRequest(message.delete(),
          onFail = APIHelper.loudFailure("deleting a message", message.getChannel))

        args.trim match {
          case "" =>
            await(createSpoilerConversation(message))
          case trimmed =>
            await(createSpoiler(message.getChannel, message.getAuthor, trimmed))
        }
      }.failed.foreach(APIHelper.loudFailure("running spoiler command", message.getChannel))
    }

    private def createSpoilerConversation(message: Message) = {
      val channel = message.getChannel
      for {
        privateChannel <- message.getAuthor.openPrivateChannel().queueFuture()
        _ <- privateChannel.sendMessage(
          s"Please enter your spoiler contents for ${channel.mention}, or reply with 'cancel' to cancel."
        ).queueFuture()
      } yield {
        conversations.start(message.getAuthor, privateChannel) { conversation =>
          conversation.message.getContentRaw match {
            case "cancel" =>
              conversation.message.!("Did not create a spoiler.")
            case spoiler =>
              for (_ <- createSpoiler(channel, conversation.message.getAuthor, spoiler))
                conversation.message.!("Created your spoiler.")
          }
        }
      }
    }
  }

  private def createSpoiler(spoilerChannel: MessageChannel, author: User, args: String): Future[Unit] = {
    async {
      // Must be lowercase (to allow case insensitive string comparison)
      val hintPrefix = "hint:"
      val Array(hintText, spoilerText) =
        if (args.take(hintPrefix.length) equalsIgnoreCase hintPrefix) {
          (args drop hintPrefix.length).split("\n", 2)
        } else {
          Array("spoilers", args)
        }

      val spoilerMessage = await(spoilerChannel.sendOwned(BotMessages.okay(
        s"**Click the magnifying glass** to see ${hintText.trim} (from ${author.mentionWithName})"
      ), owner = author))

      val spoilerDbUpdate = {
        spoilerTexts(spoilerMessage.id) = spoilerText.trim
      }
      await(spoilerMessage.addReaction(spoilerEmote).queueFuture())
      await(spoilerDbUpdate)
    }
  }

  override def onEvent(event: GenericEvent): Unit = event match {
    case NonBotReact(React.Text(`spoilerEmote`), message, channel, user) =>
      val channelName = channel match {
        case ch: TextChannel => ch.getAsMention
        case ch => Option(ch.getName).getOrElse("unnamed group chat")
      }
      for {
        maybeText <- spoilerTexts.get(message).tap(_.failed.foreach(APIHelper.failure("displaying spoiler")))
        text <- maybeText
        privateChannel <- APIHelper.tryRequest(user.openPrivateChannel(),
          onFail = APIHelper.failure(s"opening private channel with ${user.unambiguousString}"))
      } {
        privateChannel.sendMessage(s"**Spoiler contents** from $channelName\n$text").queue()
      }
    case MessageDelete(id) => spoilerTexts.remove(id).failed.foreach(APIHelper.failure("removing spoiler"))
    case _ =>
  }
}
