package score.discord.canti.command

import net.dv8tion.jda.api.entities.Message
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import score.discord.canti.TestFixtures
import score.discord.canti.command.api.*
import score.discord.canti.util.BotMessages
import score.discord.canti.wrappers.jda.RetrievableMessage

import scala.concurrent.Future

class HelpCommandTest extends AnyFlatSpec with should.Matchers:
  val fixture = TestFixtures.default
  import fixture.{*, given}

  private val dummyDesc = "dummy command"
  private val dummyLongDesc = "this command is dummy"

  private class DummyCommand(val name: String) extends GenericCommand:
    override def description = dummyDesc
    override def longDescription(invocation: String) = dummyLongDesc
    override def permissions = CommandPermissions.Anyone
    override def argSpec = Nil
    override def execute(ctx: CommandInvocation): Future[RetrievableMessage] = ???

  (1 to 100)
    .map(n => s"cmd$n")
    .map(DummyCommand(_))
    .foreach(commands.register)

  private val cmd = HelpCommand(commands)
  commands.register(cmd)

  "The &help command" should "reject negative page numbers" in {
    val embed = testCommand("&help -1").getEmbeds.get(0).nn
    embed.getColor should be(BotMessages.ERROR_COLOR)
    embed.getDescription.nn shouldNot include(dummyDesc)
  }

  it should "reject excessive page numbers" in {
    val embed = testCommand("&help 100").getEmbeds.get(0).nn
    embed.getColor should be(BotMessages.ERROR_COLOR)
    embed.getDescription.nn should include("That page does not exist")
  }

  it should "show command descriptions" in {
    val embed = testCommand("&help").getEmbeds.get(0).nn
    embed.getDescription.nn should include(dummyDesc)
  }

  it should "show long descriptions" in {
    val helpText = testCommand("&help cmd53").getEmbeds.get(0).nn.getDescription.nn
    helpText should include("cmd53")
    helpText should include(dummyDesc)
    helpText should include(dummyLongDesc)
  }

  it should "link to github from default help pages" in {
    val embed = testCommand("&help 3").getEmbeds.get(0).nn
    embed.getDescription.nn should include("github.com/")
  }
end HelpCommandTest
