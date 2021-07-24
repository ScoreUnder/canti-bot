package score.discord.canti.command.api

import com.codedx.util.MapK

final case class CommandInvocation(
  prefix: String,
  name: String,
  args: MapK[ArgSpec, [T] =>> T],
  invoker: CommandInvoker
):
  def jda = invoker.user.getJDA
