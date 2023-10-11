package score.discord.canti.wrappers.jda

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import score.discord.canti.util.MessageUtils
import score.discord.canti.wrappers.NullWrappers.*
import score.discord.canti.wrappers.jda.RichSnowflake.rawId

object RichUser:
  extension (me: User)
    /** This user's name */
    def name = me.getName.nn

    /** This user's discriminator as a String */
    def discriminator = me.getDiscriminator.nn

    /** The mention string for this user */
    def mention = me.getAsMention.nn

    /** Gets the user's name and discriminator. Looks like what you would type in Discord if you
      * wanted to mention the user. Not sanitised.
      */
    def mentionAsText = s"@${me.getName}#${me.getDiscriminator}"

    /** This user's mention with a sanitised version of their username at the end */
    def mentionWithName =
      val fullName = MessageUtils.sanitise(me.mentionAsText)
      s"${me.mention} ($fullName)"

    /** A debug-friendly plaintext representation of this user object */
    def unambiguousString = s"User(${me.rawId} /* ${me.name}#${me.discriminator} */)"

    /** Checks whether the user has read access to the channel.
      *
      * @param channel
      *   channel to check
      * @return
      *   `true` if the user can see this channel
      */
    def canSee(channel: GuildChannel): Boolean =
      channel.getGuild.nn.getMember(me).?.exists(_.hasPermission(channel, Permission.VIEW_CHANNEL))
