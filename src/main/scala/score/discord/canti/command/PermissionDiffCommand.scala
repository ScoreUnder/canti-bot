package score.discord.canti.command

import cps.*
import net.dv8tion.jda.api.{EmbedBuilder, JDA, Permission}
import net.dv8tion.jda.api.entities.{GuildChannel, PermissionOverride, Role, User}
import score.discord.canti.command.api.{ArgSpec, ArgType, CommandInvocation, CommandPermissions}
import score.discord.canti.util.BotMessages
import score.discord.canti.util.MessageUtils.{quote, sanitiseCode}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.{ID, MessageConversions, RetrievableMessage}
import score.discord.canti.wrappers.jda.Conversions.richUser
import score.discord.canti.wrappers.jda.IdConversions.*
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global
import score.discord.canti.util.FutureAsyncMonadButGood
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions
import scala.util.chaining.*

class PermissionDiffCommand extends GenericCommand:
  override def name = "permissiondiff"

  override def description = "Find the difference between two channels' permissions"

  override def aliases = List("permdiff", "pd")

  override def permissions = CommandPermissions.ServerAdminOnly

  private val baseChannelArg =
    ArgSpec("base_channel", "The channel to start comparison with", ArgType.Channel)
  private val compareChannelArg =
    ArgSpec("compare_channel", "The channel to compare against the base", ArgType.Channel)
  override val argSpec = List(baseChannelArg, compareChannelArg)

  override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
    async {
      val baseChannel = ctx.args(baseChannelArg)
      val compareChannel = ctx.args(compareChannelArg)
      val user = ctx.invoker.user
      val result =
        if !user.canSee(baseChannel) then Left("You cannot see the first channel")
        else if !user.canSee(compareChannel) then Left("You cannot see the second channel")
        else Right(compareChannels(baseChannel, compareChannel))

      given JDA = ctx.jda
      await {
        result
          .fold(BotMessages.error(_), makeDiffMessage(baseChannel, compareChannel, _))
          .pipe(ctx.invoker.reply(_))
      }
    }

  private def makeDiffMessage(baseChannel: GuildChannel, compareChannel: GuildChannel, diffs: PermissionDiffs)(
    using JDA
  ) =
    val embed = BotMessages.plain(
      s"Difference between permissions on ${baseChannel.getAsMention} and ${compareChannel.getAsMention}"
    )
    diffs.toEmbed(embed)
    embed

  sealed trait PermissionHolder:
    def asMention: String

  final case class Role(id: ID[Role]) extends PermissionHolder:
    def asMention = s"<@&${id.value}>"
  final case class Member(id: ID[User]) extends PermissionHolder:
    def asMention = s"<@${id.value}>"
  final case class Unknown(id: ID[?]) extends PermissionHolder:
    def asMention = toString

  object PermissionHolder:
    def apply(permissionOverride: PermissionOverride): PermissionHolder =
      if permissionOverride.isMemberOverride then Member(ID(permissionOverride.getIdLong))
      else if permissionOverride.isRoleOverride then Role(ID(permissionOverride.getIdLong))
      else Unknown(ID(permissionOverride.getIdLong))

  enum PermissionValue:
    case Allow
    case Inherit
    case Deny

  final case class PermissionDiffs(
    removedPerms: Map[PermissionHolder, Map[Permission, PermissionValue]],
    addedPerms: Map[PermissionHolder, Map[Permission, PermissionValue]],
    changedPerms: Map[PermissionHolder, Map[Permission, (PermissionValue, PermissionValue)]]
  ):
    private def permHoldersStr[T](
      ph: Map[PermissionHolder, Map[Permission, T]]
    )(stringify: T => String)(using JDA) =
      if ph.isEmpty then "None"
      else
        ph.view
          .map { case (holder, changes) =>
            val changesStrs =
              changes.view.map { case (perm, v) =>
                s"$perm: ${stringify(v)}"
              }
            holder.asMention + changesStrs.map(sanitiseCode).mkString("\n```\n", "\n", "```")
          }
          .mkString("\n")

    def toEmbed(embed: EmbedBuilder)(using JDA) =
      embed.addField("Removed overrides", permHoldersStr(removedPerms)(_.toString), false)
      embed.addField("Added overrides", permHoldersStr(addedPerms)(_.toString), false)
      embed.addField(
        "Changed overrides",
        permHoldersStr(changedPerms) { case (oldV, newV) => s"$oldV -> $newV" },
        false
      )

  private def permOverrideToMap(
    permOverride: PermissionOverride
  ): Map[Permission, PermissionValue] =
    import PermissionValue.*
    (permOverride.getAllowed.asScala.view.map(_ -> Allow) ++
      permOverride.getDenied.asScala.view.map(_ -> Deny)).toMap.withDefaultValue(Inherit)

  private def compareChannels(baseChannel: GuildChannel, compareChannel: GuildChannel) =
    def permMap(ch: GuildChannel) =
      ch.getPermissionOverrides().asScala.view.map(p => PermissionHolder(p) -> p).toMap
    val perms1 = permMap(baseChannel)
    val perms2 = permMap(compareChannel)

    val changedOrSamePerms = perms1.keySet & perms2.keySet
    val removedPerms = perms1 -- changedOrSamePerms
    val addedPerms = perms2 -- changedOrSamePerms

    val changedPerms =
      changedOrSamePerms
        .map { p =>
          val perm1 = permOverrideToMap(perms1(p))
          val perm2 = permOverrideToMap(perms2(p))

          val interestingKeys =
            (perm1.keySet | perm2.keySet).filter(p => perm1(p) != perm2(p))

          p -> interestingKeys.map(k => k -> (perm1(k) -> perm2(k))).toMap
        }
        .filter { case (_, changes) =>
          changes.nonEmpty
        }
        .toMap

    PermissionDiffs(
      removedPerms.view.mapValues(permOverrideToMap).toMap,
      addedPerms.view.mapValues(permOverrideToMap).toMap,
      changedPerms
    )
end PermissionDiffCommand
