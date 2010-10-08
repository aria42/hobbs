package coref

import edu.berkeley.nlp.syntax.{Tree, RichLabel}
import fig.basic.{Pair => figPair}
import hobbs.{HobbsGlobals, Entity, IDiscourseModule}
import scala.collection.JavaConversions._
import java.{util => ju}
import scala.reflect.BeanProperty
import mochi.nlp.Relation

object Mention {
  val removedParentRelns = Set("pobj")
  val removedDepRelns =
    Set("neg", "det", "cop", "aux", "auxpass", "number", "rel", "parataxis", "num")
 // val allowedDepRelns = Set("nn","appos")

}

class Mention(@BeanProperty val sent: Sentence,
              @BeanProperty val node: Tree[RichLabel])
     extends java.lang.Iterable[fig.basic.Pair[MentProp,String]]
{

  /* Ugly Mutable State - Externally Set */
  // Index in document
  @BeanProperty var index: Int = -1
  // Referring Mention Index (will stat -1 if pronoun)
  @BeanProperty var referIndex: Int = -1
  // Hack, stores variational antecedent term
  // will stay null for propers
  var antFactor: Array[Double] = null
  // Variational term over entAssignment
  // will be point mass for referring
  var entFactor: Array[Double] = null
  
  /* Lots of (Lazy) Immutable CTOR derived state - Yay
   * Alot of these values are lazy since each pass over
   * the data uses different elements. Once a field
   * is needed it tends to get used over and over
   */

  @BeanProperty val doc = sent.doc
  @BeanProperty val sentIndex = sent.index
  @BeanProperty val headIndex = node.getLabel.getHeadIndex
  @BeanProperty lazy val parentNode = sent.parentMap.get(node)
  @BeanProperty lazy val parentMention = doc.getNodeToMention().get(parentNode)
  @BeanProperty lazy val parentRelation =
    sent.relns.find(r => r.getDepIndex == headIndex) match {
      case Some(r) => r
      case _ => null
    }
  @BeanProperty lazy val parentAndWord: fig.basic.Pair[String,String] = {
    if (parentRelation == null) {
      fig.basic.Pair.makePair("*null*", "*null*")
    } else {
      val parentWord = sent.words.get(parentRelation.getGovIndex())
      fig.basic.Pair.makePair(parentRelation.getLabel,
                              (if (HobbsGlobals.lowercaseHead) parentWord.toLowerCase
                               else parentWord))
    }
  }
  

  @BeanProperty lazy val headWord = {
    val res = node.getLabel.getHeadWord
    if (HobbsGlobals.lowercaseHead) res.toLowerCase
    else res
  }

  @BeanProperty lazy val headPOS = node.getLabel.getHeadTag

  @BeanProperty lazy val words: ju.List[String] = node.getLabel.getOriginalNode.getYield

  
  @BeanProperty  lazy val headRelns: ju.List[Relation] = {
    val rs = sent.relns.filter(r => r.getGovIndex == headIndex)
    val byLabel: Map[String, Seq[Relation]] = rs.groupBy(_.getLabel)
    (byLabel.flatMap {
      case (label,rs) =>
        rs.sortWith((a,b) => math.abs(a.getDepIndex-headIndex) < math.abs(b.getDepIndex-headIndex)).
          take(HobbsGlobals.maxMods)
    }).toSeq
  }
  @BeanProperty  lazy val mentType = MentionType.fromNode(node)

  @BeanProperty val headDeps: ju.List[fig.basic.Pair[String,String]] = {
    for (r <- headRelns; if !Mention.removedDepRelns(r.getLabel.toLowerCase)) yield {
      val word = {
        val w = sent.words(r.getDepIndex)
        if (HobbsGlobals.lowercaseHead) w
        else w
      }
      fig.basic.Pair.makePair(r.getLabel,word)
    }
  }

  def getDeterminer: Option[String] = {
    headRelns.find(p => p.getLabel== "det").map(p => sent.words(p.getDepIndex))
  }
    
  override def toString = "Mention(%s)".format(words.mkString("",",",""))

  def isProper = mentType == MentionType.PROPER
  def isNominal = mentType == MentionType.NOMINAL
  def isPronoun = mentType == MentionType.PRONOUN


  @BeanProperty
  lazy val props: ju.List[fig.basic.Pair[MentProp,String]] = {
    val headProp = figPair.makePair(MentProp.getHeadProp(mentType),headWord)
    val modProps = headDeps.map(d => figPair.makePair(new MentProp(PropType.MOD,d.getFirst),d.getSecond))
    if (parentRelation != null && !Mention.removedParentRelns(parentRelation.getLabel)) {
      val parentProp = figPair.makePair(new MentProp(PropType.GOV,parentRelation.getLabel),parentAndWord.getSecond)
      Seq(headProp,parentProp) ++ modProps
    } else {
      Seq(headProp) ++ modProps
    }
  }

  @BeanProperty
  lazy val possibleAnts: ju.List[Antecedent] = Antecedent.getPossibleAnts(this)
    
  override def iterator = props.iterator
}