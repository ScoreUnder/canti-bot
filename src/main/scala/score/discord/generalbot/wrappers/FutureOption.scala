package score.discord.generalbot.wrappers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

class FutureOption[T](val value: Future[Option[T]]) extends AnyVal {
  def filter(f: (T) => Boolean): Future[Option[T]] = value.map(_.filter(f))

  def map[U](f: (T) => U): Future[Option[U]] = value.map(_.map(f))

  def flatMap[U](f: (T) => Future[Option[U]]): Future[Option[U]] =
    value flatMap {
      case None => value.asInstanceOf[Future[Option[U]]]
      case Some(x) => f(x)
    }
}

class FutureOptionConverter[T](val value: Future[Option[T]]) extends AnyVal {
  implicit def flatView = new FutureOption[T](value)
}

object FutureOption {
  implicit def futureOptionConversion[T](me: Future[Option[T]]): FutureOptionConverter[T] = new FutureOptionConverter[T](me)
}
