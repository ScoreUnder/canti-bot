package score.discord.canti.command

import cps.*
import cps.monads.FutureAsyncMonad
import com.google.re2j.{Pattern as RE2JPattern, PatternSyntaxException}
import net.dv8tion.jda.api.entities.{Guild, Message}
import net.dv8tion.jda.api.entities.channel.middleman.{GuildChannel, MessageChannel}
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.{EmbedBuilder, JDA}
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
import score.discord.canti.wrappers.jda.RichMessageChannel.findMessage
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
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import scala.collection.mutable.Buffer
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel

class FindCommand(using messageOwnership: MessageOwnership, replyCache: ReplyCache)
    extends GenericCommand:
  private val logger = loggerOf[FindCommand]

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
      val guild = ctx.invoker.member.toOption.map(_.getGuild.nn)
      val reply = makeSearchReply(ctx.invoker.channel, guild, ctx.args(arg))
      await(ctx.invoker.reply(reply))
    }

  private def makeSearchReply(
    channel: Option[MessageChannel],
    guild: Option[Guild],
    searchTerm: String
  ): MessageCreateData =
    val maxResults = 10
    val searchTermSanitised = MessageUtils.sanitiseCode(searchTerm)
    Try(RE2JPattern.compile(searchTerm, RE2JPattern.CASE_INSENSITIVE).nn)
      .map { searchPattern =>
        val results = getSearchResults(channel, guild, searchPattern)
          .take(maxResults + 1)
          .zip(ReactListener.ICONS.iterator ++ Iterator.continually(""))
          .map { case ((msg, id), icon) => (s"$icon: $msg", id) }
          .toVector

        if results.isEmpty then
          BotMessages.plain(s"No results found for ``$searchTermSanitised``").toMessageCreate
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
                Button.secondary(ReactListener.ACTION_PREFIX + id, index.toString).nn
              }
              .grouped(5)
              .map(buttons => ActionRow.of(buttons.asJava))
              .toSeq

          MessageCreateBuilder().setEmbeds(
            BotMessages
              .okay(s"$header\n${results take maxResults map (_._1) mkString "\n"}$footer")
              .build
          ).nn.setComponents(buttonRows*).nn.build.nn
      }
      .recover { case e: PatternSyntaxException =>
        BotMessages
          .error(s"Could not parse regex for $name command: ${e.getDescription}")
          .toMessageCreate
      }
      .get
  end makeSearchReply

  private def getSearchResults(
    channel: Option[MessageChannel],
    guild: Option[Guild],
    searchPattern: RE2JPattern
  ): Seq[(String, String)] =
    inline def containsSearchTerm(haystack: String) =
      searchPattern.matcher(haystack).nn.find()

    var results: Buffer[(String, String)] = Buffer.empty
    guild match
      case Some(guild) =>
        results ++= guild.getRoles.nn.asScala.view
          .filter(r => containsSearchTerm(s"@${r.getName}"))
          .map(r =>
            (
              s"**Role** ${r.getAsMention} (${MessageUtils.sanitise(s"@${r.getName}")}): `${r.getId}`",
              r.getId.nn
            )
          )
        results ++= guild.getEmojis.nn.asScala.view
          .filter(e => containsSearchTerm(s":${e.getName}:"))
          .map(e => (s"**Emote** ${e.getAsMention} (:${e.getName}:): `${e.getId}`", e.getId.nn))
        results ++= guild.getMembers.nn.asScala.view
          .filter(m =>
            containsSearchTerm(s"@${m.getUser.nn.name}#${m.getUser.nn.discriminator}") ||
              m.getNickname.?.exists(n => containsSearchTerm(s"@$n"))
          )
          .map(m =>
            val u = m.getUser.nn
            val nick = m.getNickname.?.map(MessageUtils.sanitise)
              .fold("")(name => s" (aka $name)")
            (s"**User** ${u.mentionWithName}$nick: `${u.getId}`", u.getId.nn)
          )
      case None =>
        // Private chat
        channel match
          case Some(channel: PrivateChannel) =>
            results ++= List(channel.getUser.nn)
              .filter(u => containsSearchTerm(s"@${u.name}#${u.discriminator}"))
              .map(u => (s"**User** ${u.mentionWithName}: `${u.getId}`", u.getId.nn))
          case None =>
            logger.warn("Not sure where I am (running find command outside of channel and guild)")
            results ++= Vector.fill(10)(("Where am I?", "???")) // Error or creepypasta?
    results.toSeq

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
        embed <- msg.getEmbeds.nn.asScala
        description = embed.getDescription ?? ""
        if description.contains(SEARCHABLE_MESSAGE_TAG)
        case LINE_REGEX(`idLabel`, selected) <- description.splitnn("\n")
      yield (msg, selected)).headOption

    override def onEvent(event: GenericEvent): Unit = event match
      case NonBotReact(React.Text(react), msgId, channel, user) =>
        given JDA = event.getJDA.nn
        if ICONS contains react then
          for
            case Some(`user`) <- messageOwnership(msgId)
            maybeMsgId <- getIdFromMessage(channel, msgId, react)
            msgId <- maybeMsgId
            (msg, selected) = msgId
          do
            APIHelper.tryRequest(
              msg.editMessage(selected).nn,
              onFail = APIHelper.failure("editing message for reaction")
            )
      case ev: ButtonInteractionEvent =>
        given JDA = event.getJDA.nn
        val rawId = ev.getComponentId.nn
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
              case Some(`user`) => ev.editMessage(id).nn.queueFuture()
              case _            => ev.reply(id).nn.setEphemeral(true).nn.queueFuture()
      case _ =>
  end ReactListener
end FindCommand
