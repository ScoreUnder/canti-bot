package score.discord.generalbot.command

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import score.discord.generalbot.collections.ReplyCache
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.{BotMessages, MessageUtils}
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FindCommand(implicit val messageOwnership: MessageOwnership, val replyCache: ReplyCache) extends Command.Anyone with ReplyingCommand {
  override def name: String = "find"

  override val aliases: Seq[String] = List("id")

  override def description: String = "Finds a role, user or emoji by name"

  override def longDescription(invocation: String) =
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
    val results = getSearchResults(message, searchTerm).take(maxResults + 1).toVector
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
        if (results.size > maxResults)
          "\n**...and more**"
        else
          ""

      BotMessages.okay(s"$header\n${results take maxResults mkString "\n"}$footer")
    }
  }

  private def getSearchResults(message: Message, origSearchTerm: String): Seq[String] = {
    val searchTerm = origSearchTerm.toLowerCase.toUpperCase

    def containsSearchTerm(haystack: String) =
      haystack.toLowerCase.toUpperCase.contains(searchTerm)

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
}
