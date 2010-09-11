package coref

import scala.collection.JavaConversions._
import java.{util => ju}
import edu.berkeley.nlp.util.Logger
import reflect.BeanProperty

class CorefCluster(@BeanProperty val chains: ju.List[CorefChain]) {
  private val chainsById = (for (c <- chains) yield c.id -> c).toMap
  //def getChains() = chains
  def getChain(id: String): CorefChain = chainsById(id)
  def invClustering: ju.Map[Mention,String] = scala.collection.mutable.Map() ++
    (for (c <- chains; m <- c.ments) yield m -> c.id).toMap

  def examine(): Unit = {
    Logger.startTrack("CorefCluster")
    for (c <- chains) {
      Logger.startTrack("Chain(%s)".format(c.id))
      for (m <- c.ments) {
        Logger.logs(m.toString)
      }
      Logger.endTrack
    }
    Logger.endTrack
  }
}