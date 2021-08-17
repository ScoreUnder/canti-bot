package score.discord.canti.collections

import net.dv8tion.jda.api.entities.{Guild, User, VoiceChannel}
import score.discord.canti.functionality.voicekick.VoiceBanExpiry
import score.discord.canti.util.DBUtils
import score.discord.canti.wrappers.database.IDMapping.*
import score.discord.canti.wrappers.jda.ID
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VoiceBanExpiryTable(dbConfig: DatabaseConfig[? <: JdbcProfile], tableName: String)
    extends AsyncMap[(ID[Guild], ID[VoiceChannel], ID[User]), VoiceBanExpiry]:

  import dbConfig.profile.api.*

  private class VoiceBanExpirySchema(tag: Tag, name: String)
      extends Table[(ID[Guild], ID[VoiceChannel], ID[User], Long, Boolean)](tag, name):

    val guildId = column[ID[Guild]]("guild")
    val channelId = column[ID[VoiceChannel]]("channel")
    val userId = column[ID[User]]("user")
    val expiry = column[Long]("expiry_unix_timestamp")
    val explicitGrant = column[Boolean]("was_granted_explicitly")

    val pk = primaryKey("primary", (guildId, channelId, userId))

    override def * = (guildId, channelId, userId, expiry, explicitGrant)

  private val database = dbConfig.db
  private val voiceBanExipiryTable =
    TableQuery[VoiceBanExpirySchema](new VoiceBanExpirySchema(_: Tag, tableName))
  private type C[T] = ConstColumn[ID[T]]
  private val lookupQuery = Compiled(
  (guildId: C[Guild], vcId: C[VoiceChannel], userId: C[User]) =>
    voiceBanExipiryTable
      .filter { t => t.guildId === guildId && t.channelId === vcId && t.userId === userId }
      .map { row => (row.expiry, row.explicitGrant) })
  DBUtils.ensureTableCreated(dbConfig, voiceBanExipiryTable, tableName)

  override def get(key: (ID[Guild], ID[VoiceChannel], ID[User])): Future[Option[VoiceBanExpiry]] =
    dbConfig.db.run(lookupQuery(key).result).map(_.headOption.map(VoiceBanExpiry.tupled))

  override def update(
    key: (ID[Guild], ID[VoiceChannel], ID[User]),
    value: VoiceBanExpiry
  ): Future[Int] =
    database.run(
      voiceBanExipiryTable
        .insertOrUpdate(key._1, key._2, key._3, value.timestamp, value.explicitGrant)
    )

  override def remove(key: (ID[Guild], ID[VoiceChannel], ID[User])): Future[Int] =
    database.run(lookupQuery(key).delete)

  override def items: Future[Seq[((ID[Guild], ID[VoiceChannel], ID[User]), VoiceBanExpiry)]] =
    dbConfig.db
      .run(voiceBanExipiryTable.result)
      .map(_.map(t => ((t._1, t._2, t._3), VoiceBanExpiry(t._4, t._5))))
