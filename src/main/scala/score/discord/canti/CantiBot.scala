package score.discord.canti

import com.typesafe.config.ConfigFactory
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.dv8tion.jda.api.{JDA, JDABuilder}
import score.discord.canti.collections.CacheCoordinator.*
import score.discord.canti.collections.*
import score.discord.canti.command.*
import score.discord.canti.command.slash.RegisterGuildSlashCommandsCommand
import score.discord.canti.functionality.*
import score.discord.canti.functionality.ownership.{DeleteOwnedMessages, MessageOwnership}
import score.discord.canti.functionality.voicekick.VoiceKick
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.Scheduler
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.io.{File, IOException}
import java.net.URLClassLoader
import java.util
import java.util.concurrent.{Executors, ScheduledExecutorService}
import scala.compiletime.uninitialized
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps
import net.dv8tion.jda.api.events.session.ReadyEvent

@main def main(): Unit =
  CantiBot().start()

class CantiBot:
  private var discord: Option[JDA] = None
  private var executor: ScheduledExecutorService = uninitialized

  def start(): Unit =
    discord match
      case None =>
        val rawConfig =
          ConfigFactory.load(URLClassLoader.newInstance(Array(File(".").toURI.nn.toURL))).nn
        val config = Config.load(rawConfig)
        val bot = JDABuilder
          .create(
            config.token, {
              import GatewayIntent.*
              var intents =
                GUILD_EMOJIS_AND_STICKERS :: /* &find */
                  GUILD_MESSAGE_REACTIONS :: /* Voice kick, &help, &spoiler, delete owned messages */
                  GUILD_VOICE_STATES :: /* Voice kick, private voice chats */
                  DIRECT_MESSAGES :: /* Same as GUILD_MESSAGES, plus spoiler-in-DM */
                  DIRECT_MESSAGE_REACTIONS :: /* Same as GUILD_MESSAGE_REACTIONS */
                  Nil
              if config.hasGuildMembersIntent then
                intents ::= GUILD_MEMBERS /* &find, voice roles, probably other things too */
              if config.hasMessageIntent then
                intents ::= GUILD_MESSAGES /* commands in general, &quote, &read */
              intents.asJava
            }
          )
          .nn
          .disableCache({
            import CacheFlag.*
            util.Arrays.asList(ACTIVITY, CLIENT_STATUS, ONLINE_STATUS, ROLE_TAGS)
          })
          .nn
        val dbConfig = DatabaseConfig.forConfig[JdbcProfile]("database", rawConfig)
        executor = Executors.newScheduledThreadPool(Runtime.getRuntime.nn.availableProcessors).nn
        given Scheduler = Scheduler(executor)
        given MessageOwnership = MessageOwnership(
          UserByMessage(dbConfig, "message_ownership") withCache LruCache.empty(20000)
        )
        given messageCache: MessageCache = MessageCache()
        given ReplyCache = ReplyCache()
        val userCreatedChannels =
          UserByAudioChannel(dbConfig, "user_created_channels") withCache LruCache.empty(2000)

        val eventWaiter = EventWaiter()
        val commands = Commands()
        val quoteCommand = QuoteCommand(messageCache)
        val findCommand = FindCommand()
        val conversations = Conversations()
        val voiceKick =
          VoiceKick(userCreatedChannels, VoiceBanExpiryTable(dbConfig, "voice_ban_expiries"))
        val privateVoiceChats = PrivateVoiceChats(
          userCreatedChannels,
          ChannelByGuild(dbConfig, "voice_default_category") withCache LruCache.empty(2000),
          commands,
          eventWaiter
        )
        val voiceRoles = VoiceRoles(
          RoleByGuild(dbConfig, "voice_active_role") withCache LruCache.empty(2000)
        )
        val spoilers = Spoilers(
          StringByMessage(dbConfig, "spoilers_by_message") withCache LruCache.empty(100),
          conversations
        )

        val registerSlashCommandsCommand = RegisterGuildSlashCommandsCommand(owner = config.owner)
        val helpCommand = HelpCommand(commands)
        privateVoiceChats.allCommands.foreach(commands.register)
        voiceKick.allCommands.foreach(commands.register)
        voiceRoles.allCommands.foreach(commands.register)
        spoilers.allCommands.foreach(commands.register)
        commands.register(helpCommand)
        commands.register(PlayCommand(owner = config.owner))
        commands.register(StopCommand(this, owner = config.owner))
        commands.register(FuriganaCommand())
        commands.register(BlameCommand())
        commands.register(BotInfoCommand(owner = config.owner))
        commands.register(findCommand)
        commands.register(registerSlashCommandsCommand)
        val readCommand = ReadCommand(messageCache)
        if readCommand.available then commands.register(readCommand)
        commands.register(PingCommand())
        commands.register(PermissionDiffCommand())

        if config.hasMessageIntent then commands.register(quoteCommand)

        val slashCommands = SlashCommands(commands.all*)
        registerSlashCommandsCommand.slashCommands = Some(slashCommands)

        if config.hasMessageIntent then
          bot.addEventListeners(commands, quoteCommand.GreentextListener())

        bot.addEventListeners(
          slashCommands,
          voiceRoles,
          privateVoiceChats,
          DeleteOwnedMessages(),
          conversations,
          spoilers,
          findCommand.ReactListener,
          voiceKick,
          eventWaiter,
          messageCache
        )

        bot.addEventListeners(
          { e =>
            e.nn match
              case ev: ReadyEvent =>
                // TODO: Make configurable?
                val jda = ev.getJDA.nn
                jda.getPresence.nn
                  .setActivity(Activity `playing` s"Usage: ${commands.prefix}${helpCommand.name}")
                jda.setRequiredScopes("bot", "applications.commands")
              case _ =>
          }: EventListener,
          EventLogger()
        )

        // The discord bot spawns off new threads and its event handlers expect
        // everything to have been set up, so this must come last.
        discord = Some(bot.build().nn)

      case Some(_) =>
        throw UnsupportedOperationException(
          "Cannot start() the same bot object twice without at least stopping in between."
        )

  def stop(timeout: Duration = 1 minute): Unit =
    discord match
      case Some(bot) =>
        executor.shutdownNow()
        bot.shutdown()
        discord = None
        try executor.awaitTermination(timeout.length, timeout.unit)
        catch case _: InterruptedException => ()
        val httpClient = bot.getHttpClient.nn
        httpClient.dispatcher().nn.executorService().nn.shutdown()
        httpClient.connectionPool().nn.evictAll()
        try httpClient.cache().?.foreach(_.close())
        catch case _: IOException => ()

      case None =>
        throw UnsupportedOperationException("Cannot stop() a bot which has not start()ed")
end CantiBot
