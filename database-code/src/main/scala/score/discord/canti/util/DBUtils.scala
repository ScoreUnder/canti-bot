package score.discord.canti.util

import slick.basic.DatabaseConfig
import slick.jdbc.meta.MTable
import slick.lifted.TableQuery
import slick.relational.RelationalProfile

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object DBUtils {

  /** Creates a table if it does not already exist. Blocking. */
  def ensureTableCreated(
    dbConfig: DatabaseConfig[_ <: RelationalProfile],
    table: TableQuery[_ <: RelationalProfile#Table[_]],
    tableName: String
  ): Unit = {
    import dbConfig.profile.api._
    // Ensure table is created on startup
    Await.result(
      dbConfig.db
        .run(MTable.getTables)
        .map(v => {
          val names = v.map(mt => mt.name.name)
          if (!names.contains(tableName)) {
            Await.result(dbConfig.db.run(table.schema.create), Duration.Inf)
          }
        }),
      Duration.Inf
    )
  }
}
