package score.discord.canti.functionality

import cps.*
import cps.monads.FutureAsyncMonad
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.{GenericEvent, ReadyEvent}
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.{CommandInteraction, OptionType}
import net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_CHANNEL
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import net.dv8tion.jda.api.{JDA, Permission}
import org.slf4j.LoggerFactory
import score.discord.canti.collections.{AsyncMap, ReplyCache}
import score.discord.canti.command.slash.SlashCommand
import score.discord.canti.command.{Command, ReplyingCommand}
import score.discord.canti.discord.permissions.{PermissionAttachment, PermissionCollection}
import score.discord.canti.functionality.ownership.MessageOwnership
import score.discord.canti.util.APIHelper.Error
import score.discord.canti.util.*
import score.discord.canti.wrappers.FutureEither.*
import score.discord.canti.wrappers.Scheduler
import score.discord.canti.wrappers.collections.AsyncMapConversions.*
import score.discord.canti.wrappers.jda.Conversions.{richChannelAction, richGuildChannel, richMember, richUser, richVoiceChannel}
import score.discord.canti.wrappers.jda.ID
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
import scala.language.{postfixOps, implicitConversions}
import scala.util.chaining.*
import scala.util.{Failure, Success, Try}

class PrivateVoiceChats(
                         ownerByChannel: AsyncMap[(ID[Guild], ID[VoiceChannel]), ID[User]],
                         defaultCategoryByGuild: AsyncMap[ID[Guild], ID[GuildChannel]],
                         commands: Commands,
                         eventWaiter: EventWaiter,
                       )(using messageOwnership: MessageOwnership, replyCache: ReplyCache)(using Scheduler) extends EventListener:
  private[this] val logger = LoggerFactory.getLogger(classOf[PrivateVoiceChats])

  private val invites = ConcurrentHashMap[GuildUserId, Invite]()

  private type Timestamp = Long

  private case class Invite(from: ID[User], channel: ID[VoiceChannel], expiry: Timestamp):
    def valid: Boolean = System.currentTimeMillis() < expiry

  private case class VoiceMove(id: ID[Member], guild: ID[Guild])

  sealed trait MyReplyingCommand extends ReplyingCommand:
    override given messageOwnership: MessageOwnership = PrivateVoiceChats.this.messageOwnership

    override given replyCache: ReplyCache = PrivateVoiceChats.this.replyCache

  sealed trait MySlashCommand extends SlashCommand:
    def executeAndGetMessage(origin: CommandInteraction, deferred: Future[InteractionHook]): Future[Message]

    def replyTo(deferred: Future[InteractionHook])(message: Message): Unit =
      deferred.flatMap(hook => hook.sendMessage(message).queueFuture())
        .failed.foreach(APIHelper.failure("replying to slash command"))

    def memberFromInteraction(origin: CommandInteraction): Either[String, Member] =
      Option(origin.getMember).toRight("This command must be run from within a server")

    override def execute(origin: CommandInteraction): Unit =
      val deferred = origin.deferReply().queueFuture()
      executeAndGetMessage(origin, deferred).map(replyTo(deferred))
        .failed.foreach(APIHelper.failure("calling /accept"))

  object AcceptCommand extends Command.Anyone with MyReplyingCommand with MySlashCommand:
    override def name = "accept"

    override def aliases = List("acc", "accpet")

    override def description = "Accept another user's invitation to join a voice channel"

    override def options: Seq[OptionData] = Nil

    override def executeAndGetMessage(message: Message, args: String): Future[Message] =
      val member = CommandHelper(message).member
      val reply: Message => Unit = message ! _

      def retry(): Future[Message] = executeGeneric(member, reply, retry)

      try message.delete.queueFuture()
      catch
        case _: PermissionException =>

      retry()

    override def executeAndGetMessage(origin: CommandInteraction, deferred: Future[InteractionHook]): Future[Message] =
      val member = memberFromInteraction(origin)

      def retry(): Future[Message] = executeGeneric(member, replyTo(deferred), retry)

      retry()

    private def executeGeneric(member: Either[String, Member], reply: Message => Unit, retry: () => Future[Message]): Future[Message] =
      def ensureInviteValid(inv: Invite) =
        Either.cond(inv.valid, inv, "Your last invite expired. Please ask for another.")

      val result = for
        member <- member
        guild = member.getGuild
        inv <- Option(invites get GuildUserId(member))
          .toRight("You don't have any pending voice chat invitations.")
        _ <- ensureInviteValid(inv)
        voiceChannel <- guild.findVoiceChannel(inv.channel)
          .toRight("The voice channel you were invited to no longer exists.")
        memberName = member.getEffectiveName
        voiceMention = s"<#${voiceChannel.rawId}>"
      yield
        APIHelper.tryRequest(
          guild.moveVoiceMember(member, voiceChannel)
        ).transformWith {
          case Success(_) =>
            invites.remove(GuildUserId(member))
            Future.successful(BotMessages.okay(s"Moved you into the $voiceMention channel.")
              .setTitle(s"$memberName: Success!", null)
              .toMessage)
          case Failure(ex) =>
            sendErrorOrRetry(member, reply)(retry())(ex)
        }

      result.pipe(x => eitherToFutureMessage(x))

  object InviteCommand extends Command.Anyone with MyReplyingCommand with MySlashCommand:
    override def name = "invite"

    override val aliases = List("inv")

    override def description = "Ask another user to join your current voice channel"

    override def longDescription(invocation: String): String =
      s"""Usage:
         |`$invocation @person1 @person2`
         |Invites the mentioned parties, in this case person1 and person2, to your current voice channel.
       """.stripMargin.trim

    lazy val inviteMessage: String = s"Please join a voice channel and " +
      s"type `${commands.prefix}${AcceptCommand.name}` to accept this invitation."

    val permInviteMessage: String = s"You may now enter the channel."

    override def options: Seq[OptionData] = Seq(
      OptionData(OptionType.USER, "user", "The user to invite", true),
    )

    override def executeAndGetMessage(origin: CommandInteraction, deferred: Future[InteractionHook]): Future[Message] =
      executeGeneric(memberFromInteraction(origin), Seq(origin.getOption("user").getAsUser))

    override def executeAndGetMessage(message: Message, args: String): Future[Message] =
      val member = CommandHelper(message).member
      val invitees = message.getMentionedUsers.asScala.toSeq
      executeGeneric(member, invitees)

    private def executeGeneric(member: Either[String, Member], invitees: Seq[User]) =
      val response = for
        member <- member
        guild = member.getGuild
        chan <- Option(member.getVoiceState.getChannel)
          .toRight("You must be in voice chat to use this command.")
        success <- invitees match
          case Seq() => Left("You must mention the users you want to join you in voice chat.")
          case Seq(mentions@_*) =>
            assert(guild == chan.getGuild)
            Right(inviteUsers(channel = chan, inviter = member.getUser, invitees = mentions))
      yield success

      response.pipe(x => eitherToFutureMessage(x))

    def inviteUsers(channel: VoiceChannel, inviter: User, invitees: Seq[User]): Future[String] =
      def toMentionStr(u: User) =
        if (u == inviter) ">(You)"
        else u.mention

      val mentioned = invitees map toMentionStr

      for
        owner <- ownerByChannel(channel)
        // Invite with perms if invited by private channel owner
        // else invite with invite system
        (permsAdded, remaining) <-
          if owner.exists(_.id == inviter.id) && channel.getUserLimit == 0
          then addVoicePerms(invitees, channel, channel.getGuild)
          else Future.successful(Nil, invitees)
      yield
        for (mention <- remaining)
          invites.put(
            GuildUserId(channel.getGuild.id, mention.id),
            Invite(inviter.id, channel.id, System.currentTimeMillis() + (10 minutes).toMillis)
          )

        val bothInviteMessage = (permsAdded, remaining) match {
          case (_, Seq()) => permInviteMessage
          case (Seq(), _) => inviteMessage
          case (_, _) =>
            val permInvitedStr = permsAdded map toMentionStr mkString ", "
            val remainingStr = remaining map toMentionStr mkString ", "
            s"$permInvitedStr:\n$permInviteMessage\n$remainingStr:\n$inviteMessage"
        }

        s"""${mentioned mkString ", "}:
           |You have been invited to join ${inviter.mention} in voice chat in ${channel.mention}.
           |$bothInviteMessage""".stripMargin


    private def addVoicePerms(users: Seq[User], channel: VoiceChannel, guild: Guild): Future[(Seq[User], Seq[User])] =
      try
        val (members, notFound) = users
          .map(user => user -> guild.findMember(user))
          .partition(_._2.isDefined)
        val grants = PermissionCollection(members
          .flatMap(_._2)
          .foldLeft(Vector.empty[(Member, PermissionAttachment)]) { (acc, member) =>
            acc :+ member -> channel.getPermissionAttachment(member).allow(Permission.VOICE_CONNECT)
          })

        channel.applyPerms(grants).queueFuture().transform {
          case Success(_) => Success((members.map(_._1), notFound.map(_._1)))
          case Failure(_) => Success((Nil, users))
        }
      catch
        case _: PermissionException => Future.successful((Nil, users))

  object PrivateCommand extends Command.Anyone with MyReplyingCommand with MySlashCommand:
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

    override def options: Seq[OptionData] = Seq(
      OptionData(OptionType.INTEGER, "limit", "The number of users allowed in the channel"),
      OptionData(OptionType.STRING, "name", "The name of the channel to create"),
    )

    override def executeAndGetMessage(message: Message, args: String): Future[Message] =
      createUserOwnedChannelFromMessage(message, args, name, public = false)

    override def executeAndGetMessage(origin: CommandInteraction, deferred: Future[InteractionHook]): Future[Message] =
      createUserOwnedChannel(
        public = false,
        limit = Option(origin.getOption("limit")).fold(0)(_.getAsLong.toInt),
        member = memberFromInteraction(origin),
        reply = replyTo(deferred),
        chosenName = Option(origin.getOption("name")).fold("")(_.getAsString),
        commandName = "",
        args = "")

  object PublicCommand extends Command.Anyone with MyReplyingCommand with MySlashCommand:
    override def name: String = "public"

    override def aliases: Seq[String] = List("pbv", "pb")

    override def description: String = "Create a public voice chat channel"

    override def longDescription(invocation: String): String =
      s"""This command creates a public voice channel.
         |You may invite other users there using the `${commands.prefix}${InviteCommand.name}` command.
         |The name of the channel can be set by adding it to the end of the command.
         |e.g. `$invocation Hangout number 1`""".stripMargin

    override def options: Seq[OptionData] = Seq(
      OptionData(OptionType.STRING, "name", "The name of the channel to create"),
    )

    override def executeAndGetMessage(message: Message, args: String): Future[Message] =
      createUserOwnedChannelFromMessage(message, args, name, public = true)

    override def executeAndGetMessage(origin: CommandInteraction, deferred: Future[InteractionHook]): Future[Message] =
      createUserOwnedChannel(
        public = true,
        limit = 0,
        member = memberFromInteraction(origin),
        reply = replyTo(deferred),
        chosenName = Option(origin.getOption("name")).fold("")(_.getAsString),
        commandName = "",
        args = "")

  object DefaultCategoryCommand extends MyReplyingCommand with Command.ServerAdminOnly:
    override def name: String = "voicecategory"

    override def aliases: Seq[String] = Vector("vccat")

    override def description: String = "Set or query the default category for user-created voice chats"

    override def longDescription(invocation: String): String =
      s"""Query the default category for voice chats: `$invocation`
         |Set the default category for voice chats: `$invocation <category name>` or `$invocation id`
         |Unset the default category for voice chats: `$invocation none`
         |""".stripMargin

    override def executeAndGetMessage(message: Message, args: String): Future[Message] =
      CommandHelper(message).guild.map { guild =>
        args.trim match {
          case "" => showCurrentCategory(guild)
          case "none" => removeDefaultCategory(guild)
          case arg => setDefaultCategory(guild, arg)
        }
      }.pipe(x => eitherToFutureMessage(x))

    private def showCurrentCategory(guild: Guild): Future[Message] =
      getGuildDefaultCategory(guild).map { opt =>
        val cmd = s"${commands.prefix}$name"
        describeCategoryBehaviour(opt)
          .+(s"\nUse `$cmd category name` to select a category to place " +
            s"these channels into, or `$cmd none` to undo this.")
          .pipe(BotMessages.plain)
          .toMessage
      }

    private def removeDefaultCategory(guild: Guild): Future[Message] =
      defaultCategoryByGuild.remove(guild.id).map { _ =>
        BotMessages.okay(describeCategoryBehaviour(None)).toMessage
      }

    private def setDefaultCategory(guild: Guild, categoryName: String): Future[Message] =
      ParseUtils.findCategory(guild, categoryName)
        .map { cat =>
          (defaultCategoryByGuild(guild.id) = cat.id)
            .map(_ => describeCategoryBehaviour(Some(cat)))
        }
        .fold(e => Future.successful(e.toMessage), _.map(s => BotMessages.okay(s).toMessage))

    private def describeCategoryBehaviour(category: Option[Category]): String =
      category.map(cat => s"User-created voice channels will be placed in <#${cat.getIdLong}> (${cat.getName}).")
        .getOrElse("User-created voice channels will be placed in the same category as the channel they were created from.")

  def allCommands: Seq[Command] = Seq(AcceptCommand, InviteCommand, PrivateCommand, PublicCommand, DefaultCategoryCommand)

  def allSlashCommands: Seq[SlashCommand] = Seq(AcceptCommand, InviteCommand, PrivateCommand, PublicCommand)

  private val CREATOR_PRIVATE_CHANNEL_PERMISSIONS =
    Set(Permission.MANAGE_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS)

  private val SELF_PRIVATE_CHANNEL_PERMISSIONS =
    Set(Permission.MANAGE_CHANNEL, Permission.VOICE_CONNECT)

  private val mistakeRegex = """ (\d+)$""".r.unanchored

  private val translateChannelMoveError: PartialFunction[Throwable, String] =
    case _: IllegalStateException =>
      "You need to join voice chat before I can move you into a channel."
    case e: PermissionException =>
      s"I don't have permission to move you to another voice channel. A server administrator will need to fix this. Missing `${e.getPermission.getName}`."

  private def eitherToFutureMessage[T](either: Either[String, Future[T]])(using Conversion[T, MessageFromX]): Future[Message] =
    either.fold(
      err => Future.successful(BotMessages.error(err).toMessage),
      f => f.map(success => success.toMessage)
    )

  private def getChannelMoveError(ex: Throwable): Message =
    BotMessages.error(translateChannelMoveError.applyOrElse(ex, { ex =>
      APIHelper.failure("moving a user to a newly created channel")(ex)
      "An error occurred while trying to move you to another channel."
    })).toMessage

  private def sendErrorOrRetry(member: Member, reply: Message => Unit)(handler: => Future[Message])(ex: Throwable): Future[Message] =
    val channel = Option(member.getVoiceState.getChannel)
    ex match
      case _: IllegalStateException if channel.isEmpty => waitForVoiceJoin(member, reply, VoiceMove(member.id, member.getGuild.id), handler)
      case _ => Future.successful(getChannelMoveError(ex))

  private def waitForVoiceJoin[T](member: Member, reply: Message => Unit, identifier: AnyRef, handler: => Future[T]) =
    reply(BotMessages.plain("Please join voice chat. Your command has been remembered until then.").toMessage)
    val promise = Promise[T]()
    eventWaiter.queue(identifier) {
      case GuildVoiceUpdate(`member`, None, Some(_)) => promise.completeWith(handler)
    }
    promise.future

  private def createUserOwnedChannelFromMessage(message: Message, args: String, commandName: String, public: Boolean): Future[Message] =
    val member = CommandHelper(message).member
    val reply: Message => Unit = message ! _
    val (limit, name) = parseChannelDetails(args, public)

    createUserOwnedChannel(public, limit, member, reply, name, commandName, args)

  private def createUserOwnedChannel(public: Boolean, limit: Int, member: Either[String, Member], reply: Message => Unit, chosenName: String, commandName: String, args: String) =
    def asyncCreateChannel(member: Member, limit: Int, name: String, category: Option[Category], channelReq: ChannelAction[VoiceChannel]) =
      async {
        val categoryPerms = category.fold(PermissionCollection.empty[IPermissionHolder])(_.permissionAttachments)
        val channelPerms = getChannelPermissions(member, limit, public)
        val newPerms = categoryPerms.merge(channelPerms).mapValues(_.clear(Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS))
        channelReq.applyPerms(newPerms)

        if limit != 0 && !public then
          channelReq.setUserlimit(limit)

        val newVoiceChannel = await(channelReq.queueFuture())

        {
          ownerByChannel(newVoiceChannel) = member.getUser
        }.failed.foreach { ex =>
          APIHelper.failure("saving private channel")(ex)
          newVoiceChannel.delete().queueFuture().failed.foreach(
            APIHelper.failure("deleting private channel after database error"))
          ownerByChannel remove newVoiceChannel
        }

        logger.info(s"Created user-owned channel ${newVoiceChannel.unambiguousString} on behalf of ${member.unambiguousString}")

        val moveResultFuture = APIHelper.tryRequest(member.getGuild.moveVoiceMember(member, newVoiceChannel))
          .recoverToEither(translateChannelMoveError)
          .tap(_.failed.foreach(APIHelper.loudFailure("moving user to private channel", reply)))

        for
          result <- moveResultFuture
          err <- result.left
        do
          reply(BotMessages.error(err).toMessage)

        makeCreateChannelSuccessMessage(name, limit, public, commandName, args).toMessage
      }

    def retryingParseAndCreateChannel(member: Member): Future[Message] =
      getGuildDefaultCategory(member.getGuild).flatMap { defaultCategory =>
        def aux(): Future[Message] =
          Option(member.getVoiceState.getChannel) match
            case Some(voiceChannel) =>
              val name = if (chosenName.nonEmpty) chosenName else genericChannelName(voiceChannel, public)
              val category = defaultCategory.orElse(Option(voiceChannel.getParent))
              (for channelReq <- createChannel(name, member.getGuild, category)
               yield asyncCreateChannel(member, limit, name, category, channelReq))
                .pipe(x => eitherToFutureMessage(x))

            case None =>
              waitForVoiceJoin(member, reply, VoiceMove(member.id, member.getGuild.id), aux())

        aux()
      }

    member
      .map(retryingParseAndCreateChannel)
      .pipe(x => eitherToFutureMessage(x))
      .tap(_.failed.foreach(APIHelper.loudFailure("creating private channel", reply)))

  private def makeCreateChannelSuccessMessage(name: String, limit: Int, public: Boolean, commandName: String, args: String) =
    val message = "Your channel has been created." + (
      if public then ""
      else s"\nYou can invite others with the ${commands.prefix}${InviteCommand.name} command.")

    val successMessage = BotMessages
      .okay(message)
      .setTitle("Success", null)

    if (limit == 0 && !public) args.trim match
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

  private def getChannelPermissions(member: Member, limit: Int, public: Boolean): PermissionCollection[IPermissionHolder] =
    val guild = member.getGuild
    var collection: PermissionCollection[IPermissionHolder] = PermissionCollection.empty

    if !public && limit == 0 then
      // If no limit, deny access to all users by default
      collection :+= guild.getPublicRole -> PermissionAttachment(denies = Set(Permission.VOICE_CONNECT))

    collection :+
      guild.getSelfMember -> PermissionAttachment(allows = SELF_PRIVATE_CHANNEL_PERMISSIONS) :+
      member -> PermissionAttachment(allows = CREATOR_PRIVATE_CHANNEL_PERMISSIONS)

  private def prefixOrUpdateNumber(name: String, prefix: String): String =
    if name.startsWith(prefix) then
      val posBeforeNumber = name.lastIndexWhere(!Character.isDigit(_))
      val (unnumbered, number) = name.splitAt(posBeforeNumber + 1)
      val nextNumber = number match
        case "" => " 2"
        case IntStr(n) => (n + 1).toString
        case largeNum => s"$largeNum+1" // too large to parse to Int
      unnumbered + nextNumber
    else
      prefix + name

  private val maxNameLen = 100

  private def parseChannelDetails(args: String, public: Boolean) =
    val trimmedArgs = args.trim
    val (limit, name) =
      if public then ("", trimmedArgs)
      else trimmedArgs.split(" ", 2) match
        case Array(limitStr, name_) => (limitStr, name_.trim)
        case Array(maybeLimit) => (maybeLimit, "")

    limit.toIntOption
      .filter(x => x >= 0 && x <= 99)
      .fold((0, trimmedArgs))((_, name))
    match
      case (limit_, name_) if name_.length > maxNameLen => (limit_, name_ take maxNameLen)
      case (limit_, name_) if name_.length < 3 => (limit_, "")
      case x => x

  private def genericChannelName(originalChannel: VoiceChannel, public: Boolean) =
    val newName = prefixOrUpdateNumber(originalChannel.name, prefix = if (public) "Public " else "Private ")
    newName take maxNameLen

  private def createChannel(name: String, guild: Guild, category: Option[Category]) =
    Try(category.fold(guild.createVoiceChannel(name))(_.createVoiceChannel(name))).toEither.left.map({
      case e: PermissionException =>
        s"I don't have permission to create a voice channel. A server administrator will need to fix this. Missing `${e.getPermission.getName}`."
      case x =>
        logger.error("Failed to create channel", x)
        "Unknown error occurred when trying to create your channel."
    })

  private def getGuildDefaultCategory(guild: Guild): Future[Option[Category]] =
    defaultCategoryByGuild.get(guild.id).map(_.flatMap { id =>
      Option(guild.getCategoryById(id.value))
    })

  private def deleteUserOwnedChannel(channel: VoiceChannel) =
    async {
      await(channel.delete.queueFuture().tap(_.foreach { _ =>
        logger.info(s"Deleted empty user-owned voice chat ${channel.unambiguousString} in ${channel.getGuild}")
      }).recover {
        case Error(UNKNOWN_CHANNEL) =>
          logger.info(s"Tried to delete empty user-owned voice chat ${channel.unambiguousString} in ${channel.getGuild}, but it was already gone")
      })
      // Note: Sequenced rather than parallel because the channel
      // might not be deleted due to permissions or other reasons.
      await(ownerByChannel remove channel)
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
              deleteUserOwnedChannel(channel).failed.foreach(APIHelper.failure("deleting unused private channel"))
            case _ =>

        val removed = Future.sequence {
          for (guildId, channelId) <- toRemove
          yield ownerByChannel.remove(guildId, channelId)
        }
        await(removed) // Propagate exceptions
      }.failed.foreach(APIHelper.failure("processing initial private voice chat state"))
    case GuildVoiceUpdate(_, Some(leftChannel), _) if leftChannel.getMembers.isEmpty =>
      // Last person to leave a channel
      async {
        val user = await(ownerByChannel(leftChannel))
        if user.isDefined then
          await(deleteUserOwnedChannel(leftChannel))
      }.failed.foreach(APIHelper.failure("deleting unused private channel"))

    case _ =>
