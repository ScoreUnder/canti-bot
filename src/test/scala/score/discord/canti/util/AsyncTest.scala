package score.discord.canti.util

import cps.*
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.adhocExtensions // AnyFlatSpec

class AsyncTest extends AnyFlatSpec with should.Matchers:
  // using FutureAsyncMonadButGood from this package

  "async" `should` "report errors" in {
    def raise(x: Throwable): Unit = throw x
    val ex1 = IllegalArgumentException("blah")
    val ex2 = IllegalArgumentException("blah 2")
    val ex3 = IllegalArgumentException("blah 3")
    val a1 = async { raise(ex1) }
    val a2 = async { await(a1) }
    val a3 = async { raise(ex2); await(a1) }
    val a4 = async { 5 }
    val a5 = async { await(a4); raise(ex3) }
    val a6 = async { await(a4); await(a4); await(a5); await(a1) }
    val a7 = async { await(a4); await(a4); await(a1); await(a5) }

    Await.ready(a1, 1.second)
    Await.ready(a2, 1.second)
    Await.ready(a3, 1.second)
    Await.ready(a4, 1.second)
    Await.ready(a5, 1.second)
    Await.ready(a6, 1.second)
    Await.ready(a7, 1.second)
    a1.value `should` equal(Some(Failure(ex1)))
    a2.value `should` equal(Some(Failure(ex1)))
    a3.value `should` equal(Some(Failure(ex2)))
    a4.value `should` equal(Some(Success(5)))
    a5.value `should` equal(Some(Failure(ex3)))
    a6.value `should` equal(Some(Failure(ex3)))
    a7.value `should` equal(Some(Failure(ex1)))
  }
