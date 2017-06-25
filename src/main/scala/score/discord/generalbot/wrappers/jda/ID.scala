package score.discord.generalbot.wrappers.jda

import slick.lifted.MappedTo

final class ID[+T](val value: Long) extends AnyVal with MappedTo[Long]
