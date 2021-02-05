package score.discord.canti.command

import com.google.re2j.{PatternSyntaxException, Pattern => RE2JPattern}
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.{EmbedBuilder, JDA}
import score.discord.canti.collections.ReplyCache
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.{APIHelper, BotMessages, MessageUtils}
import score.discord.canti.wrappers.jda.Conversions._
import score.discord.canti.wrappers.jda.matching.Events.NonBotReact
import score.discord.canti.wrappers.jda.matching.React

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Try

class FindCommand(implicit val messageOwnership: MessageOwnership, val replyCache: ReplyCache) extends Command.Anyone with ReplyingCommand {
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

  override def executeAndGetMessage(message: Message, args: String): Future[Message] =
    Future {
      (args.trim match {
        case "" => BotMessages.error("Please enter a term to search for.")
        case searchTerm => makeSearchReply(message, searchTerm)
      }).toMessage
    }

  private def makeSearchReply(message: Message, searchTerm: String): EmbedBuilder = {
    val maxResults = 10
    val searchTermSanitised = MessageUtils.sanitiseCode(searchTerm)
    Try(RE2JPattern.compile(searchTerm, RE2JPattern.CASE_INSENSITIVE))
      .map { searchPattern =>
        val results = getSearchResults(message, searchPattern)
          .take(maxResults + 1)
          .zip(ReactListener.ICONS.iterator ++ Iterator.continually(""))
          .map { case (msg, icon) => s"$icon: $msg" }
          .toVector

        if (results.isEmpty) {
          BotMessages.plain(s"No results found for ``$searchTermSanitised``")
        } else {
          val header =
            if (results.size > maxResults)
              s"__First $maxResults results for ``$searchTermSanitised``__"
            else if (results.size == 1)
              s"__Got one result for ``$searchTermSanitised``__"
            else
              s"__Got ${results.size} results for ``$searchTermSanitised``__"

          val footer =
            (if (results.size > maxResults)
              "\n**...and more**\n"
            else
              "\n") + ReactListener.SEARCHABLE_MESSAGE_TAG

          BotMessages.okay(s"$header\n${results take maxResults mkString "\n"}$footer")
        }
      }
      .recover {
        case e: PatternSyntaxException =>
          BotMessages.error(s"Could not parse regex for $name command: ${e.getDescription}")
      }
      .get
  }

  private def getSearchResults(message: Message, searchPattern: RE2JPattern): Seq[String] = {
    @inline def containsSearchTerm(haystack: String) =
      searchPattern.matcher(haystack).find()

    var results: Seq[String] = Vector.empty
    message.guild match {
      case None =>
        // Private chat
        results ++= message.getChannel.participants
          .filter(u => containsSearchTerm(s"@${u.name}#${u.discriminator}"))
          .map(u => s"**User** ${u.mentionWithName}: `${u.getId}`")
      case Some(guild) =>
        results ++= guild.getRoles.asScala.view
          .filter(r => containsSearchTerm(s"@${r.getName}"))
          .map(r => s"**Role** ${r.getAsMention} (${MessageUtils.sanitise(s"@${r.getName}")}): `${r.getId}`")
        results ++= guild.getEmotes.asScala.view
          .filter(e => containsSearchTerm(s":${e.getName}:"))
          .map(e => s"**Emote** ${e.getAsMention} (:${e.getName}:): `${e.getId}`")
        results ++= guild.getMembers.asScala.view
          .filter(m =>
            containsSearchTerm(s"@${m.getUser.name}#${m.getUser.discriminator}") ||
              Option(m.getNickname).exists(n => containsSearchTerm(s"@$n")))
          .map(m => {
            val u = m.getUser
            val nick = Option(m.getNickname)
              .map(MessageUtils.sanitise)
              .map(name => s" (aka $name)")
              .getOrElse("")
            s"**User** ${u.mentionWithName}$nick: `${u.getId}`"
          })
    }
    results
  }

  object ReactListener extends EventListener {
    val ICONS = Vector("0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "\uD83D\uDD1F")
    val SEARCHABLE_MESSAGE_TAG = "React with one of the icons above to make it easier to copy the ID on mobile"
    private val LINE_REGEX = (ICONS.mkString("(", "|", ")") + ":.*`(\\d+)`").r.unanchored

    override def onEvent(event: GenericEvent): Unit = event match {
      case NonBotReact(React.Text(react), msgId, channel, user) =>
        implicit val jda: JDA = event.getJDA
        if (ICONS contains react) {
          for {
            Some(`user`) <- messageOwnership(msgId)
            msg <- APIHelper.tryRequest(channel.retrieveMessageById(msgId.value), onFail = APIHelper.failure("retrieving reacted message"))
            embed <- msg.getEmbeds.asScala
            if embed.getDescription.contains(SEARCHABLE_MESSAGE_TAG)
            LINE_REGEX(`react`, selected) <- embed.getDescription.split("\n")
          } {
            APIHelper.tryRequest(msg.editMessage(selected), onFail = APIHelper.failure("editing message for reaction"))
          }
        }
      case _ =>
    }
  }
}