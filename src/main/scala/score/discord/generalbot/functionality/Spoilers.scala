package score.discord.generalbot.functionality

import net.dv8tion.jda.core.entities.{Message, MessageChannel, TextChannel, User}
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.exceptions.{ErrorResponseException, PermissionException}
import net.dv8tion.jda.core.hooks.EventListener
import score.discord.generalbot.collections.StringByMessage
import score.discord.generalbot.command.Command
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.{APIHelper, BotMessages}
import score.discord.generalbot.wrappers.Tap._
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.matching.Events.{MessageDelete, NonBotReact}
import score.discord.generalbot.wrappers.jda.matching.React

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Spoilers(spoilerTexts: StringByMessage, commands: Commands, conversations: Conversations)(implicit messageOwnership: MessageOwnership) extends EventListener {
  val spoilerEmote = "🔍"

  commands register new Command.ServerAdminDiscretion {
    override def name = "spoiler"

    override val aliases = List("sp", "spoil", "hide")

    override def description = "Hide a spoiler"

    override val longDescription =
      s"""Hides your message with a short bot message not revealing its contents.
         |Others can click the magnifying glass on the message to see its contents.
         |example usage:
         |```
         |${commands.prefix}$name
         |```
         |This will prompt you for the contents in DM.
         |
         |You can also give the spoiler inline (though someone might see it before it gets deleted):
         |```
         |${commands.prefix}$name The rabbit hole goes even deeper
         |```
         |You can add a short preview by starting the spoiler with "Hint:". This ends at the end of the line. It works in DM and inline.
         |```
         |${commands.prefix}$name Hint: Star Wars spoilers
         |Use the force, Luke!
         |```
      """.stripMargin

    override def execute(message: Message, args: String) {
      async {
        APIHelper.tryRequest(message.delete(), {
          case _: PermissionException =>
            message reply BotMessages.error("I don't have permission to delete messages here.")
          case e: ErrorResponseException =>
            message reply BotMessages.error(s"Error deleting your message. ${e.getMeaning}.")
          case e =>
            APIHelper.loudFailure("deleting a message", message.getChannel)(e)
        })

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
        myMessage <- privateChannel.sendMessage(
          s"Please enter your spoiler contents for ${channel.mention}, or reply with 'cancel' to cancel."
        ).queueFuture()
      } yield {
        conversations.start(message.getAuthor, privateChannel) { conversation =>
          conversation.message.getContentRaw match {
            case "cancel" =>
              conversation.message.reply("Did not create a spoiler.")
            case spoiler =>
              for (_ <- createSpoiler(channel, conversation.message.getAuthor, spoiler))
                conversation.message.reply("Created your spoiler.")
          }
        }
      }
    }

    override def getIdLong = -1145591283071885991L
  }

  private def createSpoiler(spoilerChannel: MessageChannel, author: User, args: String): Future[Unit] = {
    async {
      // Must be lowercase (to allow case insensitive string comparison)
      val hintPrefix = "hint:"
      val Array(hintText, spoilerText) =
        if (args.take(hintPrefix.length).toLowerCase == hintPrefix) {
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

  override def onEvent(event: Event): Unit = event match {
    case NonBotReact(React.Text(`spoilerEmote`), message, channel, user) =>
      val channelName = channel match {
        case ch: TextChannel => ch.getAsMention
        case ch => Option(ch.getName).getOrElse("unnamed group chat")
      }
      for {
        maybeText <- spoilerTexts(message).tap(_.failed.foreach(APIHelper.failure("displaying spoiler")))
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
