package score.discord.canti.functionality

import cps.*
import cps.monads.FutureAsyncMonad
import net.dv8tion.jda.api.entities.{Message, User}
import net.dv8tion.jda.api.entities.channel.middleman.{GuildMessageChannel, MessageChannel}
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.{Button, ButtonStyle}
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import score.discord.canti.collections.{AsyncMap, ReplyCache}
import score.discord.canti.command.api.{
  ArgSpec, ArgType, CommandInvocation, CommandInvoker, CommandPermissions
}
import score.discord.canti.command.GenericCommand
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.{APIHelper, BotMessages}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.{ID, MessageReceiver, OutgoingMessage, RetrievableMessage}
import score.discord.canti.wrappers.jda.Conversions.{
  richChannel, richMessage, richMessageChannel, richRestAction, richSnowflake, richUser
}
import score.discord.canti.wrappers.jda.MessageConversions.*
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.matching.Events.{MessageDelete, NonBotReact}
import score.discord.canti.wrappers.jda.matching.React

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.chaining.*
import net.dv8tion.jda.api.entities.emoji.Emoji

class Spoilers(spoilerTexts: AsyncMap[ID[Message], String], conversations: Conversations)(using
  MessageOwnership,
  ReplyCache
) extends EventListener:
  private val logger = loggerOf[Spoilers]

  val spoilerEmote = "🔍"

  private val spoilerCommand = new GenericCommand:
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

    override def permissions = CommandPermissions.Anyone

    private val spoilerArg = ArgSpec("text", "Spoiler text", ArgType.GreedyString, required = false)

    override val argSpec = List(spoilerArg)

    override def canBeEdited = false

    override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
      async {
        for message <- ctx.invoker.originatingMessage do
          APIHelper.tryRequest(
            message.delete().nn,
            onFail = APIHelper.loudFailure("deleting a message", ctx.invoker.asMessageReceiver)
          )

        val futureSpoilerMessage =
          ctx.args.get(spoilerArg) match
            case None =>
              ctx.invoker.originatingMessage.fold {
                ctx.invoker.reply(
                  OutgoingMessage(
                    BotMessages
                      .error("You can't use this command as an application command (yet)")
                      .toMessageCreate,
                    ephemeral = true
                  )
                )
              } { _ =>
                createSpoilerConversation(ctx.invoker)
              }
            case Some(trimmed) =>
              createSpoiler(ctx.invoker.asMessageReceiver, ctx.invoker.user, trimmed)

        await(futureSpoilerMessage)
      }

    private def createSpoilerConversation(invoker: CommandInvoker): Future[RetrievableMessage] =
      val channel = invoker.channel
      for
        privateChannel <- invoker.user.openPrivateChannel().nn.queueFuture()
        message <- privateChannel
          .sendMessage({
            val channelText = channel.fold("")(c => s" for ${c.mention}")
            s"Please enter your spoiler contents$channelText, or reply with 'cancel' to cancel."
          })
          .nn
          .queueFuture()
        spoilerTextMessage <- conversations.awaitMessage(invoker.user, privateChannel)
      yield
        spoilerTextMessage.getContentRaw.nn.trimnn match
          case "cancel" | "" =>
            spoilerTextMessage ! "Did not create a spoiler."
          case spoiler =>
            for _ <- createSpoiler(invoker.asMessageReceiver, invoker.user, spoiler)
            do spoilerTextMessage ! "Created your spoiler."
        RetrievableMessage(message)
  end spoilerCommand

  val allCommands: Seq[GenericCommand] = Seq(spoilerCommand)

  private def createSpoiler(
    replyHook: MessageReceiver,
    author: User,
    args: String
  ): Future[RetrievableMessage] =
    async {
      // Must be lowercase (to allow case insensitive string comparison)
      val hintPrefix = "hint:"
      val (hintTextMaybe, spoilerText) =
        if args.take(hintPrefix.length) `equalsIgnoreCase` hintPrefix then
          val unprefixed = args.drop(hintPrefix.length)
          unprefixed.splitAt(unprefixed.indexOf('\n'))
        else ("", args)

      val hintText = if hintTextMaybe.isEmpty then "spoilers" else hintTextMaybe

      val spoilerMessageHook = await(
        replyHook.sendMessage(
          OutgoingMessage(
            MessageCreateBuilder()
              .setEmbeds(
                BotMessages
                  .okay(
                    s"""**Click the button** to see ${hintText.trim} (from ${author.mentionWithName})
                       |If the buttons are gone, you can react with a :mag: emoji.""".stripMargin
                  )
                  .build
              )
              .nn
              .setComponents(
                ActionRow.of(
                  Button.of(
                    ButtonStyle.PRIMARY,
                    "show-spoiler",
                    "Show spoiler",
                    Emoji.fromUnicode(spoilerEmote)
                  )
                )
              )
              .nn
              .build
              .nn
          )
        )
      )

      val spoilerMessage = await(spoilerMessageHook.retrieve())
      await(spoilerTexts(spoilerMessage.id) = spoilerText.trimnn)
      logger.info(s"Created spoiler ${spoilerMessage.id} on behalf of ${author.unambiguousString}")
      RetrievableMessage(spoilerMessage)
    }

  private def getChannelName(channel: MessageChannel) =
    val channelName = channel match
      case ch: GuildMessageChannel => ch.getAsMention
      case ch                      => Option(ch.getName).getOrElse("unnamed group chat")
    channelName

  override def onEvent(event: GenericEvent): Unit = event match
    case NonBotReact(React.Text(`spoilerEmote`), message, channel, user) =>
      val channelName = getChannelName(channel)
      for
        maybeText <- spoilerTexts
          .get(message)
          .tap(_.failed.foreach(APIHelper.failure("displaying spoiler")))
        text <- maybeText
        privateChannel <- APIHelper.tryRequest(
          user.openPrivateChannel().nn,
          onFail = APIHelper.failure(s"opening private channel with ${user.unambiguousString}")
        )
      do
        logger.debug(s"Sending spoiler id ${message.value} to ${user.unambiguousString}")
        privateChannel.sendMessage(s"**Spoiler contents** from $channelName\n$text").nn.queue()
    case MessageDelete(id) =>
      val futureRows = spoilerTexts.remove(id)
      futureRows.failed.foreach(APIHelper.failure("removing spoiler"))
      futureRows.foreach(r => if r != 0 then logger.info(s"Deleted spoiler $id"))
    case ev: GenericComponentInteractionCreateEvent =>
      if ev.getComponentId == "show-spoiler" then
        val msg = ev.getMessage.nn
        for
          maybeText <- spoilerTexts
            .get(msg.id)
            .tap(_.failed.foreach(APIHelper.failure("displaying spoiler")))
          text <- maybeText
        do
          logger.debug(s"Sending spoiler id ${msg.id.value} to ${ev.getUser.nn.unambiguousString}")
          ev.reply(text).nn.setEphemeral(true).nn.queue()
    case _ =>
end Spoilers
