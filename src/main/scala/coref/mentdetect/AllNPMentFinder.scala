package coref.mentdetect

import java.{util =>  ju}
import scala.collection.JavaConversions._
import edu.berkeley.nlp.syntax.{Tree, RichLabel}
import coref.{MentionType, Mention, Document}

class AllNPMentFinder extends IMentionFinder {

  val disallowedHeads = Set("CD","POS")

  def validNode(node: Tree[RichLabel]): Boolean = {
    val l = node.getLabel.getLabel
    val h = node.getLabel.getHeadTag
    (h != null &&
     (l.startsWith("NP") || h.startsWith("PRP") || h.startsWith("W")) &&
     !disallowedHeads(h))    
  }

  def getNPsByHead(tree: Tree[RichLabel]): Map[Int, Seq[Tree[RichLabel]]] = {
    tree.getPostOrderTraversal.
         filter(validNode).
         groupBy(node => node.getLabel.getHeadIndex)
  }

  def getMentions(doc: Document): ju.List[Mention] = {
    for {
      (sent,sentIndex) <- doc.sents.zipWithIndex
      (headIndex,nps) <- getNPsByHead(sent.tree)
      maxNP = nps.max(Ordering.fromLessThan((a: Tree[RichLabel],b: Tree[RichLabel]) => a.getDepth < b.getDepth))
      ment = new Mention(sent,maxNP)
      if ment.mentType != MentionType.PRENOMINAL
    } yield ment 
  }
}