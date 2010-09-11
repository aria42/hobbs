package coref.hobbs

import java.{util => ju}
import scala.collection.JavaConversions._
import coref.Antecedent
import edu.berkeley.nlp.math.DoubleArrays


class DiscourseSuffStats(val fvs: ju.List[ju.List[FVPair]], val weights: Array[Double])

object DiscourseSuffStats {

  def fromAntFactors(elems: Seq[(Antecedent,Double)]): DiscourseSuffStats = {
    val pruned = elems.sortBy(-_._2).take(20)        
    val fvs = pruned.map(_._1.featVec)
    val weights = pruned.map(_._2).toArray
    DoubleArrays.probabilisticNormalize(weights)
    require(fvs.size == weights.size)
    if(fvs.isEmpty) {
      val breakHere = true
    }
    new DiscourseSuffStats(fvs,weights)
  }

}