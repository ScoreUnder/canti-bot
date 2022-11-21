package score.discord.canti.functionality

import cps.*
import cps.monads.FutureAsyncMonad
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.{GenericEvent, ReadyEvent}
import net.dv8tion.jda.api.exceptions.{InsufficientPermissionException, PermissionException}
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.{CommandInteraction, OptionType}
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_CHANNEL
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import net.dv8tion.jda.api.{JDA, Permission}
import score.discord.canti.collections.{AsyncMap, ReplyCache}
import score.discord.canti.command.api.{
  ArgSpec, ArgType, CommandInvocation, CommandInvoker, CommandPermissions
}
import score.discord.canti.command.GenericCommand
import score.discord.canti.discord.permissions.PermissionHolder.asPermissionHolder
import score.discord.canti.discord.permissions.{
  PermissionAttachment, PermissionCollection, PermissionHolder
}
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.APIHelper.Error
import score.discord.canti.util.*
import score.discord.canti.wrappers.FutureEither.*
import score.discord.canti.wrappers.collections.AsyncMapConversions.*
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.Scheduler
import score.discord.canti.wrappers.jda.Conversions.{
  richChannelAction, richGuildChannel, richMember, richUser, richVoiceChannel
}
import score.discord.canti.wrappers.jda.{ID, MessageReceiver, RetrievableMessage}
import score.discord.canti.wrappers.jda.IdConversions.*
import score.discord.canti.wrappers.jda.MessageConversions.{MessageFromX, given}
import score.discord.canti.wrappers.jda.RichGuild.{findMember, findVoiceChannel}
import score.discord.canti.wrappers.jda.RichMessage.!
import score.discord.canti.wrappers.jda.RichRestAction.queueFuture
import score.discord.canti.wrappers.jda.RichSnowflake.{id, rawId}
import score.discord.canti.wrappers.jda.matching.Events.GuildVoiceUpdate

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Future, Promise}
import scala.jdk.CollectionConverters.*
import scala.language.{implicitConversions, postfixOps}
import scala.util.chaining.*
import scala.util.{Failure, Success, Try}
import scala.annotation.threadUnsafe

class PrivateVoiceChats(
  ownerByChannel: AsyncMap[(ID[Guild], ID[VoiceChannel]), ID[User]],
  defaultCategoryByGuild: AsyncMap[ID[Guild], ID[GuildChannel]],
  commands: Commands,
  eventWaiter: EventWaiter,
)(using messageOwnership: MessageOwnership, replyCache: ReplyCache)(using Scheduler)
    extends EventListener:
  private val logger = loggerOf[PrivateVoiceChats]

  private val invites = ConcurrentHashMap[GuildUserId, Invite]()

  private type Timestamp = Long

  private case class Invite(from: ID[User], channel: ID[VoiceChannel], expiry: Timestamp):
    def valid: Boolean = System.currentTimeMillis() < expiry

  private case class VoiceMove(id: ID[Member], guild: ID[Guild])

  private val maxNameLen = 100

  object AcceptCommand extends GenericCommand:
    override def name = "accept"

    override def aliases = List("acc", "accpet")

    override def description = "Accept another user's invitation to join a voice channel"

    override val permissions = CommandPermissions.Anyone

    override val argSpec = Nil

    override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
      def ensureInviteValid(inv: Invite) =
        Either.cond(inv.valid, inv, "Your last invite expired. Please ask for another.")

      val result = for
        member <- ctx.invoker.member
        guild = member.getGuild
        inv <- invites.get(GuildUserId(member)) ?<>
          "You don't have any pending voice chat invitations."
        _ <- ensureInviteValid(inv)
        voiceChannel <- guild
          .findVoiceChannel(inv.channel)
          .toRight("The voice channel you were invited to no longer exists.")
        memberName = member.getEffectiveName
        voiceMention = s"<#${voiceChannel.rawId}>"
      yield APIHelper.tryRequest(guild.moveVoiceMember(member, voiceChannel)).transformWith {
        case Success(_) =>
          invites.remove(GuildUserId(member))
          ctx.invoker.reply(
            BotMessages
              .okay(s"Moved you into the $voiceMention channel.")
              .setTitle(s"$memberName: Success!", null)
          )
        case Failure(ex) =>
          sendErrorOrRetry(member, ctx, this)(ex)
      }

      result.fold(ctx.invoker.reply(_), x => x)
  end AcceptCommand

  object InviteCommand extends GenericCommand:
    override def name = "invite"

    override val aliases = List("inv")

    override def description = "Ask another user to join your current voice channel"

    override def longDescription(invocation: String): String =
      s"""Usage:
         |`$invocation @person1 @person2`
         |Invites the mentioned parties, in this case person1 and person2, to your current voice channel.
       """.stripMargin.trimnn

    lazy val inviteMessage: String = s"Please join a voice channel and " +
      s"type `${commands.prefix}${AcceptCommand.name}` to accept this invitation."

    val permInviteMessage: String = s"You may now enter the channel."

    override val permissions = CommandPermissions.Anyone

    private val userArg = ArgSpec("user", "The user to invite", ArgType.MentionedUsers)

    override val argSpec = List(userArg)

    def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
      val response = for
        member <- ctx.invoker.member
        guild = member.getGuild
        voiceState <- member.getVoiceState ?<> "Internal error: no voice state cached for you"
        chan <- voiceState.getChannel ?<> "You must be in voice chat to use this command."
        success <- ctx.args(userArg) match
          case Seq() => Left("You must mention the users you want to join you in voice chat.")
          case Seq(mentions*) =>
            assert(guild == chan.getGuild)
            Right(inviteUsers(channel = chan, inviter = member.getUser, invitees = mentions))
      yield success

      response
        .pipe(x => eitherToFutureMessage(x))
        .flatMap(ctx.invoker.reply(_))

    def inviteUsers(channel: VoiceChannel, inviter: User, invitees: Seq[User]): Future[String] =
      def toMentionStr(u: User) =
        if u == inviter then ">(You)"
        else u.mention

      val mentioned = invitees map toMentionStr

      for
        owner <- ownerByChannel(channel)
        // Invite with perms if invited by private channel owner
        // else invite with invite system
        (permsAdded, remaining) <-
          if owner.exists(_.id == inviter.id) && channel.getUserLimit == 0 then
            addVoicePerms(invitees, channel, channel.getGuild)
          else Future.successful(Nil, invitees)
      yield
        for mention <- remaining do
          invites.put(
            GuildUserId(channel.getGuild.id, mention.id),
            Invite(inviter.id, channel.id, System.currentTimeMillis() + (10 minutes).toMillis)
          )

        val bothInviteMessage = (permsAdded, remaining) match
          case (_, Seq()) => permInviteMessage
          case (Seq(), _) => inviteMessage
          case (_, _) =>
            val permInvitedStr = permsAdded map toMentionStr mkString ", "
            val remainingStr = remaining map toMentionStr mkString ", "
            s"$permInvitedStr:\n$permInviteMessage\n$remainingStr:\n$inviteMessage"

        s"""${mentioned mkString ", "}:
           |You have been invited to join ${inviter.mention} in voice chat in ${channel.mention}.
           |$bothInviteMessage""".stripMargin

    private def addVoicePerms(
      users: Seq[User],
      channel: VoiceChannel,
      guild: Guild
    ): Future[(Seq[User], Seq[User])] =
      try
        val (members, notFound) = users
          .map(user => user -> guild.findMember(user))
          .partition(_._2.isDefined)
        val grants = PermissionCollection(
          members
            .flatMap(_._2)
            .foldLeft(Vector.empty[(PermissionHolder, PermissionAttachment)]) { (acc, member) =>
              acc :+ member.asPermissionHolder -> channel
                .getPermissionAttachment(member)
                .allow(Set(Permission.VOICE_CONNECT))
            }
        )

        channel.applyPerms(grants).queueFuture().transform {
          case Success(_) => Success((members.map(_._1), notFound.map(_._1)))
          case Failure(_) => Success((Nil, users))
        }
      catch case _: PermissionException => Future.successful((Nil, users))
  end InviteCommand

  private val nameArgType = ArgType.GreedyString.map(_.take(maxNameLen))

  object PrivateCommand extends GenericCommand:
    override def name = "private"

    override val aliases = List("prv", "pv", "voice")

    override def description = "Create a private voice chat channel"

    override def longDescription(invocation: String): String =
      s"""This command creates a semi-private voice channel.
         |You can set a user limit (e.g. `$invocation 4`), or leave it blank to make it completely closed.
         |Any user may enter below the user limit, but after that an invite is required to enter.
         |You may invite other users there using the `${commands.prefix}${InviteCommand.name}` command.
         |The name of the channel can be set by adding it to the end of the command.
         |e.g. `$invocation 3 Hangout number 1`""".stripMargin

    override def permissions = CommandPermissions.Anyone

    private val limitArg = ArgSpec(
      "limit",
      "The number of users allowed in the channel",
      ArgType.Integer.withFilter(v => v > 0L && v <= 99L).map(_.toInt),
      required = false
    )
    private val nameArg =
      ArgSpec("name", "The name of the channel to create", nameArgType, required = false)

    override val argSpec = List(limitArg, nameArg)

    def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
      createUserOwnedChannel(
        public = false,
        limit = ctx.args.get(limitArg).getOrElse(0),
        invoker = ctx.invoker,
        chosenName = ctx.args.get(nameArg).getOrElse(""),
        commandName = ctx.name,
        maybeMistake = ctx.args.contains(nameArg) && !ctx.args.contains(limitArg)
      )

  object PublicCommand extends GenericCommand:
    override def name: String = "public"

    override def aliases: Seq[String] = List("pbv", "pb")

    override def description: String = "Create a public voice chat channel"

    override def longDescription(invocation: String): String =
      s"""This command creates a public voice channel.
         |You may invite other users there using the `${commands.prefix}${InviteCommand.name}` command.
         |The name of the channel can be set by adding it to the end of the command.
         |e.g. `$invocation Hangout number 1`""".stripMargin

    override def permissions = CommandPermissions.Anyone

    private val nameArg =
      ArgSpec("name", "The name of the channel to create", nameArgType, required = false)

    override val argSpec = List(nameArg)

    override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
      createUserOwnedChannel(
        public = true,
        limit = 0,
        invoker = ctx.invoker,
        chosenName = ctx.args.get(nameArg).getOrElse(""),
        commandName = ctx.name,
        maybeMistake = false
      )

  object DefaultCategoryCommand extends GenericCommand:
    override def name: String = "voicecategory"

    override def aliases: Seq[String] = Vector("vccat")

    override def description: String =
      "Set or query the default category for user-created voice chats"

    override def longDescription(invocation: String): String =
      s"""Query the default category for voice chats: `$invocation`
         |Set the default category for voice chats: `$invocation <category name>` or `$invocation id`
         |Unset the default category for voice chats: `$invocation none`
         |""".stripMargin

    override def permissions = CommandPermissions.ServerAdminOnly

    enum Action:
      case Show
      case Remove
      case Set(arg: Either[String, Category])

    private val categoryActionType =
      import ArgType.*
      val removeCategoryActionType =
        for
          v <- GreedyString
          if v == "none"
        yield Action.Remove
      val setCategoryActionType =
        for v <- CategoryFind
        yield Action.Set(v)
      Disjunction(removeCategoryActionType, setCategoryActionType)

    private val blobArg = ArgSpec(
      "category",
      "Category to put voice chats in, or 'none'",
      categoryActionType,
      required = false
    )

    override val argSpec = List(blobArg)

    override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
      ctx.invoker.member
        .map { member =>
          val guild = member.getGuild
          ctx.args.get(blobArg).getOrElse(Action.Show) match
            case Action.Show     => showCurrentCategory(guild)
            case Action.Remove   => removeDefaultCategory(guild)
            case Action.Set(cat) => setDefaultCategory(guild, cat)
        }
        .pipe(x => eitherToFutureMessage(x))
        .flatMap(ctx.invoker.reply(_))

    private def showCurrentCategory(guild: Guild): Future[Message] =
      getGuildDefaultCategory(guild).map { opt =>
        val cmd = s"${commands.prefix}$name"
        describeCategoryBehaviour(opt)
          .+(
            s"\nUse `$cmd category name` to select a category to place " +
              s"these channels into, or `$cmd none` to undo this."
          )
          .pipe(BotMessages.plain)
          .toMessage
      }

    private def removeDefaultCategory(guild: Guild): Future[Message] =
      defaultCategoryByGuild.remove(guild.id).map { _ =>
        BotMessages.okay(describeCategoryBehaviour(None)).toMessage
      }

    private def setDefaultCategory(
      guild: Guild,
      category: Either[String, Category]
    ): Future[Message] =
      category
        .map { cat =>
          (defaultCategoryByGuild(guild.id) = cat.id)
            .map(_ => describeCategoryBehaviour(Some(cat)))
        }
        .fold(e => Future.successful(e.toMessage), _.map(s => BotMessages.okay(s).toMessage))

    private def describeCategoryBehaviour(category: Option[Category]): String =
      category
        .fold {
          "User-created voice channels will be placed in the same category as the channel they were created from."
        } { cat =>
          s"User-created voice channels will be placed in <#${cat.getIdLong}> (${cat.getName})."
        }
  end DefaultCategoryCommand

  def allCommands: Seq[GenericCommand] =
    Seq(AcceptCommand, InviteCommand, PrivateCommand, PublicCommand, DefaultCategoryCommand)

  def allSlashCommands: Seq[GenericCommand] =
    Seq(AcceptCommand, InviteCommand, PrivateCommand, PublicCommand)

  private val CREATOR_PRIVATE_CHANNEL_PERMISSIONS =
    Set(Permission.MANAGE_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS)

  private val SELF_PRIVATE_CHANNEL_PERMISSIONS =
    Set(Permission.MANAGE_CHANNEL, Permission.VOICE_CONNECT)

  private val mistakeRegex = """ (\d+)$""".r.unanchored

  @threadUnsafe private lazy val translateChannelMoveError: PartialFunction[Throwable, String] =
    case _: IllegalStateException =>
      "You need to join voice chat before I can move you into a channel."
    case e: InsufficientPermissionException if e.getChannelId != 0 =>
      s"I don't have permission to move you to another voice channel. A server administrator will need to fix this. Missing `${e.getPermission.nn.getName}` on <#${e.getChannelId}>."
    case e: PermissionException =>
      s"I don't have permission to move you to another voice channel. A server administrator will need to fix this. Missing `${e.getPermission.nn.getName}`."

  private def eitherToFutureMessage[T](
    either: Either[String, Future[T]]
  )(using Conversion[T, MessageFromX]): Future[Message] =
    either.fold(
      err => Future.successful(BotMessages.error(err).toMessage),
      f => f.map(success => success.toMessage)
    )

  private def getChannelMoveError(ex: Throwable): Message =
    BotMessages
      .error(
        translateChannelMoveError.applyOrElse(
          ex,
          { ex =>
            APIHelper.failure("moving a user to a newly created channel")(ex)
            "An error occurred while trying to move you to another channel."
          }
        )
      )
      .toMessage

  private def sendErrorOrRetry(member: Member, ctx: CommandInvocation, command: GenericCommand)(
    ex: Throwable
  ): Future[RetrievableMessage] =
    val channel = member.getVoiceState.?.flatMap(_.getChannel.?)
    ex match
      case _: IllegalStateException if channel.isEmpty =>
        waitForVoiceJoin(
          member,
          ctx.invoker.asMessageReceiver,
          VoiceMove(member.id, member.getGuild.id),
          command.execute(ctx)
        )
      case _ => ctx.invoker.reply(getChannelMoveError(ex))

  private def waitForVoiceJoin[T](
    member: Member,
    reply: MessageReceiver,
    identifier: AnyRef,
    handler: => Future[T]
  ) =
    reply.sendMessage(
      BotMessages
        .plain("Please join voice chat. Your command has been remembered until then."): MessageFromX
    )
    val promise = Promise[T]()
    eventWaiter.queue(identifier) { case GuildVoiceUpdate(`member`, None, Some(_)) =>
      promise.completeWith(handler)
    }
    promise.future

  private def createUserOwnedChannel(
    public: Boolean,
    limit: Int,
    invoker: CommandInvoker,
    chosenName: String,
    commandName: String,
    maybeMistake: Boolean
  ) =
    def asyncCreateChannel(
      member: Member,
      limit: Int,
      name: String,
      category: Option[Category],
      channelReq: ChannelAction[VoiceChannel]
    ) =
      async {
        val categoryPerms =
          category.fold(PermissionCollection.empty)(_.permissionAttachments)
        val channelPerms = getChannelPermissions(member, limit, public)
        val newPerms = categoryPerms
          .merge(channelPerms)
          .mapValues(_.clear(Set(Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS)))
        channelReq.applyPerms(newPerms)

        if limit != 0 && !public then channelReq.setUserlimit(limit)

        val newVoiceChannel = await(channelReq.queueFuture())

        {
          ownerByChannel(newVoiceChannel) = member.getUser
        }.failed.foreach { ex =>
          APIHelper.failure("saving private channel")(ex)
          newVoiceChannel
            .delete()
            .queueFuture()
            .failed
            .foreach(APIHelper.failure("deleting private channel after database error"))
          ownerByChannel `remove` newVoiceChannel
        }

        logger.info(
          s"Created user-owned channel ${newVoiceChannel.unambiguousString} on behalf of ${member.unambiguousString}"
        )

        val moveResultFuture = APIHelper
          .tryRequest(member.getGuild.moveVoiceMember(member, newVoiceChannel))
          .recoverToEither(translateChannelMoveError)
          .tap(
            _.failed.foreach(
              APIHelper.loudFailure("moving user to private channel", invoker.asMessageReceiver)
            )
          )

        for
          result <- moveResultFuture
          err <- result.left
        do invoker.reply(BotMessages.error(err).toMessage)

        makeCreateChannelSuccessMessage(name, public, commandName, maybeMistake).toMessage
      }

    def retryingParseAndCreateChannel(member: Member): Future[Message] =
      getGuildDefaultCategory(member.getGuild).flatMap { defaultCategory =>
        def aux(): Future[Message] =
          member.getVoiceState.?.flatMap(_.getChannel.?) match
            case Some(voiceChannel) =>
              val name =
                if chosenName.nonEmpty then chosenName else genericChannelName(voiceChannel, public)
              val category = defaultCategory.orElse(voiceChannel.getParent.?)
              (for channelReq <- createChannel(name, member.getGuild, category)
              yield asyncCreateChannel(member, limit, name, category, channelReq))
                .pipe(x => eitherToFutureMessage(x))
                .recover { case Error(ErrorResponse.MISSING_PERMISSIONS) =>
                  diagnosePermissionFailure(category)
                }

            case None =>
              waitForVoiceJoin(
                member,
                invoker.asMessageReceiver,
                VoiceMove(member.id, member.getGuild.id),
                aux()
              )

        aux()
      }

    invoker.replyLater(transientIfPossible = false)
    invoker.member
      .map(retryingParseAndCreateChannel)
      .pipe(x => eitherToFutureMessage(x))
      .flatMap(invoker.reply(_))
  end createUserOwnedChannel

  private def diagnosePermissionFailure(baseCategory: Option[Category]): Message =
    baseCategory
      .flatMap { category =>
        val permsCollection = category.permissionAttachments
        val allSetPerms =
          permsCollection.values.map(_._2).foldLeft(PermissionAttachment.empty) { (acc, perms) =>
            acc.merge(perms)
          }
        val selfMember = category.getGuild.getSelfMember
        val myPerms = permsCollection
          .get(PermissionHolder.Member(selfMember.id))
          .getOrElse(PermissionAttachment.empty)
        val myDefaultPerms = PermissionAttachment.empty.allow(selfMember.getPermissions.asScala)
        val myEffectivePerms = myDefaultPerms.merge(myPerms)
        val myEffectivePermSet = myEffectivePerms.allows
        val allSetPermsSet = allSetPerms.allows ++ allSetPerms.denies
        val missingPerms = allSetPermsSet -- myEffectivePermSet
        val missingPermsString = missingPerms.map(_.getName).mkString(", ")

        if missingPerms.nonEmpty then
          Some(
            BotMessages
              .error(
                s"""Cannot create channel, probably because I don't have these permissions in the category ${category.getName}:
                   |$missingPermsString
                   |(I need to have these permissions to copy them from the category)""".stripMargin
              )
              .toMessage
          )
        else None
      }
      .getOrElse(
        BotMessages
          .error("An unexplained permission error occurred when creating the channel, sorry.")
          .toMessage
      )

  private def makeCreateChannelSuccessMessage(
    name: String,
    public: Boolean,
    commandName: String,
    maybeMistake: Boolean
  ) =
    val howToInvite =
      if public then ""
      else s"\nYou can invite others with the ${commands.prefix}${InviteCommand.name} command."
    val message = "Your channel has been created." + howToInvite

    val successMessage = BotMessages
      .okay(message)
      .setTitle("Success", null)

    if maybeMistake then
      name match
        case mistakeRegex(mistake) =>
          val cutName = name.dropRight(mistake.length).trim
          val invocation = s"${commands.prefix}$commandName $mistake $cutName"
          successMessage.addField(
            "Did you mean...?",
            s"You may have meant to type " +
              s"``${MessageUtils.sanitiseCode(invocation)}``, " +
              s"which will create a semi-public channel limited " +
              s"to $mistake users.",
            false
          )
        case _ =>
    successMessage

  private def getChannelPermissions(
    member: Member,
    limit: Int,
    public: Boolean
  ): PermissionCollection[PermissionHolder] =
    val guild = member.getGuild
    var collection: PermissionCollection[PermissionHolder] = PermissionCollection.empty

    if !public && limit == 0 then
      // If no limit, deny access to all users by default
      collection :+= guild.getPublicRole.asPermissionHolder -> PermissionAttachment.deny(
        Set(Permission.VOICE_CONNECT)
      )

    collection :+
      guild.getSelfMember.asPermissionHolder -> PermissionAttachment.allow(
        SELF_PRIVATE_CHANNEL_PERMISSIONS
      ) :+
      member.asPermissionHolder -> PermissionAttachment.allow(CREATOR_PRIVATE_CHANNEL_PERMISSIONS)

  private def prefixOrUpdateNumber(name: String, prefix: String): String =
    if name.startsWith(prefix) then
      val posBeforeNumber = name.lastIndexWhere(!Character.isDigit(_))
      val (unnumbered, number) = name.splitAt(posBeforeNumber + 1)
      val nextNumber = number match
        case ""        => " 2"
        case IntStr(n) => (n + 1).toString
        case largeNum  => s"$largeNum+1" // too large to parse to Int
      unnumbered + nextNumber
    else prefix + name

  private def genericChannelName(originalChannel: VoiceChannel, public: Boolean) =
    val newName =
      prefixOrUpdateNumber(originalChannel.name, prefix = if public then "Public " else "Private ")
    newName take maxNameLen

  private def createChannel(name: String, guild: Guild, category: Option[Category]) =
    Try(category.fold(guild.createVoiceChannel(name))(_.createVoiceChannel(name))).toEither.left
      .map({
        case e: InsufficientPermissionException if e.getChannelId != 0 =>
          s"I don't have permission to create a voice channel. A server administrator will need to fix this. Missing `${e.getPermission.nn.getName}` on <#${e.getChannelId}>."
        case e: PermissionException =>
          s"I don't have permission to create a voice channel. A server administrator will need to fix this. Missing `${e.getPermission.nn.getName}`."
        case x =>
          logger.error("Failed to create channel", x)
          "Unknown error occurred when trying to create your channel."
      })

  private def getGuildDefaultCategory(guild: Guild): Future[Option[Category]] =
    defaultCategoryByGuild
      .get(guild.id)
      .map(_.flatMap { id =>
        guild.getCategoryById(id.value).?
      })

  private def deleteUserOwnedChannel(channel: VoiceChannel) =
    async {
      await(
        channel.delete
          .queueFuture()
          .tap(_.foreach { _ =>
            logger.info(
              s"Deleted empty user-owned voice chat ${channel.unambiguousString} in ${channel.getGuild}"
            )
          })
          .recover { case Error(UNKNOWN_CHANNEL) =>
            logger.info(
              s"Tried to delete empty user-owned voice chat ${channel.unambiguousString} in ${channel.getGuild}, but it was already gone"
            )
          }
      )
      // Note: Sequenced rather than parallel because the channel
      // might not be deleted due to permissions or other reasons.
      await(ownerByChannel `remove` channel)
    }

  override def onEvent(event: GenericEvent): Unit = event match
    case ev: ReadyEvent =>
      given JDA = ev.getJDA
      async {
        val allUsersByChannel = await(ownerByChannel.items)
        val toRemove = mutable.HashSet[(ID[Guild], ID[VoiceChannel])]()

        for ((guildId, channelId), _) <- allUsersByChannel do
          channelId.find match
            case None =>
              toRemove += ((guildId, channelId))
            case Some(channel) if channel.getMembers.isEmpty =>
              deleteUserOwnedChannel(channel).failed.foreach(
                APIHelper.failure("deleting unused private channel")
              )
            case _ =>

        val removed = Future.sequence {
          for (guildId, channelId) <- toRemove
          yield ownerByChannel.remove(guildId, channelId)
        }
        await(removed) // Propagate exceptions
      }.failed.foreach(APIHelper.failure("processing initial private voice chat state"))
    case GuildVoiceUpdate(member, Some(leftChannel), _) =>
      if leftChannel.getMembers.isEmpty then
        // Last person to leave a channel
        async {
          val user = await(ownerByChannel(leftChannel))
          if user.isDefined then
            logger.debug(
              s"Last person (${member.unambiguousString}) left; will delete user-owned voice chat ${leftChannel.unambiguousString}"
            )
            await(deleteUserOwnedChannel(leftChannel))
          else
            logger.debug(
              s"Last person (${member.unambiguousString}) left channel ${leftChannel.unambiguousString}, which is not user-owned"
            )
        }.failed.foreach(APIHelper.failure("deleting unused private channel"))
      else
        val remainingMembers =
          leftChannel.getMembers.asScala.map(_.unambiguousString).mkString("[", ", ", "]")
        logger.debug(
          s"Someone (${member.unambiguousString}) left channel ${leftChannel.unambiguousString}, now these users remain: ${remainingMembers}"
        )

    case _ =>
end PrivateVoiceChats
