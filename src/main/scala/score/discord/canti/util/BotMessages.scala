package score.discord.canti.util

import java.awt.Color

import net.dv8tion.jda.api.EmbedBuilder

/** Helpers to create consistently-styled EmbedBuilders for bot output */
object BotMessages:
  val ERROR_COLOR = Color(240, 100, 100)
  val OKAY_COLOR = Color(100, 130, 240)

  def error(message: String): EmbedBuilder = plain(message).setColor(ERROR_COLOR).nn

  def okay(message: String): EmbedBuilder = plain(message).setColor(OKAY_COLOR).nn

  def plain(message: String): EmbedBuilder = EmbedBuilder().setDescription(message).nn
