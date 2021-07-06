package score.discord.canti.collections

import scala.concurrent.Future

trait AsyncMap[K, V]:
  def get(key: K): Future[Option[V]]

  def update(key: K, value: V): Future[Int]

  def remove(key: K): Future[Int]

  def items: Future[Seq[(K, V)]]
