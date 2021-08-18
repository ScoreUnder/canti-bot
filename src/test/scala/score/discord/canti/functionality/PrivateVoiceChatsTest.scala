package score.discord.canti.functionality

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import org.scalatest.PrivateMethodTester
import score.discord.canti.TestFixtures
import score.discord.canti.collections.NullCacheBackend

class PrivateVoiceChatsTest extends AnyFlatSpec with should.Matchers with PrivateMethodTester:
  val fixture = TestFixtures.default
  import fixture.{given, *}

  val pvc = PrivateVoiceChats(NullCacheBackend(), NullCacheBackend(), commands, EventWaiter())

  val prefixOrUpdateNumberM = PrivateMethod[String](Symbol("prefixOrUpdateNumber"))
  def prefixOrUpdateNumber(name: String, prefix: String): String =
    pvc.invokePrivate(prefixOrUpdateNumberM(name, prefix))

  "prefixOrUpdateNumber" should "add a prefix correctly" in {
    prefixOrUpdateNumber("name", "prefix ") should equal("prefix name")
    prefixOrUpdateNumber("prefix2 name", "prefix ") should equal("prefix prefix2 name")
  }

  it should "increase the number if a prefix already exists" in {
    prefixOrUpdateNumber("prefix2 name", "prefix") should equal("prefix2 name 2")
    prefixOrUpdateNumber("prefix name", "prefix ") should equal("prefix name 2")
    prefixOrUpdateNumber("prefix name 2", "prefix ") should equal("prefix name 3")
    prefixOrUpdateNumber("5", "") should equal("6")
  }

  it should "not fail on very large numbers" in {
    val orig = "2345345378927468532154869879645439802349802349802439832409"
    prefixOrUpdateNumber(orig, "") should not equal orig
  }
