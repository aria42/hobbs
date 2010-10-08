package coref;

import coref.hobbs.Entity;
import edu.berkeley.nlp.syntax.RichLabel;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.TreePathFinder;
import edu.berkeley.nlp.util.functional.Fn;
import edu.berkeley.nlp.util.functional.Functional;
import mochi.nlp.Relation;
import mochi.nlp.Trees;

import java.io.File;
import java.util.*;

public class Document {

  /* CTOR Given */
  public final List<Sentence> sents;
  public final String id;

  /* Immutable Derived */
  private final Tree<String> docTree;
  public final TreePathFinder<String> tpf;
  public final List<Tree<RichLabel>> trees;
  public final List<List<Relation>> relns;


  /* External But not changed once set */
  private List<Mention> ments = null;
  private List<Mention> referMents = null;
  private List<Mention> pros = null;
  private Map<Tree<RichLabel>, Mention> node2Ment;
  public List<Entity> ents;

  /* CTOR */
  public Document(String id, List<Sentence> sents) {
    this.id = id;
    this.sents = Collections.unmodifiableList(sents);
    this.trees = Functional.map(sents, new Fn<Sentence, Tree<RichLabel>>() {
      @Override
      public Tree<RichLabel> apply(Sentence sentence) {
        return sentence.tree();
      }
    });
    this.relns = Functional.map(sents, new Fn<Sentence, List<Relation>>() {
      @Override
      public List<Relation> apply(Sentence sentence) {
        return sentence.relns();
      }
    });
    this.docTree = Trees.foldTreesLeft(Functional.map(trees, new Fn<Tree<RichLabel>, Tree<String>>() {
      public Tree<String> apply(Tree<RichLabel> input) {
        return input.getLabel().getOriginalNode();
      }
    }), "DOC");
    this.tpf = new TreePathFinder<String>(docTree);
  }


  public List<Tree<RichLabel>> getTrees() {
    return trees;
  }

  public List<List<Relation>> getRelns() {
    return relns;
  }

  public List<Mention> getMentions() {
    return ments;
  }

  public List<Mention> getReferMentions() {
    return referMents;
  }

  public List<Mention> getProMentions() {
    return pros;
  }

  public void setMentions(List<Mention> ments) {
    this.pros = new ArrayList<Mention>();
    this.referMents = new ArrayList<Mention>();
    this.ments = mochi.utils.Collections.toList(ments);
    int referIndex = 0;
    for (int i=0; i < ments.size(); ++i) {
      ments.get(i).setIndex(i);
      if (!ments.get(i).isPronoun()) {
        ments.get(i).setReferIndex(referIndex);
        referMents.add(ments.get(i));
        referIndex++;
      } else {
        pros.add(ments.get(i));
      }
    }
    referMents = java.util.Collections.unmodifiableList(referMents);
    pros = java.util.Collections.unmodifiableList(pros);
    this.node2Ment = new IdentityHashMap<Tree<RichLabel>,Mention>();
    for (Mention ment : ments) {
      node2Ment.put(ment.getNode(), ment);
    }
  }


  public Map<Tree<RichLabel>, Mention> getNodeToMention() {
    return java.util.Collections.unmodifiableMap(node2Ment);
  }

  public int getTreeDistance(Mention ment, Mention ant) {
    return tpf.getDistance(ment.node().getLabel().getOriginalNode(),
                           ant.node().getLabel().getOriginalNode());
  }
  
}
