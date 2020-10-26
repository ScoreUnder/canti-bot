package score.discord.generalbot.commands

import org.scalatest.{FlatSpec, Matchers}
import score.discord.generalbot.TestFixtures
import score.discord.generalbot.command.FuriganaCommand

class FuriganaCommandTest extends FlatSpec with Matchers{
  val fixture = TestFixtures.default
  import fixture.implicits._

  val furiganaCommand = new FuriganaCommand

  "parseInput" should "accept plain text" in {
    furiganaCommand.parseInput("asdf asdfasdf") should contain(("asdf asdfasdf", ""))
  }

  it should "accept a furigana group" in {
    furiganaCommand.parseInput("{asdf:asdfasdf}") should contain(("asdf", "asdfasdf"))
  }

  it should "accept a furigana group with no furigana" in {
    furiganaCommand.parseInput("{asdf:}") should contain(("asdf", ""))
  }

  it should "accept a furigana group with no text" in {
    furiganaCommand.parseInput("{:asdfasdf}") should contain(("", "asdfasdf"))
  }

  it should "trim empty groups" in {
    val parsed = furiganaCommand.parseInput("{asdf:ghi}{:}{:}{:zxc}{vbn:}{qwe:rty}")
    parsed should not contain(("", ""))
  }

  it should "split newlines properly" in {
    furiganaCommand.parseInput("asdf\nghi") should equal(Seq(("asdf", ""), ("\n", ""), ("ghi", "")))
    furiganaCommand.parseInput("a\n\n\na").count(_ == ("\n", "")) should be(3)
  }

  it should "give exact correct results" in {
    furiganaCommand.parseInput("{郵便局:ゆうびんきょく}に{行:い}きました") should equal(
      Seq(("郵便局", "ゆうびんきょく"), ("に", ""), ("行", "い"), ("きました", "")))
    furiganaCommand.parseInput("{asdf:ghi}{:}{:}{:zxc}{vbn:}{qwe:rty}") should equal(
      Seq(("asdf", "ghi"), ("", "zxc"), ("vbn", ""), ("qwe", "rty")))
  }

  it should "accept fullwidth separators" in {
    furiganaCommand.parseInput("｛郵便局：ゆうびんきょく｝に｛行:い｝きました") should equal(
      Seq(("郵便局", "ゆうびんきょく"), ("に", ""), ("行", "い"), ("きました", "")))
    furiganaCommand.parseInput("{郵便局：ゆうびんきょく｝に{行：い}きました") should equal(
      Seq(("郵便局", "ゆうびんきょく"), ("に", ""), ("行", "い"), ("きました", "")))
  }
}
