package score.discord.canti.wrappers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

class FutureEither[L, R](val value: Future[Either[L, R]]) extends AnyVal {
  def map[R2](f: R => R2): Future[Either[L, R2]] = value.map(_.map(f))

  def flatMap[R2](f: R => Future[Either[L, R2]]): Future[Either[L, R2]] =
    value flatMap {
      case Left(_) => value.asInstanceOf[Future[Either[L, R2]]]
      case Right(x) => f(x)
    }
}

class FutureEitherConverter[L, R](val value: Future[Either[L, R]]) extends AnyVal {
  def flatView = new FutureEither[L, R](value)
}

class EitherFutureConverter[L, R](val value: Either[L, Future[Either[L, R]]]) extends AnyVal {
  def toFuture: Future[Either[L, R]] = value.fold(x => Future.successful(Left(x)), identity)
}

class FutureConverter[V](val value: Future[V]) extends AnyVal {
  def recoverToEither[L](pf: PartialFunction[Throwable, L]): Future[Either[L, V]] =
    value.map(Right(_)).recover(pf.andThen(Left(_)))
}

object FutureEither {
  implicit def futureEitherConversion[L, R](me: Future[Either[L, R]]): FutureEitherConverter[L, R] = new FutureEitherConverter[L, R](me)

  implicit def eitherFutureConversion[L, R](me: Either[L, Future[Either[L, R]]]): EitherFutureConverter[L, R] = new EitherFutureConverter[L, R](me)

  implicit def futureConversion[V](me: Future[V]): FutureConverter[V] = new FutureConverter[V](me)
}
