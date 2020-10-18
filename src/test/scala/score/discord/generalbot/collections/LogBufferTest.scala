package score.discord.generalbot.collections

import org.scalatest.{FlatSpec, Matchers}

class LogBufferTest extends FlatSpec with Matchers {
  def bufWithContent[T](content: Seq[T], capacity: Int = 10): LogBuffer[T] = {
    val buf = new LogBuffer[T](capacity)
    content.foreach(buf ::= _)
    buf
  }

  "size" should "be equal to the number of elements" in {
    val capacity = 10
    1 to capacity*4 foreach { n =>
      val buf = new LogBuffer[Int](capacity)
      1 to n foreach(buf ::= _)
      buf.size should be(n min capacity)
    }
  }

  "head" should "be equal to the most recently added element" in {
    val capacity = 10
    val buf = new LogBuffer[Int](capacity)
    1 to capacity*4 foreach { n =>
      buf ::= n
      buf.head should be(n)
    }
  }

  "last" should "be equal to the first added element, while capacity is not exceeded" in {
    val capacity = 10
    val buf = new LogBuffer[Int](capacity)
    1 to capacity foreach { n =>
      buf ::= n
      buf.last should be(1)
    }
  }

  it should "be equal to the element added $capacity-1 elements ago, when capacity is full" in {
    val capacity = 10
    val buf = bufWithContent(content = 0 until capacity, capacity = capacity)
    capacity to capacity*4 foreach { n =>
      buf ::= n
      buf.last should be(n - (capacity - 1))
    }
  }

  "iterator" should "return the same results as sequential apply()" in {
    val capacity = 10
    val buf = new LogBuffer[Int](capacity)

    def iteratorChecks(): Unit = {
      buf.iterator.zipWithIndex.map {
        case (v, i) => v should be(buf(i))
      }
      buf.iterator.toVector should equal(buf.toVector)
      buf.iterator.size should equal(buf.size)
    }

    iteratorChecks()  // Test empty case
    1 to capacity*4 foreach { n =>
      buf ::= n
      iteratorChecks()
    }
  }

  "findAndUpdate" should "replace exactly one element" in {
    val buf = bufWithContent(1 to 20)
    buf should not contain 0
    val oldCount = buf.count(_ > 15)
    oldCount should not be 0
    buf.findAndUpdate(_ > 15)(_ => 0)
    buf.count(_ > 15) should be(oldCount - 1)
    buf should contain(0)
  }

  it should "not fail when nothing is found" in {
    val buf = bufWithContent(1 to 5)
    buf should not contain 20
    buf.findAndUpdate(_ == 20)(_ => 123)
    buf should not contain 20
    buf should not contain 123
    buf.size should be(5)
    buf.toSeq should equal(5 to 1 by -1)
  }

  "isEmpty" should "return true when empty" in {
    new LogBuffer[Int](20).isEmpty should be(true)
  }

  it should "return false when non-empty" in {
    val capacity = 10
    val buf = new LogBuffer[Int](capacity)
    1 to capacity*4 foreach { n =>
      buf ::= n
      buf.isEmpty should be(false)
    }
  }
}
