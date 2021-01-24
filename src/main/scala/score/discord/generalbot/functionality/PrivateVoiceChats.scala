package score.discord.generalbot.functionality

import net.dv8tion.jda.api.entities._
import net.dv8tion.jda.api.events.{GenericEvent, ReadyEvent}
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.{JDA, Permission}
import score.discord.generalbot.collections.{AsyncMap, ReplyCache}
import score.discord.generalbot.command.{Command, ReplyingCommand}
import score.discord.generalbot.discord.permissions.{PermissionAttachment, PermissionCollection}
import score.discord.generalbot.functionality.ownership.MessageOwnership
import score.discord.generalbot.util._
import score.discord.generalbot.wrappers.FutureEither._
import score.discord.generalbot.wrappers.Scheduler
import score.discord.generalbot.wrappers.collections.AsyncMapConversions._
import score.discord.generalbot.wrappers.jda.Conversions._
import score.discord.generalbot.wrappers.jda.IdConversions._
import score.discord.generalbot.wrappers.jda.matching.Events.GuildVoiceUpdate
import score.discord.generalbot.wrappers.jda.{ChannelPermissionUpdater, ID}

import java.util.concurrent.ConcurrentHashMap
import scala.async.Async._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.chaining._
import scala.util.{Failure, Success, Try}

class PrivateVoiceChats(
  ownerByChannel: AsyncMap[(ID[Guild], ID[VoiceChannel]), ID[User]],
  defaultCategoryByGuild: AsyncMap[ID[Guild], ID[GuildChannel]],
  commands: Commands,
)(implicit scheduler: Scheduler, messageOwnership: MessageOwnership, replyCache: ReplyCache) extends EventListener {
  private val invites = new ConcurrentHashMap[GuildUserId, Invite]()

  private type Timestamp = Long

  private case class Invite(from: ID[User], channel: ID[VoiceChannel], expiry: Timestamp) {
    def valid: Boolean = System.currentTimeMillis() < expiry
  }

  {
    val accept: Command = new Command.Anyone {
      override def name = "accept"

      override def aliases = List("acc", "accpet")

      override def description = "Accept another user's invitation to join a voice channel"

      override def execute(message: Message, args: String): Unit = {
        try message.delete.queue()
        catch {
          case _: PermissionException =>
        }

        def ensureInviteValid(inv: Invite) =
          if (inv.valid) Right(inv)
          else Left("Your last invite expired. Please ask for another.")

        val member = CommandHelper(message).member
        val result = for {
          member <- member
          inv <- Option(invites get GuildUserId(member))
            .toRight("You don't have any pending voice chat invitations.")
          _ <- ensureInviteValid(inv)
          voiceChannel <- message.getGuild.findVoiceChannel(inv.channel)
            .toRight("The voice channel you were invited to no longer exists.")
          memberName = member.getEffectiveName
          voiceMention = s"<#${voiceChannel.rawId}>"
        } yield APIHelper.tryRequest(
          message.getGuild.moveVoiceMember(member, voiceChannel),
          onFail = sendChannelMoveError(message)
        ).foreach { _ =>
          invites.remove(GuildUserId(member))
          message ! BotMessages
            .okay(s"Moved you into the $voiceMention channel.")
            .setTitle(s"$memberName: Success!", null)
        }

        for (err <- result.left) {
          val errEmbed = BotMessages.error(err)
          for (member <- member)
            errEmbed.setTitle(s"${member.getEffectiveName}: Error", null)
          message ! errEmbed
        }
      }
    }

    val invite: Command = new Command.Anyone {
      override def name = "invite"

      override val aliases = List("inv")

      override def description = "Ask another user to join your current voice channel"

      override def longDescription(invocation: String): String =
        s"""Usage:
           |`$invocation @person1 @person2`
           |Invites the mentioned parties, in this case person1 and person2, to your current voice channel.
         """.stripMargin.trim

      lazy val inviteMessage: String = s"Please join a voice channel and " +
        s"type `${commands.prefix}${accept.name}` to accept this invitation."

      val permInviteMessage: String = s"You may now enter the channel."

      override def execute(message: Message, args: String): Unit = {
        val response = for {
          member <- CommandHelper(message).member
          chan <- Option(member.getVoiceState.getChannel)
            .toRight("You must be in voice chat to use this command.")
          success <- message.getMentionedUsers.asScala.toSeq match {
            case Seq() => Left("You must mention the users you want to join you in voice chat.")
            case Seq(mentions@_*) =>
              assert(message.getGuild == chan.getGuild)
              Right(inviteUsers(channel = chan, inviter = message.getAuthor, invitees = mentions))
          }
        } yield success

        response.fold(
          x => Future.successful(BotMessages.error(x): MessageFromX),
          x => x.map(s => s: MessageFromX)
        ) foreach (message ! _)
      }

      def inviteUsers(channel: VoiceChannel, inviter: User, invitees: Seq[User]): Future[String] = {
        def toMentionStr(u: User) =
          if (u == inviter) ">(You)"
          else u.mention

        val mentioned = invitees map toMentionStr

        for {
          owner <- ownerByChannel(channel)
          // Invite with perms if invited by private channel owner
          // else invite with invite system
          (permsAdded, remaining) <-
            if (owner.exists(_.id == inviter.id) && channel.getUserLimit == 0) {
              addVoicePerms(invitees, channel, channel.getGuild)
            } else Future.successful(Nil, invitees)
        } yield {
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
        }
      }

      private def addVoicePerms(users: Seq[User], channel: VoiceChannel, guild: Guild): Future[(Seq[User], Seq[User])] = {
        try {
          val permUpdater = ChannelPermissionUpdater(channel)
          val addedMembers = users.map(guild.findMember).map {
            case Some(targetMember) =>
              permUpdater.grant(targetMember, Permission.VOICE_CONNECT)
              Right(())
            case None =>
              Left("Member missing from guild")
          }

          val (granted, notFound) = (addedMembers zip users).partition(_._1.isRight)
          permUpdater.queue().transform {
            case Success(_) => Success((granted.map(_._2), notFound.map(_._2)))
            case Failure(_) => Success((Nil, users))
          }
        } catch {
          case _: PermissionException => Future.successful((Nil, users))
        }
      }
    }

    val privat: Command = new Command.Anyone {
      override def name = "private"

      override val aliases = List("prv", "pv", "voice")

      override def description = "Create a private voice chat channel"

      override def longDescription(invocation: String): String =
        s"""This command creates a semi-private voice channel.
           |You can set a user limit (e.g. `$invocation 4`), or leave it blank to make it completely closed.
           |Any user may enter below the user limit, but after that an invite is required to enter.
           |You may invite other users there using the `${commands.prefix}${invite.name}` command.
           |The name of the channel can be set by adding it to the end of the command.
           |e.g. `$invocation 3 Hangout number 1`""".stripMargin

      override def execute(message: Message, args: String): Unit = {
        createUserOwnedChannelFromMessage(message, args, name, public = false)
      }
    }

    val public: Command = new Command.Anyone {
      override def name: String = "public"

      override def aliases: Seq[String] = List("pbv", "pb")

      override def description: String = "Create a public voice chat channel"

      override def longDescription(invocation: String): String =
        s"""This command creates a public voice channel.
           |You may invite other users there using the `${commands.prefix}${invite.name}` command.
           |The name of the channel can be set by adding it to the end of the command.
           |e.g. `$invocation Hangout number 1`""".stripMargin

      override def execute(message: Message, args: String): Unit = {
        createUserOwnedChannelFromMessage(message, args, name, public = true)
      }
    }

    val defaultCategoryCmd: Command = new ReplyingCommand with Command.ServerAdminOnly {
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
        }.fold({ e => Future.successful(BotMessages.error(e).toMessage) }, identity)

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

      override def messageOwnership: MessageOwnership = PrivateVoiceChats.this.messageOwnership

      override def replyCache: ReplyCache = PrivateVoiceChats.this.replyCache
    }

    commands register accept
    commands register invite
    commands register privat
    commands register public
    commands register defaultCategoryCmd
  }

  private val CREATOR_PRIVATE_CHANNEL_PERMISSIONS =
    Set(Permission.MANAGE_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS)

  private val SELF_PRIVATE_CHANNEL_PERMISSIONS =
    Set(Permission.MANAGE_CHANNEL, Permission.VOICE_CONNECT)

  private val mistakeRegex = """ (\d+)$""".r.unanchored

  private val translateChannelMoveError: PartialFunction[Throwable, String] = {
    case _: IllegalStateException =>
      "You need to join voice chat before I can move you into a channel."
    case _: PermissionException =>
      "I don't have permission to move you to another voice channel. A server administrator will need to fix this."
  }

  private def sendChannelMoveError(replyTo: Message)(ex: Throwable): Unit = {
    replyTo ! BotMessages.error(translateChannelMoveError.applyOrElse(ex, { ex: Throwable =>
      APIHelper.failure("moving a user to a newly created channel")(ex)
      "An error occurred while trying to move you to another channel."
    }))
  }

  private def createUserOwnedChannelFromMessage(message: Message, args: String, commandName: String, public: Boolean): Unit = {
    val guild = message.getGuild
    for (defaultCategory <- getGuildDefaultCategory(guild)) {
      (for {
        member <- CommandHelper(message).member
        voiceChannel <- Option(member.getVoiceState.getChannel)
          .toRight("You need to join voice chat before you can do this.")
        (limit, name) = parseChannelDetails(args, voiceChannel, public)
        category = defaultCategory.orElse(Option(voiceChannel.getParent))
        channelReq <- createChannel(name, guild, category)
      } yield {
        async {
          val categoryPerms = category.fold(PermissionCollection.empty[IPermissionHolder])(_.permissionOverrides)
          val channelPerms = getChannelPermissions(member, limit, public)
          val newPerms = categoryPerms.merge(channelPerms).mapValues(_.clear(Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS))
          channelReq.applyPerms(newPerms)

          if (limit != 0 && !public)
            channelReq.setUserlimit(limit)

          val newVoiceChannel = await(channelReq.queueFuture())

          {
            ownerByChannel(newVoiceChannel) = message.getAuthor
          }.failed.foreach { ex =>
            APIHelper.failure("saving private channel")(ex)
            newVoiceChannel.delete().queueFuture().failed.foreach(
              APIHelper.failure("deleting private channel after database error"))
            ownerByChannel remove newVoiceChannel
          }

          message ! makeCreateChannelSuccessMessage(name, limit, public, commandName, args)

          await(APIHelper.tryRequest(guild.moveVoiceMember(member, newVoiceChannel))
            .recoverToEither(translateChannelMoveError))
        }
      }).toFuture
        .tap(_.failed.foreach(APIHelper.loudFailure("creating private channel", message)))
        .foreach { result =>
          for (err <- result.left)
            message ! BotMessages.error(err)
        }
    }
  }

  private def makeCreateChannelSuccessMessage(name: String, limit: Int, public: Boolean, commandName: String, args: String) = {
    val successMessage = BotMessages
      .okay("Your channel has been created.")
      .setTitle("Success", null)

    if (limit == 0 && !public) args.trim match {
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
    }
    successMessage
  }

  private def getChannelPermissions(member: Member, limit: Int, public: Boolean): PermissionCollection[IPermissionHolder] = {
    val guild = member.getGuild
    var collection: PermissionCollection[IPermissionHolder] = PermissionCollection.empty

    if (!public && limit == 0)
      // If no limit, deny access to all users by default
      collection :+= guild.getPublicRole -> PermissionAttachment( denies = Set(Permission.VOICE_CONNECT))

    collection :+
      guild.getSelfMember -> PermissionAttachment(allows = SELF_PRIVATE_CHANNEL_PERMISSIONS) :+
      member -> PermissionAttachment(allows = CREATOR_PRIVATE_CHANNEL_PERMISSIONS)
  }

  private def prefixOrUpdateNumber(name: String, prefix: String): String = {
    if (name.startsWith(prefix)) {
      val posBeforeNumber = name.lastIndexWhere(!Character.isDigit(_))
      val (unnumbered, number) = name.splitAt(posBeforeNumber + 1)
      val nextNumber = number match {
        case "" => " 2"
        case IntStr(n) => (n + 1).toString
        case largeNum => s"$largeNum+1"  // too large to parse to Int
      }
      unnumbered + nextNumber
    } else {
      prefix + name
    }
  }

  private def parseChannelDetails(args: String, originalChannel: VoiceChannel, public: Boolean) = {
    val trimmedArgs = args.trim
    val (limit, name) =
      if (public) ("", trimmedArgs)
      else trimmedArgs.split(" ", 2) match {
        case Array(limitStr, name_) => (limitStr, name_.trim)
        case Array(maybeLimit) => (maybeLimit, "")
      }

    val maxNameLen = 100
    limit.toIntOption
      .filter(x => x >= 0 && x <= 99)
      .map((_, name))
      .getOrElse((0, trimmedArgs))
    match {
      case (limit_, name_) if name_.length > maxNameLen => (limit_, name_ take maxNameLen)
      case (limit_, name_) if name_.length < 3 =>
        val newName = prefixOrUpdateNumber(originalChannel.name, prefix = if (public) "Public " else "Private ")
        (limit_, newName take maxNameLen)
      case x => x
    }
  }

  private def createChannel(name: String, guild: Guild, category: Option[Category]) =
    Try(category.fold(guild.createVoiceChannel(name))(_.createVoiceChannel(name))).toEither.left.map({
      case _: PermissionException =>
        "I don't have permission to create a voice channel. A server administrator will need to fix this."
      case x =>
        System.err.println("Printing a stack trace for failed channel creation:")
        x.printStackTrace()
        "Unknown error occurred when trying to create your channel."
    })

  private def getGuildDefaultCategory(guild: Guild): Future[Option[Category]] =
    defaultCategoryByGuild.get(guild.id).map(_.flatMap { id =>
      Option(guild.getCategoryById(id.value))
    })

  override def onEvent(event: GenericEvent): Unit = event match {
    case ev: ReadyEvent =>
      implicit val jda: JDA = ev.getJDA
      async {
        val allUsersByChannel = await(ownerByChannel.items)
        val toRemove = new mutable.HashSet[(ID[Guild], ID[VoiceChannel])]

        for (((guildId, channelId), _) <- allUsersByChannel) {
          channelId.find match {
            case None =>
              toRemove += ((guildId, channelId))
            case Some(channel) if channel.getMembers.isEmpty =>
              async {
                await(channel.delete.queueFuture())
                // Note: Sequenced rather than parallel because the channel
                // might not be deleted due to permissions or other reasons.
                await(ownerByChannel remove channel)
              }.failed.foreach(APIHelper.failure("deleting unused private channel"))
            case _ =>
          }
        }

        val removed = Future.sequence {
          for ((guildId, channelId) <- toRemove)
            yield ownerByChannel.remove(guildId, channelId)
        }
        await(removed) // Propagate exceptions
      }.failed.foreach(APIHelper.failure("processing initial private voice chat state"))
    case GuildVoiceUpdate(_, Some(leftChannel), _) if leftChannel.getMembers.isEmpty =>
      // Last person to leave a channel
      async {
        val user = await(ownerByChannel(leftChannel))
        if (user.isDefined) {
          await(leftChannel.delete.queueFuture())
          await(ownerByChannel remove leftChannel)
        }
      }.failed.foreach(APIHelper.failure("deleting unused private channel"))

    case _ =>
  }
}
