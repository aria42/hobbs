package coref

import java.{util => ju}
import scala.collection.JavaConversions._
import mochi.utils.IIndexed
import reflect.BeanProperty
import edu.berkeley.nlp.syntax.{Tree, Trees, RichLabel}
import mochi.nlp.Relation

class Sentence(@BeanProperty val tree: Tree[RichLabel],
               @BeanProperty val relns: ju.List[Relation],
               @BeanProperty val index: Int) extends IIndexed
{
  // Immutable CTOR Derived

  @BeanProperty
  val words: ju.List[String] = tree.getYield.map(_.getHeadWord)
  @BeanProperty
  val parentMap: ju.Map[Tree[RichLabel],Tree[RichLabel]] =
    mochi.nlp.Trees.getParentMap(tree)
  @BeanProperty
  val depTree: Tree[java.lang.Integer] = mochi.nlp.Trees.getDepTree(tree)
  @BeanProperty
  val headIndexToMentMap: ju.Map[Int,Mention] = new ju.HashMap[Int,Mention]()

  /* Externally Set */
  var doc: Document = null
  var ments: ju.List[Mention] = null

  def getMentions: ju.List[Mention] = ju.Collections.unmodifiableList(ments)

}