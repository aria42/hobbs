package coref

import hobbs.HobbsGlobals
import java.io.{Reader, InputStreamReader, File, FileInputStream}
import mentdetect.IMentionFinder
import scala.collection.JavaConversions._
import mochi.nlp.process.{Relations}
import java.lang.String
import edu.berkeley.nlp.syntax.{Tree, RichLabel, Trees}
import edu.berkeley.nlp.ling.{CollinsHeadFinder, HeadFinder}
import java.{util => ju}
import mochi.io.{IOUtils => IO}
import mochi.nlp.Relation
import edu.berkeley.nlp.util.Logger

object DocumentLoader {

  // Handle POS constructions, (NP (NNP Microsoft) (POS 's))
  val hf = new HeadFinder() {
    val collins = new CollinsHeadFinder
    def determineHead(t: Tree[String]): Tree[String] = {
      val numChildrens = t.getChildren.size
      if (numChildrens > 1 && t.getChildren.last.getLabel == "POS")
        t.getChildren.get(numChildrens-2)
      else collins.determineHead(t)
    }
  }

  var warnedRelnWriting = false
 
  def fromPrefix(treesPath: String, mf: IMentionFinder): Document = {
    val treeIn = new InputStreamReader(new FileInputStream(treesPath))
    val trees = (new Trees.PennTreeReader(treeIn)).
        toSeq.map(t => RichLabel.getRichTree(t,hf))
    val relns: ju.List[ju.List[Relation]] = {
        val relnFile = {
          val f = new java.io.File(HobbsGlobals.outputDir,(new java.io.File(treesPath)).getName)
          IO.changeExt(f.getAbsolutePath,HobbsGlobals.relnExt)
        }
        if (IO.exists(relnFile))
          mochi.io.IOUtils.lines(relnFile).map(line => Relations.relnFromLine.apply(line))
        else {
          if (!HobbsGlobals.writeRelns) {
            try {
              throw new RuntimeException(
                "Didn't find relations file: Must set writeRelns option to" +
                "to write relns file where trees file are")
            } catch {
              case e =>
                e.printStackTrace
                System.exit(0)
            }            
          }
          val res: ju.List[ju.List[Relation]] = trees.zipWithIndex.map { case (t,i) =>
            try {
              mochi.nlp.process.Relations.getRelations(t.getLabel.getOriginalNode)
           } catch {
             case _ =>
               Logger.logs("[Hobbs] Error processing relations from %sth tree in %s, extracting none for sentence".
                  format(i.toString,treesPath))
               new ju.ArrayList[Relation]()
           }
          }
          mochi.io.IOUtils.writeLines(relnFile,res.map(_.mkString(" ")))
          if (!warnedRelnWriting) {
           warnedRelnWriting = true
           edu.berkeley.nlp.util.Logger.logs("[Hobbs] Writing relations files; subsequent data passes much faster") 
          }
          res
        }
    }
    require(relns.size == trees.size)
    val sents: java.util.List[Sentence] =
      for {
        (tree,sentIndex) <- trees.zipWithIndex
        sentRelns = relns(sentIndex)
      } yield new Sentence(tree,sentRelns,sentIndex)
    val doc = new Document(new File(treesPath).getName, sents)
    sents.foreach(sent => sent.doc = doc)
    val ments = mf.getMentions(doc).sortWith { (a,b) =>
      val aNode = a.node.getLabel.getOriginalNode
      val bNode = b.node.getLabel.getOriginalNode
      (a.sentIndex < b.sentIndex) ||
      (doc.tpf.findLowestCommonAncestor(aNode,bNode) eq aNode) ||
      a.getHeadIndex < b.getHeadIndex
    }
    for (sent <- sents) {    
      sent.ments = ments.filter(_.sentIndex == sent.index)
    }    
    doc.setMentions(ments)
    return doc
  }

}