package score.discord.canti.wrappers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FutureEither[L, R](val value: Future[Either[L, R]]) extends AnyVal:
  def map[R2](f: R => R2): Future[Either[L, R2]] = value.map(_.map(f))

  def flatMap[R2](f: R => Future[Either[L, R2]]): Future[Either[L, R2]] =
    value flatMap {
      case Left(_) => value.asInstanceOf[Future[Either[L, R2]]]
      case Right(x) => f(x)
    }

object FutureEither:
  extension [L, R](value: Future[Either[L, R]])
    def flatView = FutureEither[L, R](value)

  extension [L, R](value: Either[L, Future[Either[L, R]]])
    def toFuture: Future[Either[L, R]] = value.fold(x => Future.successful(Left(x)), identity)

  extension [V](value: Future[V])
    def recoverToEither[L](pf: PartialFunction[Throwable, L]): Future[Either[L, V]] =
      value.map(Right(_)).recover(pf.andThen(Left(_)))
