package score.discord.canti.command.api

import com.google.re2j.{Pattern as RE2JPattern}
import net.dv8tion.jda.api.entities.{Guild, Role, User}
import net.dv8tion.jda.api.interactions.commands.{OptionMapping, OptionType}
import net.dv8tion.jda.api.JDA
import score.discord.canti.util.{CommandHelper, ParseUtils}
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.ID
import score.discord.canti.wrappers.jda.IdConversions.*
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.given

sealed trait ArgType[+T](val asJda: OptionType):
  def fromString(invoker: CommandInvoker, s: String): Option[(T, String)]

  def fromJda(invoker: CommandInvoker, m: OptionMapping): Option[T]

  def flatMap[U](f: T => Option[U]): ArgType[U] = MappedArgType(this, f)

  def map[U](f: T => U): ArgType[U] = flatMap(x => Some(f(x)))

  def withFilter(f: T => Boolean): ArgType[T] = flatMap(x => if f(x) then Some(x) else None)

private class MappedArgType[T, U](prev: ArgType[T], f: T => Option[U])
    extends ArgType[U](prev.asJda):
  override def fromString(invoker: CommandInvoker, s: String): Option[(U, String)] =
    prev.fromString(invoker, s).flatMap { case (value, remaining) => f(value).map((_, remaining)) }

  override def fromJda(invoker: CommandInvoker, m: OptionMapping): Option[U] =
    prev.fromJda(invoker, m).flatMap(f)

  override def flatMap[V](f2: U => Option[V]): ArgType[V] =
    MappedArgType(prev, f(_).flatMap(f2))

object ArgType:
  import OptionType.{INTEGER, ROLE, STRING, USER}

  object GreedyString extends ArgType[String](STRING):
    override def fromString(invoker: CommandInvoker, s: String): Option[(String, String)] =
      val result = s.trimnn
      if result.isEmpty then None
      else Some((result, ""))

    override def fromJda(invoker: CommandInvoker, m: OptionMapping): Option[String] =
      Some(m.getAsString)

  object Integer extends ArgType[Long](INTEGER):
    override def fromString(invoker: CommandInvoker, s: String): Option[(Long, String)] =
      val trimmed = s.trimnn
      val (first, remaining) =
        trimmed.indexOf(' ') match
          case -1  => (trimmed, "")
          case pos => trimmed.splitAt(pos)
      first.trimnn.toLongOption.map((_, remaining))

    override def fromJda(invoker: CommandInvoker, m: OptionMapping): Option[Long] =
      if m.getType == asJda then Some(m.getAsLong)
      else None

  object GreedyRole extends ArgType[Either[String, Role]](ROLE):
    override def fromString(
      invoker: CommandInvoker,
      s: String
    ): Option[(Either[String, Role], String)] =
      val role =
        for
          member <- invoker.member
          role <- ParseUtils.findRole(member.getGuild, s.trimnn)
        yield role
      Some((role, ""))

    override def fromJda(invoker: CommandInvoker, m: OptionMapping): Option[Either[String, Role]] =
      if m.getType == asJda then
        val role = m.getAsRole
        Some(Right(role))
      else None

  object MentionedUsers extends ArgType[Seq[User]](USER):
    override def fromString(invoker: CommandInvoker, s: String): Option[(Seq[User], String)] =
      invoker.originatingMessage
        .map(m => (m.getMentionedUsers.asScala.toSeq, s))
        .filter(_._1.nonEmpty)

    override def fromJda(invoker: CommandInvoker, m: OptionMapping): Option[Seq[User]] =
      if m.getType == asJda then Some(Seq(m.getAsUser))
      else None

  final class Disjunction[+T](types: ArgType[T]*) extends ArgType[T](STRING):
    override def fromString(invoker: CommandInvoker, s: String): Option[(T, String)] =
      types.view.map(_.fromString(invoker, s)).flatten.headOption

    override def fromJda(invoker: CommandInvoker, m: OptionMapping): Option[T] =
      types.view
        .map { typ =>
          typ
            .fromString(invoker, m.getAsString)
            .filter(_._2.trimnn.isEmpty) // Ensure whole buffer is consumed
            .map(_._1)
        }
        .flatten
        .headOption

end ArgType
