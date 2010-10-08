package coref

import edu.berkeley.nlp.syntax.{Tree, RichLabel}
import java.{util => ju}
import scala.collection.JavaConversions._

object SyntacticConfigurations {
  def hasComplement(node: Tree[String]) = {
    val children = node.getChildren.map(_.getLabel).toList
    if (children.size < 2) false
    else (children(0) == "NP") && List("SBAR", "CONJP", ",").contains(children(1))
  }

  def isConjunction(node: Tree[String]) = {
    val children = node.getChildren.map(_.getLabel).toList
    children == List("NP", "CC", "NP")
  }

  /**
   * Identify the Governing Appositive
   */
  def identifyAppositive(ment: Mention): Option[Mention] = {
    if (ment.isPronoun) return None
    //if (ment.mentIndex == 0) return None
    val headWord = ment.getHeadWord.toLowerCase
    val mentParent = ment.sent.getParentMap.get(ment.getNode)
    val ant = ment.doc.getNodeToMention.get(mentParent)
    if (ant == null) return None
    //if (ment.mentIndex < ant.mentIndex) return None
    val children: Seq[Tree[RichLabel]] = mentParent.getChildren
    // Role Appositive
    if (children.size == 2 &&
            children(0).getLabel.getLabel.startsWith("NP") &&
            children(1).getLabel.getLabel.startsWith("NP")) {
      return Some(ant)
    }
    // NP, NP Appositive
    if (children.size > 4 || children.size < 3) return None
    val firstChild = children(0);
    val secondChild = children(1);
    val thirdChild = children(2);
    if (!firstChild.getLabel.getLabel.startsWith("NP") ||
            !secondChild.getLabel.getLabel.startsWith(",") ||
            !thirdChild.getLabel.getLabel.startsWith("NP")) {
      return None;
    }
    val childMent = ment.doc.getNodeToMention.get(thirdChild);
    if (childMent == ment) {
      //      if (ment.mentInfo.ner.isDefined  && ant.mentInfo.ner.isDefined &&
      //          ment.mentInfo.ner == ant.mentInfo.ner) return None;
      return Some(ant);
    }
    return None
  }

  val isForms = Set("is", "are", "was", "'m")

  def isAppositiveNode(t: Tree[String]): Boolean = {
    if (!t.getLabel.startsWith("NP")) return false
    t.getChildren.map(_.getLabel).toList == List("NP", ",", "NP", ",")
  }

  def getAppositiveAntecedent(m: Mention): Mention = {
    val parentMent = m.getParentMention
    if (parentMent != null) {
      val parentNode = parentMent.node
      if (((parentMent.isProper && m.isNominal) ||
          (parentMent.isNominal && m.isProper)) &&
          //CHANGE
          m.parentRelation != null && m.parentRelation.getLabel == "appos"
          //isAppositiveNode(parentNode.getLabel.getOriginalNode)
          ) {
        return parentMent
      }
    }
    return null
  }

  val whTags = Set("WDT","WP")

  def getWhConstrained(m: Mention): Mention = {
    if (!m.isPronoun || !whTags(m.headPOS)) return null
    val parentMent = m.parentMention
    if (parentMent != null && !parentMent.isPronoun) parentMent
    else null
  }

  def getForced(m: Mention): Mention = {
    var forced: Mention = getAppositiveAntecedent(m)
    if (forced != null) return forced
    forced = getWhConstrained(m)
    if (forced != null) return forced
    predNom(m) match {
      case Some(ant) =>
        val stopHere = true
        ant
      case _ => null
    }
  }

  def predNom(ment: Mention): Option[Mention] = {
     if (ment.isPronoun) return None
     val vpAndHeadWordOpt = getGoverningVP(ment)
     if (vpAndHeadWordOpt.isDefined) {
       val (vp, headWord) = vpAndHeadWordOpt.get
       if (!isForms.contains(headWord)) return None;
       val sent = ment.doc.tpf.parentMap.get(vp);
       val child = ment.doc.getNodeToMention.get(sent.getChildren().get(0))
       //CHANGE add child.isPronoun part
       if (child == null) None
       else Some(child)
     } else None
  }


  // /**
  //  * Find Governing PredNom Subject
  //  */
  //
  def getGoverningVP(ment: Mention): Option[(Tree[String],String)] = {
     val vp = ment.getParentNode
     if (vp.getLabel.getLabel == "DOC") None
     else {
       val vpHeadWord = vp.getLabel().getHeadWord().toLowerCase();
       Some((vp.getLabel.getOriginalNode, vpHeadWord))
     }
  }

//  def isSubject(tpf: TreePathFinder[String], vp: Tree[String], np: Tree[String]): Boolean = {
//     import TreePathFinder.{Direction,VerticalDirection,ChildPosition}
//     val path = tpf.getTreePath(np, vp)
//     path.head.dir match {
//       case Direction(VerticalDirection.UP,ChildPosition.LEFT) => true
//       case _ => false
//     }
//  }

  // def getForced(ment: Mention): Option[Mention] = {
  //   val apposOpt = identifyAppositive(ment)
  //   //CHANGE made sure ant was modeled
  //   if (apposOpt.isDefined) return apposOpt
  //   val predNomOpt = predNom(ment)
  //   if (predNomOpt.isDefined) return predNomOpt
  //   return None
  // }
}