package score.discord.generalbot.functionality

import net.dv8tion.jda.core.entities.{Message, TextChannel}
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

class Spoilers(spoilerTexts: StringByMessage, commands: Commands)(implicit messageOwnership: MessageOwnership) extends EventListener {
  val spoilerEmote = "🔍"

  commands register new Command.ServerAdminDiscretion {
    override def name = "spoiler"

    override val aliases = List("sp", "spoil", "hide")

    override def description = "Hide a spoiler"

    override val longDescription =
      s"""Deletes your message and then replaces it with a short message not revealing its contents.
         |Others can click the magnifying glass on the message to see its contents.
         |example usage:
         |```
         |${commands.prefix}$name The rabbit hole goes even deeper
         |```
         |Or, you can give a short preview by starting the spoiler with "Hint:". This ends at the end of the line.
         |```
         |${commands.prefix}$name Hint: Star Wars spoilers
         |Use the force, Luke!
         |```
      """.stripMargin

    override def execute(message: Message, args: String) {
      async {
        // Must be lowercase (to allow case insensitive string comparison)
        val hintPrefix = "hint:"
        val Array(hintText, spoilerText) =
          if (args.take(hintPrefix.length).toLowerCase == hintPrefix) {
            (args drop hintPrefix.length).split("\n", 2)
          } else {
            Array("spoilers", args)
          }

        APIHelper.tryRequest(message.delete(), {
          case _: PermissionException =>
            message reply BotMessages.error("I don't have permission to delete messages here.")
          case e: ErrorResponseException =>
            message reply BotMessages.error(s"Error deleting your message. ${e.getMeaning}.")
          case e =>
            APIHelper.loudFailure("deleting a message", message.getChannel)(e)
        })

        val spoilerMessage = await(message.reply(BotMessages.okay(
          s"**Click the magnifying glass** to see ${hintText.trim} (from ${message.getAuthor.mentionWithName})"
        )))

        val spoilerDbUpdate = {
          spoilerTexts(spoilerMessage.id) = spoilerText.trim
        }
        await(spoilerMessage.addReaction(spoilerEmote).queueFuture())
        await(spoilerDbUpdate)
      }.failed.foreach(APIHelper.loudFailure("running spoiler command", message.getChannel))
    }

    override def getIdLong = -1145591283071885991L
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
