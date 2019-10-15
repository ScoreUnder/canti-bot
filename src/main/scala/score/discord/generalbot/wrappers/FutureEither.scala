package score.discord.generalbot.wrappers

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

object FutureEither {
  implicit def futureEitherConversion[L, R](me: Future[Either[L, R]]): FutureEitherConverter[L, R] = new FutureEitherConverter[L, R](me)

  def futureFromRight[L, R](me: Either[L, Future[R]]): Future[Either[L, R]] =
    me match {
      case x@Left(_) => Future.successful(x.asInstanceOf[Either[L, R]])
      case Right(x) => x.map(Right(_))
    }
}
