package coref.hobbs;

import coref.*;
import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.functional.Functional;
import edu.berkeley.nlp.util.functional.Predicate;
import fig.basic.Pair;

import java.io.PrintWriter;
import java.util.*;

public class Entity {


  /* Immutable State */
  private final Map<MentProp, List<String>> lists;
  private final MentionTypeSig mentTypeSig;

  /* Mutable Set Once */
  public List<Mention> ments;

  /* Ugly Mutable State - Changed a lot */
  public Counter<EntType> entTypeFactors = null;
  public Counter<Mention> proFactors = null;

  public Entity(Map<MentProp, List<String>> lists) {
    this.lists = lists;
    List<String> propHeads = lists.get(new MentProp(PropType.HEAD, MentionType.PROPER.toString()));
    List<String> nomHeads = lists.get(new MentProp(PropType.HEAD, MentionType.NOMINAL.toString()));
    mentTypeSig = new MentionTypeSig(
      (propHeads != null ? propHeads.size() : 0),
      (nomHeads != null ? nomHeads.size() : 0));
  }

  public List<EntType> getForcedTypes() {
    return Functional.filter(EntType.allTrue(), new Predicate<EntType>() {
      public Boolean apply(EntType input) {
        return input.protos.isMatch(Entity.this);
      }
    });
  }

  public MentionTypeSig getMentionTypeSig() {
    return mentTypeSig;
  }

  public List<String> getWordList(MentProp mp) {
    List<String> lst = lists.get(mp);
    if (lst == null) return Collections.emptyList();
    return lst;
  }

  public Map<MentProp,List<String>> getWordLists() {
    return lists;
  }

  public List<String> getHeadWords(MentionType mentType) {
    return getWordList(MentProp.getHeadProp(mentType));
  }

  public Set<String> getReferHeads() {
    Set<String> referHeads = new HashSet<String>();
    for (String w : getHeadWords(MentionType.PROPER)) {
      referHeads.add(w.toLowerCase());
    }
    for (String w : getHeadWords(MentionType.NOMINAL)) {
      referHeads.add(w.toLowerCase());
    }
    return referHeads;
  }

  public void output(PrintWriter out) {
    for (Map.Entry<MentProp, List<String>> entry : lists.entrySet()) {
      if (entry.getValue().isEmpty()) continue;
      out.print(entry.getKey().toString());
      out.print("\t");
      out.print(Functional.mkString(entry.getValue(),""," ",""));
      out.println();
    }
  }

  /* Static */

  public static Entity heuristicFromMentions(List<Mention> ments) {
    Map<MentProp, Set<String>> wordSets = new HashMap<MentProp, Set<String>>();
    for (Mention ment : ments) {
      for (Pair<MentProp, String> pair : ment.props()) {
        MentProp r = pair.getFirst();
        if (!HyperParams.isTokenProp(r)) {
          CollectionUtils.addToValueSet(wordSets,r,pair.getSecond());
        }
      }
    }
    Map<MentProp, List<String>> lists = new HashMap<MentProp, List<String>>();
    for (Map.Entry<MentProp, Set<String>> entry : wordSets.entrySet()) {
      lists.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
    }
    Entity ent = new Entity(lists);
    if (HobbsGlobals.useHeadAsMod) {
      List<String> modWords = ent.getWordList(new MentProp(PropType.MOD,"nn"));
      Set<String> referHeads = ent.getReferHeads();
      Set<String> toRemove = new HashSet<String>();
      for (String w: modWords) {
        if (referHeads.contains(w.toLowerCase())) {
          toRemove.add(w);
        }
      }
      modWords.removeAll(toRemove);
      if (modWords.isEmpty()) ent.lists.remove(new MentProp(PropType.MOD,"nn"));
    }

    ent.ments = ments;
    return ent;
  }


}
