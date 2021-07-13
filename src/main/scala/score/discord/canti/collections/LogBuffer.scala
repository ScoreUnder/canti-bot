package score.discord.canti.collections

import java.util.NoSuchElementException
import scala.collection.mutable

class LogBuffer[T](capacity: Int) extends mutable.IndexedSeq[T]:
  private val buffer = new Array[Any](capacity)
  private var readPos, writePos = 0
  private var isEmpty_ = true

  override def length: Int =
    if isEmpty_ then 0
    else
      readPos - writePos match
        case x if x > 0 => x
        case x          => x + buffer.length

  override def isEmpty: Boolean = isEmpty_

  def ::=(elem: T): Unit =
    val nextWrite = writePos match
      case 0 => buffer.length - 1
      case x => x - 1
    buffer(nextWrite) = elem
    if readPos == writePos && !isEmpty then readPos = nextWrite
    writePos = nextWrite
    isEmpty_ = false

  override def apply(idx: Int) =
    buffer(idxToBufferIdx(idx)).asInstanceOf[T]

  override def update(idx: Int, elem: T): Unit =
    buffer(idxToBufferIdx(idx)) = elem

  private def idxToBufferIdx(idx: Int) =
    if idx < 0 || idx >= size then
      throw IndexOutOfBoundsException(s"$idx out of bounds for buffer of size $size")
    (idx + writePos) % buffer.length

  def findAndUpdate(condition: T => Boolean)(replace: T => T): this.type =
    val index = this.indexWhere(condition)
    if index != -1 then this(index) = replace(this(index))
    this

  override def iterator: Iterator[T] = new Iterator[T]:
    private var myPos = writePos
    private var iterated = LogBuffer.this.isEmpty

    override def hasNext: Boolean = myPos != readPos || !iterated

    override def next(): T =
      if !hasNext then throw NoSuchElementException()
      val pos = myPos
      val result = buffer(pos).asInstanceOf[T]
      myPos = pos + 1 match
        case x if x == buffer.length => 0
        case x                       => x
      iterated = true
      result
end LogBuffer
