package score.discord.canti.command

import net.dv8tion.jda.api.entities.{Activity, Message, User}
import score.discord.canti.command.api.{ArgSpec, CommandInvocation, CommandPermissions}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.{ID, RetrievableMessage}
import net.dv8tion.jda.internal.JDAImpl
import net.dv8tion.jda.internal.requests.ratelimit.{BotRateLimiter, IBucket}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*
import scala.util.Random
import scala.concurrent.Future

class DebugCommand(owner: ID[User]) extends GenericCommand:
  private val logger = loggerOf[DebugCommand]

  override def name = "debug"

  override def description = "Print debug info to console"

  override def execute(ctx: CommandInvocation): Future[RetrievableMessage] =
    val jda = ctx.jda.asInstanceOf[JDAImpl]
    val rateLimiter = jda.getRequester.nn.getRateLimiter.asInstanceOf[BotRateLimiter]
    val field = classOf[BotRateLimiter].getDeclaredField("rateLimitQueue").nn
    field.setAccessible(true)
    val queue = field.get(rateLimiter).asInstanceOf[ConcurrentHashMap[? <: IBucket, ?]]
    queue.forEachKey(
      32L,
      { (kk: IBucket | Null) =>
        val bucket = kk.nn
        val requests = bucket.getRequests.nn.asScala
        if requests.nonEmpty then
          val reqSample = Random.shuffle(requests).take(10).map(_.getRoute.toString).mkString("\n")
          logger.debug(s"${requests.size} requests in bucket, sample:\n${reqSample}")
      }: java.util.function.Consumer[IBucket]
    )
    Future.never

  override def argSpec = Nil

  override val permissions = CommandPermissions.OneUserOnly(owner)
