package score.discord.canti.command

import cps.*
import score.discord.canti.util.FutureAsyncMonadButGood
import com.google.re2j.{PatternSyntaxException, Pattern as RE2JPattern}
import net.dv8tion.jda.api.entities.{GuildChannel, Message, MessageChannel}
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.components.{ActionRow, Button}
import net.dv8tion.jda.api.requests.restaction.MessageAction
import net.dv8tion.jda.api.{EmbedBuilder, JDA, MessageBuilder}
import score.discord.canti.collections.ReplyCache
import score.discord.canti.command.api.{ArgSpec, ArgType, CommandInvocation, CommandPermissions}
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.{APIHelper, BotMessages, MessageUtils}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.MessageConversions.given
import score.discord.canti.wrappers.jda.RetrievableMessage
import score.discord.canti.wrappers.jda.RichGenericComponentInteractionCreateEvent.messageId
import score.discord.canti.wrappers.jda.RichMessage.{!, guild}
import score.discord.canti.wrappers.jda.RichMessageChannel.{findMessage, participants}
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

class FindCommand(using messageOwnership: MessageOwnership, replyCache: ReplyCache)
    extends GenericCommand:
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

  override def permissions = CommandPermissions.Anyone

  private val arg =
    ArgSpec("search_terms", "Terms to search for (regex)", ArgType.GreedyString, required = true)

  override val argSpec = List(arg)

  override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
    async {
      val reply = makeSearchReply(ctx.invoker.channel, ctx.args(arg))
      await(ctx.invoker.reply(reply))
    }

  private def makeSearchReply(channel: MessageChannel, searchTerm: String): Message =
    val maxResults = 10
    val searchTermSanitised = MessageUtils.sanitiseCode(searchTerm)
    Try(RE2JPattern.compile(searchTerm, RE2JPattern.CASE_INSENSITIVE).nn)
      .map { searchPattern =>
        val results = getSearchResults(channel, searchPattern)
          .take(maxResults + 1)
          .zip(ReactListener.ICONS.iterator ++ Iterator.continually(""))
          .map { case ((msg, id), icon) => (s"$icon: $msg", id) }
          .toVector

        if results.isEmpty then
          BotMessages.plain(s"No results found for ``$searchTermSanitised``").toMessage
        else
          val header =
            if results.size > maxResults then
              s"__First $maxResults results for ``$searchTermSanitised``__"
            else if results.size == 1 then s"__Got one result for ``$searchTermSanitised``__"
            else s"__Got ${results.size} results for ``$searchTermSanitised``__"

          val footer =
            (if results.size > maxResults then "\n**...and more**\n"
             else "\n") + ReactListener.SEARCHABLE_MESSAGE_TAG

          val buttonRows =
            results
              .take(maxResults)
              .map(_._2)
              .zipWithIndex
              .map { case (id, index) =>
                Button.secondary(ReactListener.ACTION_PREFIX + id, index.toString)
              }
              .grouped(5)
              .map(buttons => ActionRow.of(buttons.asJava))
              .toSeq

          MessageBuilder(
            BotMessages
              .okay(s"$header\n${results take maxResults map (_._1) mkString "\n"}$footer")
          ).setActionRows(buttonRows*).build
      }
      .recover { case e: PatternSyntaxException =>
        BotMessages
          .error(s"Could not parse regex for $name command: ${e.getDescription}")
          .toMessage
      }
      .get
  end makeSearchReply

  private def getSearchResults(
    channel: MessageChannel,
    searchPattern: RE2JPattern
  ): Seq[(String, String)] =
    inline def containsSearchTerm(haystack: String) =
      searchPattern.matcher(haystack).nn.find()

    var results: Seq[(String, String)] = Vector.empty
    channel match
      case ch: GuildChannel =>
        val guild = ch.getGuild
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
              m.getNickname.?.exists(n => containsSearchTerm(s"@$n"))
          )
          .map(m =>
            val u = m.getUser
            val nick = m.getNickname.?.map(MessageUtils.sanitise)
              .fold("")(name => s" (aka $name)")
            (s"**User** ${u.mentionWithName}$nick: `${u.getId}`", u.getId)
          )
      case _ =>
        // Private chat
        results ++= channel.participants
          .filter(u => containsSearchTerm(s"@${u.name}#${u.discriminator}"))
          .map(u => (s"**User** ${u.mentionWithName}: `${u.getId}`", u.getId))
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
      for msg <- channel.findMessage(myMsgId, logFail = true)
      yield (for
        embed <- msg.getEmbeds.asScala
        description = embed.getDescription ?? ""
        if description.contains(SEARCHABLE_MESSAGE_TAG)
        case LINE_REGEX(`idLabel`, selected) <- description.splitnn("\n")
      yield (msg, selected)).headOption

    override def onEvent(event: GenericEvent): Unit = event match
      case NonBotReact(React.Text(react), msgId, channel, user) =>
        given JDA = event.getJDA
        if ICONS contains react then
          for
            case Some(`user`) <- messageOwnership(msgId)
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
          val id = rawId.drop(ACTION_PREFIX.length)
          for
            owner <- messageOwnership(ev.messageId)
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
