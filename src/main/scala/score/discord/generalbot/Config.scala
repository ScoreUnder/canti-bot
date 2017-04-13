package score.discord.generalbot

import java.io.{File, FileReader}
import java.util.{Map => JMap}

import org.yaml.snakeyaml.Yaml

import scala.reflect.ClassTag

class Config(val token: String)

object Config {
  val BOT_OWNER = 226521865537978368L  // TODO: Make this configurable

  def load(file: File) = {
    val map = new Yaml().load(new FileReader(file)) match {
      case m: JMap[_, _] => m
      case _ => throw new NoSuchElementException(s"Expected a map at the config root in file: $file")
    }

    def grab[T](path: String)(implicit tag: ClassTag[T]): T = {
      map.get(path) match {
        case obj: T => obj
        case _ => throw new ClassCastException(s"Expected a $tag at $path in the config file $file")
      }
    }

    new Config(
      token = grab[String]("token")
    )
  }
}
