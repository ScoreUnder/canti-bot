package score.discord.canti.command

import com.google.re2j.{PatternSyntaxException, Pattern as RE2JPattern}
import net.dv8tion.jda.api.entities.{Message, MessageChannel}
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.components.{ActionRow, Button}
import net.dv8tion.jda.api.requests.restaction.MessageAction
import net.dv8tion.jda.api.{EmbedBuilder, JDA}
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.{APIHelper, BotMessages, MessageUtils}
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.RichMessage.{!, guild}
import score.discord.canti.wrappers.jda.RichMessageChannel.participants
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichUser.{discriminator, mentionWithName, name}
import score.discord.canti.wrappers.jda.matching.Events.NonBotReact
import score.discord.canti.wrappers.jda.matching.React

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions
import scala.util.Try
import scala.util.chaining.scalaUtilChainingOps

class FindCommand(using val messageOwnership: MessageOwnership, val replyCache: ReplyCache)
    extends Command.Anyone
    with DataReplyingCommand[Seq[String]]:
  override def name: String = "find"

  override val aliases: Seq[String] = List("id")

  override def description: String = "Finds a role, user or emoji by name"

  override def longDescription(invocation: String): String =
    s"""Usage:
       |`$invocation mod`
       |This might find the moderator roles on the server.
       |`$invocation score`
       |This might find users called score in the server.
       |`$invocation blob`
       |This might find blob emotes on the server.
       |
       |So far, this searches roles, emotes, users, and games.
    """.stripMargin

  override def executeAndGetMessageWithData(
    message: Message,
    args: String
  ): Future[(Message, Seq[String])] =
    Future {
      args.trim match
        case "" => (BotMessages.error("Please enter a term to search for.").toMessage, Nil)
        case searchTerm =>
          makeSearchReply(message, searchTerm).pipe { case (x, y) => (x.toMessage, y) }
    }

  private def makeSearchReply(message: Message, searchTerm: String): (EmbedBuilder, Seq[String]) =
    val maxResults = 10
    val searchTermSanitised = MessageUtils.sanitiseCode(searchTerm)
    Try(RE2JPattern.compile(searchTerm, RE2JPattern.CASE_INSENSITIVE))
      .map { searchPattern =>
        val results = getSearchResults(message, searchPattern)
          .take(maxResults + 1)
          .zip(ReactListener.ICONS.iterator ++ Iterator.continually(""))
          .map { case ((msg, id), icon) => (s"$icon: $msg", id) }
          .toVector

        if results.isEmpty then
          (BotMessages.plain(s"No results found for ``$searchTermSanitised``"), Nil)
        else
          val header =
            if results.size > maxResults then
              s"__First $maxResults results for ``$searchTermSanitised``__"
            else if results.size == 1 then s"__Got one result for ``$searchTermSanitised``__"
            else s"__Got ${results.size} results for ``$searchTermSanitised``__"

          val footer =
            (if results.size > maxResults then "\n**...and more**\n"
             else "\n") + ReactListener.SEARCHABLE_MESSAGE_TAG

          (
            BotMessages.okay(
              s"$header\n${results take maxResults map (_._1) mkString "\n"}$footer"
            ),
            results.take(maxResults).map(_._2)
          )
      }
      .recover { case e: PatternSyntaxException =>
        (BotMessages.error(s"Could not parse regex for $name command: ${e.getDescription}"), Nil)
      }
      .get

  override def tweakMessageAction(action: MessageAction, data: Seq[String]): MessageAction =
    action.setActionRows(
      data.zipWithIndex
        .map { case (id, index) =>
          Button.secondary(ReactListener.ACTION_PREFIX + id, index.toString)
        }
        .grouped(5)
        .map(buttons => ActionRow.of(buttons.asJava))
        .toSeq
        .asJava
    )

  private def getSearchResults(
    message: Message,
    searchPattern: RE2JPattern
  ): Seq[(String, String)] =
    @inline def containsSearchTerm(haystack: String) =
      searchPattern.matcher(haystack).find()

    var results: Seq[(String, String)] = Vector.empty
    message.guild match
      case None =>
        // Private chat
        results ++= message.getChannel.participants
          .filter(u => containsSearchTerm(s"@${u.name}#${u.discriminator}"))
          .map(u => (s"**User** ${u.mentionWithName}: `${u.getId}`", u.getId))
      case Some(guild) =>
        results ++= guild.getRoles.asScala.view
          .filter(r => containsSearchTerm(s"@${r.getName}"))
          .map(r =>
            (
              s"**Role** ${r.getAsMention} (${MessageUtils.sanitise(s"@${r.getName}")}): `${r.getId}`",
              r.getId
            )
          )
        results ++= guild.getEmotes.asScala.view
          .filter(e => containsSearchTerm(s":${e.getName}:"))
          .map(e => (s"**Emote** ${e.getAsMention} (:${e.getName}:): `${e.getId}`", e.getId))
        results ++= guild.getMembers.asScala.view
          .filter(m =>
            containsSearchTerm(s"@${m.getUser.name}#${m.getUser.discriminator}") ||
              Option(m.getNickname).exists(n => containsSearchTerm(s"@$n"))
          )
          .map(
          m =>
            val u = m.getUser
            val nick = Option(m.getNickname)
              .map(MessageUtils.sanitise)
              .fold("")(name => s" (aka $name)")
            (s"**User** ${u.mentionWithName}$nick: `${u.getId}`", u.getId)
          )
    results

  object ReactListener extends EventListener:
    val ICONS =
      Vector("0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "\uD83D\uDD1F")
    val ACTION_PREFIX = "show_result:"
    val SEARCHABLE_MESSAGE_TAG =
      "React with one of the icons above to make it easier to copy the ID on mobile"
    private val LINE_REGEX = (ICONS.mkString("(", "|", ")") + ":.*`(\\d+)`").r.unanchored

    def getIdFromMessage(
      channel: MessageChannel,
      myMsgId: ID[Message],
      idLabel: String
    ): Future[Option[(Message, String)]] =
      for
        msg <- APIHelper.tryRequest(
          channel.retrieveMessageById(myMsgId.value),
          onFail = APIHelper.failure("retrieving reacted message")
        )
      yield (for
        embed <- msg.getEmbeds.asScala
        if embed.getDescription.contains(SEARCHABLE_MESSAGE_TAG)
        LINE_REGEX(`idLabel`, selected) <- embed.getDescription.split("\n")
      yield (msg, selected)).headOption

    override def onEvent(event: GenericEvent): Unit = event match
      case NonBotReact(React.Text(react), msgId, channel, user) =>
        given JDA = event.getJDA
        if ICONS contains react then
          for
            Some(`user`) <- messageOwnership(msgId)
            maybeMsgId <- getIdFromMessage(channel, msgId, react)
            msgId <- maybeMsgId
            (msg, selected) = msgId
          do
            APIHelper.tryRequest(
              msg.editMessage(selected),
              onFail = APIHelper.failure("editing message for reaction")
            )
      case ev: ButtonClickEvent =>
        given JDA = event.getJDA
        val rawId = ev.getComponentId
        if rawId.startsWith(ACTION_PREFIX) then
          val id = rawId.substring(ACTION_PREFIX.length)
          for
            owner <- messageOwnership(ID[Message](ev.getMessageIdLong))
            if id.forall(c =>
              c.isDigit || c == '-'
            ) // Sanity check for potential exploits that probably don't exist
          do
            val user = ev.getUser
            owner match
              case Some(`user`) => ev.editMessage(id).queueFuture()
              case _            => ev.reply(id).setEphemeral(true).queueFuture()
      case _ =>
  end ReactListener
end FindCommand
