package score.discord.canti.util

import java.awt.Color

import net.dv8tion.jda.api.EmbedBuilder

/** Helpers to create consistently-styled EmbedBuilders for bot output */
object BotMessages:
  val ERROR_COLOR = Color(240, 100, 100)
  val OKAY_COLOR = Color(100, 130, 240)

  def error(message: String) = plain(message).setColor(ERROR_COLOR)

  def okay(message: String) = plain(message).setColor(OKAY_COLOR)

  def plain(message: String) = EmbedBuilder().setDescription(message)
