package score.discord.canti.util

import cps.*
import scala.concurrent.*
import scala.concurrent.duration.*
import scala.quoted.*
import scala.util.*

given FutureAsyncMonadButGood(using ExecutionContext): CpsSchedulingMonad[Future] with
  type F[+T] = Future[T]
  override type WF[T] = F[T]

  override inline def pure[T](t: T): Future[T] = Future.successful(t)

  override inline def map[A, B](fa: F[A])(f: A => B): F[B] =
    fa.map(f)

  override inline def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] =
    fa.flatMap(f)

  override inline def error[A](e: Throwable): F[A] =
    Future.failed(e)

  override def mapTry[A, B](fa: F[A])(f: Try[A] => B): F[B] =
    fa.transform { v => Success(f(v)) }

  override def flatMapTry[A, B](fa: F[A])(f: Try[A] => F[B]): F[B] =
    fa.transformWith { v => f(v) }

  override def restore[A](fa: F[A])(fx: Throwable => F[A]): F[A] =
    fa.recoverWith { case ex => fx(ex) }

  override def adoptCallbackStyle[A](source: (Try[A] => Unit) => Unit): F[A] =
    val p = Promise[A]
    source(p.complete(_))
    p.future

  override inline def spawn[A](op: => F[A]): F[A] = Future(op).flatten

  override def tryCancel[A](op: Future[A]): Future[Unit] =
    Future.failed(UnsupportedOperationException("FutureAsyncMonad.tryCancel is unsupported"))
