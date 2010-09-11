package coref;

import edu.berkeley.nlp.syntax.RichLabel;
import edu.berkeley.nlp.syntax.Tree;
import mochi.utils.Collections;

import java.io.Serializable;

public enum MentionType implements Serializable {
  PROPER, NOMINAL, PRONOUN, PRENOMINAL;

  public static final long serialVersionUID = 42L;

  public static MentionType fromNode(Tree<RichLabel> node) {
    if (Collections.set("there","one","that","this").contains(node.getLabel().getHeadWord().toLowerCase())) {
      return PRONOUN; 
    }
    String tag = node.getLabel().getHeadTag();    
    if (tag.startsWith("NNP")) return PROPER;
    if (tag.startsWith("PRP") || tag.startsWith("W")) return PRONOUN;
    return tag.startsWith("JJ") ? PRENOMINAL : NOMINAL;
    //return node.isPreTerminal() ? PRENOMINAL : NOMINAL;
  }

  public static MentionType fromShortName(String name) {
    if (name.equals("NAM")) return PROPER;
    if (name.equals("NOM")) return NOMINAL;
    if (name.equals("PRE")) return PRENOMINAL;
    if (name.equals("PRO")) return PRONOUN;
    throw new RuntimeException("Don't know about mention type: " + name);
  }
//  public static MentionType fromPOS(String tag) {
//    if (tag.startsWith("NNP")) return PROPER;
//    if (tag.startsWith("PRP") || tag.startsWith("W")) return PRONOUN;
//    return NOMINAL;
//  }
}
