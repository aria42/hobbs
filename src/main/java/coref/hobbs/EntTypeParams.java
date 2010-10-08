package coref.hobbs;

import coref.*;
import edu.berkeley.nlp.concurrent.Lazy;
import fig.basic.Pair;
import mochi.ml.probs.DirichletMultinomial;
import mochi.ml.probs.Distribution;
import edu.berkeley.nlp.util.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class represents the parameters
 * associated with a given (Entity) Type
 *
 * The basic methods of these parameters
 * are to score a collection of entity word lists
 * - getEntLogProb
 * And to score a mention given an entity
 * - getMentLogProb 
 */
public class EntTypeParams {
  public EntType type;
  public Distribution<MentionTypeSig> mentTypeSigDistr;
  public Map<MentProp, Distribution<String>> vocabDistrs;
  private boolean inited = false;

  public Map<MentProp, Distribution<Integer>> fertilityDistrs;

  public EntTypeParams(EntType type) {
    this.type = type;  
  }

  public void init() {
    if (inited) return;
    inited = true;

    mentTypeSigDistr = initMentTypeSignature();
    // Vocab and Fertility
    vocabDistrs = new HashMap<MentProp, Distribution<String>>();
    fertilityDistrs = new HashMap<MentProp, Distribution<Integer>>();
    // Head Props
    for (MentProp r : MentProp.byType(PropType.HEAD)) {
      DirichletMultinomial<String> distr = new DirichletMultinomial<String>();
      MentionType mentType = MentionType.valueOf(r.name);
      assert mentType != null;
      if (mentType != MentionType.PRONOUN) {
        Set<String> heads = MentProp.getVocab(r);
        distr.setLambda(HobbsGlobals.nonProHeadLambda);
        distr.setNumKeys(heads.size());
      } else {
        Set<String> protoPros = type.isModeled ? type.protos.protos.get(MentionType.PRONOUN) :null;                        
        Set<String> allPros = MentProp.getVocab(new MentProp(PropType.HEAD, MentionType.PRONOUN.toString()));
        distr.setLambda(HobbsGlobals.proHeadLambda);
        distr.setNumKeys(protoPros != null ? protoPros.size() : allPros.size());
      }
      vocabDistrs.put(r, distr);
    }
    for (MentProp r : MentProp.allNonHead()) {
      Set<String> propVocab = MentProp.getVocab(r);
      assert propVocab != null && !propVocab.isEmpty();
      vocabDistrs.put(r, DirichletMultinomial.<String>make(HobbsGlobals.modLambda, propVocab.size()));
      if (r.kind == PropType.MOD) {
        fertilityDistrs.put(r, DirichletMultinomial.<Integer>make(HobbsGlobals.fertilityLambda, HobbsGlobals.maxListSize));
      }
    }
  }

  public double getEntLogProb(Entity ent)
  {
    double logProb = 0.0;
    if (!Vocab.global.mentTypeSigs.contains(ent.getMentionTypeSig())) return -500;
    logProb += mentTypeSigDistr.getLogProb(ent.getMentionTypeSig());
    for (Map.Entry<MentProp,List<String>> entry: ent.getWordLists().entrySet()) {
      MentProp r = entry.getKey();
      List<String> lst = entry.getValue();
      if (r.kind != PropType.HEAD) {
        assert lst.size() > 0;
        logProb += fertilityDistrs.get(r).getLogProb(lst.size());
        //Distribution<Integer> fertDistr = fertilityDistrs.get(r);
        //if (fertDistr != null) logProb += fertDistr.getLogProb(lst.size());
      }
      for (String w : lst) {
       logProb += vocabDistrs.get(r).getLogProb(w);
      }
    }
    return logProb;
  }

  public static final Lazy<MentProp> PRO_PROP = new Lazy<MentProp>() {
       @Override
       public MentProp get() {
        return MentProp.getHeadProp(MentionType.PRONOUN);
       }
    } ;

  public double getPronounHeadLogProb(Mention ment) {
    if (type.isModeled &&
        (type.protos.protos.get(MentionType.PRONOUN) == null ||
         !type.protos.protos.get(MentionType.PRONOUN).contains(ment.getHeadWord().toLowerCase()))) {
      // Negative Infinity
      return -10;
    }
    return vocabDistrs.get(PRO_PROP.get()).getLogProb(ment.getHeadWord().toLowerCase());
  }
  
  private static double interpolate(double normalProb, double backoffProb, double backoffAlpha) {
    return normalProb * (1-backoffAlpha)  + backoffProb * backoffAlpha;
  }

  public double getMentLogProb(Mention ment, Entity ewl)
  {
    double logProb = 0.0;    
    for (Pair<MentProp, String> pair : ment.props()) {
      MentProp r = pair.getFirst();
      if (r.equals(PRO_PROP.get())) {
        logProb += getPronounHeadLogProb(ment);
        continue;
      }
      String w = pair.getSecond();
      assert HyperParams.alphas.containsKey(r);
      double alpha = HyperParams.alphas.getCount(r);
      double entProb = 0.0;
      if (alpha < 1.0) {
        List<String> L = ewl.getWordList(r);
        entProb = L.isEmpty() ? 0.0 : 1.0 / L.size();
      }
      if (HobbsGlobals.useHeadAsMod && r.equals(new MentProp(PropType.MOD,"nn"))) {
        Set<String> referHeads = ewl.getReferHeads();
        double modProb = referHeads.contains(w.toLowerCase()) ? 1.0/referHeads.size() : 0.0;
        entProb = interpolate(entProb, modProb, HobbsGlobals.headAsModAlpha);
      }
      if (HobbsGlobals.useApposAsNomHead && r.equals(new MentProp(PropType.HEAD,"nom"))) {
        double apposProb = vocabDistrs.get(new MentProp(PropType.MOD,"appos")).getProb(w);
        double beforeEntProb = entProb;
        entProb = interpolate(entProb, apposProb, HobbsGlobals.apposAsNomHeadAlpha);        
        if (entProb > beforeEntProb) {
          Logger.logs("beforeEntProb: %.3f entProb: %.3f",beforeEntProb,entProb);                
        }
      }
      assert entProb >= 0.0 && entProb <= 1.0;      
      double typeProb = vocabDistrs.get(r).getProb(w); 
      assert typeProb > 0.0;      
      double prob = (1-alpha) * entProb + alpha * typeProb;
      assert prob > 0.0 && prob <= 1.0;
      logProb += Math.log(prob);      
    }
    return logProb;
  }


  /* Static */

  private static Distribution<MentionTypeSig> initMentTypeSignature() {
    return DirichletMultinomial.make(HobbsGlobals.mtsLambda, Vocab.global.mentTypeSigs.size());
  }

}
