package coref.output


import coref.{Mention, Document}
import scala.{collection => coll}
import scala.collection.JavaConversions._
import scala.collection.{mutable => mut, immutable => immut}
import edu.berkeley.nlp.syntax.Trees.TreeTransformer
import java.lang.String
import coref.{CorefChain, CorefCluster}
import edu.berkeley.nlp.syntax.{Trees, Tree}

object CorefTreeOutput {

  def outputDocument(outputPath: String, doc: Document, corefCluster: CorefCluster) {
    val sortedChains: mut.Buffer[CorefChain] = corefCluster.getChains.sortBy {
      chain: CorefChain =>
        chain.ments.map { m: Mention =>
          val sentIndex = m.sent.getIndex
          val tokIndex = m.getHeadIndex
          (sentIndex,tokIndex)
        }.min
    }
    val ment2ChainId = new java.util.IdentityHashMap[Tree[String],String]()
    for ((chain,index) <- sortedChains.zipWithIndex; ment <- chain.getMents) {
      ment2ChainId.put(ment.node.getLabel.getOriginalNode,
                       "E%d".format(index))
    }
    val transformer = new TreeTransformer[String] {
      def transformTree(node: Tree[String]) = {
        val entLabel = ment2ChainId.get(node)
        val newLabel =
          if (entLabel != null) node.getLabel + "-" + entLabel
          else node.getLabel
        new Tree[String](newLabel, node.getChildren.map(transformTree(_)))
      }
    }
    val outTrees: mut.Buffer[Tree[String]] = doc.getTrees.map(t => transformer.transformTree(t.getLabel.getOriginalNode))
    //println("Writing to " + outputPath)
    mochi.io.IOUtils.writeLines(outputPath, outTrees.map(t => Trees.PennTreeRenderer.render(t)))
  }

}