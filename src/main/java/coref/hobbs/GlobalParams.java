package coref.hobbs;

import coref.MentProp;
import coref.Mention;
import coref.PropType;
import edu.berkeley.nlp.util.Logger;
import edu.berkeley.nlp.util.functional.Fn;
import edu.berkeley.nlp.util.functional.Functional;
import mochi.utils.IMergable;
import mochi.ml.probs.DirichletMultinomial;
import mochi.ml.probs.Distribution;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlobalParams implements IMergable<GlobalParams> {

  // EntType Params
  public Distribution<EntType> phi;
  public List<EntTypeParams> theta;
  public Map<EntType, EntTypeParams> thetaByType;

  // Discourse
  public IDiscourseModule proPi;
  public IDiscourseModule nomPi;

    
 
  public GlobalParams() {
    phi = DirichletMultinomial.make(HobbsGlobals.phiLambda, EntType.all().size());
    theta = Functional.map(EntType.all(), new Fn<EntType, EntTypeParams>() {
      public EntTypeParams apply(EntType input) {
        EntTypeParams typeParams = new EntTypeParams(input);
        typeParams.init();
        return typeParams;
      }});
    thetaByType = new HashMap<EntType, EntTypeParams>();
    for (EntTypeParams typeParams : theta) {
      thetaByType.put(typeParams.type, typeParams);
    }
    proPi =   new LogLinearDiscourseModule();
    nomPi =   new LogLinearDiscourseModule();


  }

  public void inspect() {
    Logger.startTrack("GlobalParams");
    Logger.logs("type prior: " + phi.toString());
    for (EntType type : EntType.allTrue()) {
      inspectType(type);
    }
    for (EntType type : EntType.allJunk()) {
      inspectType(type);
    }
    Logger.endTrack();
  }

  private void inspectType(EntType type) {
    EntTypeParams typeParams = thetaByType.get(type);
    assert typeParams != null : "No params for type " + type;
    assert typeParams.vocabDistrs != null : "No vocab distrs for type " + type;
    Logger.startTrack("TypeParams: " + type.name);
    Logger.logs("mentTypeSig -> " + typeParams.mentTypeSigDistr);
    for (MentProp r : MentProp.byType(PropType.HEAD)) {
      Distribution<String> vocabDistr = typeParams.vocabDistrs.get(r);
      assert vocabDistr != null : "No vocab for " + r;
      Logger.logss(r + " -> " + vocabDistr);
    }
    for (MentProp r: Arrays.asList(new MentProp(PropType.MOD,"nn"),
                                   new MentProp(PropType.GOV,"nsubj"),
                                   new MentProp(PropType.MOD,"appos"))) {
      Logger.logss(r + " -> " + typeParams.vocabDistrs.get(r));
    }
    Logger.endTrack();
  }

  public void maximize() {
    /* No-Op */
    Logger.startTrack("Pro Discourse Params Optimize on  " + HobbsGlobals.numThreads + " threads ");
    proPi.makeDistribution();
    Logger.endTrack();
    Logger.startTrack("Nom Discourse Params Optimize on  " + HobbsGlobals.numThreads + " threads ");
    nomPi.makeDistribution();
    Logger.endTrack();
  }

  public double[] getAntProbs(Mention ment) {
    switch (ment.mentType()) {
      case PROPER: throw new RuntimeException("Bad!");
      case NOMINAL: return nomPi.getAntProbs(ment);
      case PRONOUN: return proPi.getAntProbs(ment);
      default: throw new RuntimeException("Bad!");
    }
  }

  public static void init() {
    cur = new GlobalParams();
  }

  public void merge(GlobalParams other) {
    
  }

  // Current Global Params
  public static GlobalParams cur = null;


}
