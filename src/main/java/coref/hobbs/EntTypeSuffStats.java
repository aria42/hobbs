package coref.hobbs;

import coref.*;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.CounterMap;
import edu.berkeley.nlp.util.Logger;
import fig.basic.Pair;
import mochi.ml.probs.Distribution;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class EntTypeSuffStats {

  EntType type;
  int typeIndex;
  CounterMap<MentProp, String> vocabCounts;
  CounterMap<MentProp, Integer> fertCounts;
  Counter<MentionTypeSig> mentTypeSigCounts;

  public EntTypeSuffStats(EntType type) {
    this.type = type;
    this.typeIndex = EntType.all().indexOf(type);
    vocabCounts = new CounterMap<MentProp, String>();
    mentTypeSigCounts = new Counter<MentionTypeSig>();
    fertCounts = new CounterMap<MentProp, Integer>();
  }

  public void observeEnt(Entity ent, double weight) {
    mentTypeSigCounts.incrementCount(ent.getMentionTypeSig(), weight);
    for (Map.Entry<MentProp,List<String>> entry: ent.getWordLists().entrySet()) {
      MentProp r = entry.getKey();
      List<String> lst = entry.getValue();
      if (r.kind == PropType.MOD) {
        fertCounts.incrementCount(r, lst.size(), weight);
      }
      for (String w : lst) {
        vocabCounts.incrementCount(r,w,weight);  
      }
    }
  }

  public void observeMent(Mention ment, Entity ent, double weight) {
    //type.name.startsWith("PER-Individual")
    if (ment.isPronoun() && ment.headWord().equalsIgnoreCase("he")) {
      if (!type.name.equals("PER-Individual") && weight > 1.0e-2) {
        boolean breakHere = true;
      }
    }
    for (Pair<MentProp, String> pair : ment.props()) {
      MentProp r = pair.getFirst();
      String w = pair.getSecond();
      List<String> L = ent.getWordList(r);
      double entProb =
          L.contains(w) ?
         1.0 / L.size() :
         0.0;
      double typeProb =
          (EntTypeParams.PRO_PROP.get().equals(r)) ?
            Math.exp(GlobalParams.cur.theta.get(typeIndex).getPronounHeadLogProb(ment)) :
            GlobalParams.cur.theta.get(typeIndex).vocabDistrs.get(r).getProb(w);                         
      double alpha = HyperParams.alphas.getCount(r);
      double prob = (1-alpha) * entProb + alpha * typeProb;
      assert prob > 0.0 ;
      double typePost = (alpha * typeProb) / prob;
      assert typePost >= 0.0 && typePost <= 1.0;
      if (r.equals(MentProp.getHeadProp(MentionType.PRONOUN))) {
        w = w.toLowerCase();    
      }
      vocabCounts.incrementCount(r,w,typePost*weight);
    }

  }

  public void updateParams(EntTypeParams typeParams) {
    for (Map.Entry<MentionTypeSig, Double> entry : mentTypeSigCounts.entrySet()) {
      typeParams.mentTypeSigDistr.observe(entry.getKey(),entry.getValue());
    }
    for (Map.Entry<MentProp, Counter<Integer>> entry : fertCounts.entrySet()) {
      MentProp r = entry.getKey();
      Counter<Integer> innerFertCounts = entry.getValue();
      Distribution<Integer> fertDistr = typeParams.fertilityDistrs.get(r);
      for (Map.Entry<Integer, Double> innerEntry : innerFertCounts.entrySet()) {
        fertDistr.observe(innerEntry.getKey(), innerEntry.getValue());
      }
    }
    for (Map.Entry<MentProp, Counter<String>> entry : vocabCounts.entrySet()) {
      MentProp r = entry.getKey();
      Counter<String> innerVocabCounts = entry.getValue();
      Distribution<String> vocabDistr = typeParams.vocabDistrs.get(r);
      for (Map.Entry<String, Double> innerEntry : innerVocabCounts.entrySet()) {
        vocabDistr.observe(innerEntry.getKey(),innerEntry.getValue());
      }
    }
  }
}
