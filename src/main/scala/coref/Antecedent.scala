package coref

import hobbs.{Pronoun, FVPair, LogLinearDiscourseModule}
import java.{util => ju}
import scala.collection. JavaConversions._

class Antecedent(val ment: Mention, val ant: Mention) {

  // Hack Hack - feats source hardcoded
  lazy val featVec: ju.List[FVPair] = LogLinearDiscourseModule.extractFeatures(this)

  lazy val treeDist =  ment.doc.getTreeDistance(ment, ant) 

  def sentDist = math.abs(ment.sent.getIndex() - ant.sent.getIndex())

  def mentDist = math.abs(ment.index - ant.index).asInstanceOf[Double]

  def isNull = ant == null
}

object Antecedent {

  private def dominates(ant: Mention, ment: Mention): Boolean = {
//    val antNode = ant.node.getLabel.getOriginalNode
//    val mentNode = ment.node.getLabel.getOriginalNode
//    ment.doc.tpf.findLowestCommonAncestor(antNode,mentNode) eq antNode
    ment.parentMention == ant
  }

  private def reflexiveViolation(ant: Mention, ment: Mention): Boolean = {
    if (ment.sentIndex != ant.sentIndex) return false
    if (!ment.isPronoun || ant.parentRelation == null || ment.parentRelation == null) return false
    val mentParentIndex = ment.parentRelation.getGovIndex
    val antParentIndex = ant.parentRelation.getGovIndex
    val relns = Set(ment.parentRelation.getLabel,ant.parentRelation.getLabel)
    if (mentParentIndex == antParentIndex && relns == Set("nsubj","dobj")) {
      // If its a pronoun or a wh, its okay as in "john is the man who play him"
       if (Pronoun.reflexives(ment.headWord.toLowerCase) || ment.headPOS.startsWith("WP")) {
         return false
       } else {
         val sent = ment.sent.words
         return true
       }
    }
    return false
  }

  /**
   * Retrieve Antecedents for a given mention.
   * - Returns determinstic coreference
   * - Enforces X-over-X unless unless appositive or wh-constrained
   */
  private def possibleAnts(ment: Mention, numPastSents: Int): java.util.List[Mention] = {
//    val forced = SyntacticConfigurations.getForced(ment)
//    if (forced != null) return Seq(forced)
    var res = Seq[Mention]()
    for (i <- 0 to (numPastSents+1); if (ment.sentIndex-i >= 0)) {
      val sent = ment.doc.sents.get(ment.sentIndex-i)
      val relevantAnts: Seq[Mention] =
        if (i == 0) {
          sent.getMentions.filter(a => a.index < ment.index && !dominates(a,ment) && !reflexiveViolation(a,ment))
        }
        else sent.getMentions
      res = res ++
            (if (ment.isNominal) relevantAnts.filter(!_.isPronoun)
             else relevantAnts)
    }
    asList(res.toBuffer)
  }

  /**
   *
   *  - Last Antecedent is null antecedent to represent a mention is
   *    starting a new entity
   */
  def getPossibleAnts(ment: Mention): ju.List[Antecedent] = {
    val forced = SyntacticConfigurations.getForced(ment)
    if (forced != null) return Seq(new Antecedent(ment,forced))            
    (ment.mentType match {
      case MentionType.NOMINAL => possibleAnts(ment,5).map(a => new Antecedent(ment,a))
      case MentionType.PRONOUN => possibleAnts(ment,2).map(a => new Antecedent(ment,a))
      case _ => error("Can't Ask for antecedents of this mention type")
    }) ++ Seq(new Antecedent(ment,null))
  }
}