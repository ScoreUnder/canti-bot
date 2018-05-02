package score.discord.generalbot.command

import net.dv8tion.jda.core.entities.Message
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util.{APIHelper, BotMessages, MessageUtils}
import score.discord.generalbot.wrappers.jda.Conversions._

import scala.collection.GenIterable
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FindCommand(implicit messageOwnership: MessageOwnership) extends Command.Anyone {
  override def name: String = "find"

  override val aliases: GenIterable[String] = List("id")

  override def description: String = "Finds a role, user or emoji by name"

  override def execute(message: Message, args: String): Unit = {
    Future {
      val origSearchTerm = args.trim
      val searchTerm = origSearchTerm.toLowerCase.toUpperCase
      if (searchTerm.isEmpty) {
        message reply BotMessages.error("Please enter a term to search for.")
        return
      }

      def containsSearchTerm(haystack: String) =
        haystack.toLowerCase.toUpperCase.contains(searchTerm)

      var results: Seq[String] = Nil.view
      message.getGuild match {
        case null =>
          // Private chat
          results ++= message.getChannel.participants
            .filter(u => containsSearchTerm(s"@${u.name}#${u.discriminator}"))
            .map(u => s"**User** ${u.mentionWithName}: `${u.getId}`")
        case guild =>
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
            .map(m => s"**User** ${m.getUser.mentionWithName}: `${m.getUser.getId}`")
          results ++= guild.getMembers.asScala.view
            .filter(m => Option(m.getGame).exists(g => containsSearchTerm(g.getName)))
            .map { m =>
              val game = MessageUtils.sanitise(m.getGame.getName)
              s"**Game** ${m.getUser.mentionWithName} playing $game"
            }
      }
      val maxResults = 5
      val searchTermSanitised = MessageUtils.sanitise(origSearchTerm)
      results = results.take(maxResults + 1).toVector
      if (results.isEmpty) {
        message reply BotMessages.plain(s"No results found for `$searchTermSanitised`")
      } else {
        val header =
          if (results.size > maxResults)
            s"__First $maxResults results for `$searchTermSanitised`__"
          else
            s"__Got ${results.size} results for `$searchTermSanitised`__"

        message reply BotMessages.okay(s"$header\n${results take 5 mkString "\n"}")
      }
    }.failed.foreach(APIHelper.loudFailure("searching for entities", message.getChannel))
  }
}
