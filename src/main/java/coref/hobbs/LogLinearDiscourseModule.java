package coref.hobbs;

import coref.*;
import edu.berkeley.nlp.classify.Feature;
import edu.berkeley.nlp.classify.FeatureManager;
import edu.berkeley.nlp.concurrent.WorkQueue;
import edu.berkeley.nlp.math.*;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.*;
import edu.berkeley.nlp.util.functional.Fn;
import edu.berkeley.nlp.util.functional.Functional;
import fig.basic.Pair;
import mochi.io.IOUtils;
import mochi.nlp.Relation;

import java.io.File;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

public class LogLinearDiscourseModule implements IDiscourseModule {

  double[] weights = null;

  List<DiscourseSuffStats> instances = new ArrayList<DiscourseSuffStats>();

  public void observe(DiscourseSuffStats suffStats) {
    instances.add(suffStats);
  }

  private static String getCFG(Tree<String> n) {
    if (n.isPreTerminal()) return n.getLabel();
    return
    Functional.mkString(n.getChildren(),n.getLabel() + " -> ["," ","]",new Fn<Tree<String>, String>() {
      @Override
      public String apply(Tree<String> input) { return input.getLabel(); }});
  }

  public static List<FVPair> extractFeatures(Antecedent ant) {
    List<FVPair> res = new ArrayList<FVPair>();
    if (ant.ment().isProper()) {
      return res;
    }
    MentionType mentType = ant.ment().mentType();
    if (ant.ant() == null) {
      res.add(new FVPair(featManager.getFeature("new-" + mentType + "-bias"), 1.0));
      String mentParentReln = ant.ment().parentAndWord().getFirst();
      res.add(new FVPair(featManager.getFeature("new-" + mentType + "-syn-pos=" + mentParentReln), 1.0));
      // CFG Feature
      if (ant.ment().isNominal()) {
        String cfg = getCFG(ant.ment().getNode().getLabel().getOriginalNode());
        res.add(new FVPair(featManager.getFeature("new-" + mentType + "-CFG=" + cfg),1.0));
        for (Relation r: ant.ment().headRelns()) {
          if (r.getLabel().startsWith("det")) {
            String det = ant.ment().sent().words().get(r.getDepIndex()).toLowerCase();
            res.add(new FVPair(featManager.getFeature("new-" + mentType + "-det=" + det),1.0));
          }
        }
      }
    } else {
      MentionType antType = ant.ant().mentType();
      // distance features
      res.add(new FVPair(featManager.getFeature("ant-tree-dist-" + mentType + "-" + antType), (double) ant.treeDist()));
      res.add(new FVPair(featManager.getFeature("ant-sent-dist-" + mentType + "-" + antType + "=" + ant.sentDist()), 1.0));
      //res.add(new FVPair(featManager.getFeature("ant-ment-dist-" + mentType + "-" + antType),ant.mentDist()));
      // syntactic position
        String mentParentReln = ant.ment().parentAndWord().getFirst();
        String antParentReln = ant.ant().parentAndWord().getFirst();
        res.add(new FVPair(featManager.getFeature(String.format(
          "ant-syn-pos-%s-%s->%s-%s", mentType, mentParentReln, antType, antParentReln)), 1.0));
    }
    return res;
  }

  private void ensureInitWeights() {
    if (weights != null) return;
    weights = new double[featManager.getNumFeatures()];
    // Heuristic
    for (Feature f : featManager.getFeatures()) {
//      if (f.getObj().toString().startsWith("ant-ment-dist-PRONOUN")) {
//        weights[f.getIndex()] = -0.2;
//      }
      if (f.getObj().toString().startsWith("ant-tree-dist-PRONOUN")) {
        weights[f.getIndex()] = -0.2;
      }
      if (f.getObj().toString().startsWith("ant-tree-dist-NOM")) {
        weights[f.getIndex()] = -0.01;
      }
      if (f.getObj().toString().startsWith("new-PRONOUN-bias")) {
        weights[f.getIndex()] = -1000;
      }
    }
  }

  private double score(List<FVPair> fv) {
    ensureInitWeights();
    double s = 0.0;
    for (FVPair fvPair : fv) {              
      s += fvPair.v * weights[fvPair.f.getIndex()];
    }
    return s;
  }

  private double[] getAntLogScores(List<List<FVPair>> fvs) {
    double[] logScores = new double[fvs.size()];
    for (int i = 0; i < fvs.size(); ++i) {
      logScores[i] = score(fvs.get(i));
    }
    return logScores;
  }

  public double[] getAntProbs(List<List<FVPair>> fvs) {
    return SloppyMath.logScoresToProbs(getAntLogScores(fvs));
  }

  public double[] getAntProbs(Mention ment) {
    return getAntProbs(Functional.map(ment.possibleAnts(), new Fn<Antecedent, List<FVPair>>() {
      public List<FVPair> apply(Antecedent input) {
        return input.featVec();
      }}));
//
//      Functional.map(ment.possibleAnts, new Fn<AntecedentChoice, List<FVPair>>() {
//      public List<FVPair> apply(AntecedentChoice input) {
//        return extractFeatures(input);
//      }
//    }));
  }

  public void makeDistribution() {
    //if (HobbsGlobals.isIgnorePronouns()) return;
    if (instances.isEmpty()) return;

    class ObjFn extends CachingDifferentiableFunction {
      protected Pair<Double, double[]> calculate(double[] x) {
        class Worker implements Runnable {
          List<DiscourseSuffStats> data;
          double logProb = 0.0;
          double[] grad = new double[dimension()];

          Worker(List<DiscourseSuffStats> data) {
            this.data = data;
          }

          public void run() {
            for (DiscourseSuffStats instance : data) {
              if (instance.weights().length == 1) continue;
              double[] probs = getAntProbs(instance.fvs());
              for (int i = 0; i < probs.length; i++) {
                List<FVPair> fv = instance.fvs().get(i);
                double prob = probs[i];
                double weight = instance.weights()[i];
                logProb += weight * Math.log(prob);
                for (FVPair pair : fv) {
                  Feature f = pair.f;
                  double v = pair.v;
                  grad[f.getIndex()] += (weight - prob) * v;
                }
              }
            }
          }
        }
        weights = DoubleArrays.clone(x);
        List<List<DiscourseSuffStats>> splits = CollectionUtils.split(instances, HobbsGlobals.numThreads);
        List<Worker> workers = Functional.map(splits, new Fn<List<DiscourseSuffStats>, Worker>() {
          public Worker apply(List<DiscourseSuffStats> input) {
            return new Worker(input) ;
          }});
        WorkQueue.invokeAll(workers, true);
        double logProb = 0.0;
        double[] grad = new double[dimension()];
        for (Worker worker : workers) {
          logProb += worker.logProb;
          DoubleArrays.addInPlace(grad, worker.grad);
        }
        logProb *= -1;
        // very light regularization - sigmaSq not sensitive
        // logProb += (new L2Regularizer(100.0)).update(weights,grad,1.0);
        DoubleArrays.scale(grad, -1);        
        return Pair.newPair(logProb, grad);
      }

      public int dimension() {
        return featManager.getNumFeatures();
      }
    }
    double[] initWeights = new double[featManager.getNumFeatures()];
    weights = getMinimizer().minimize(new ObjFn(), initWeights, 1.0e-4, false);
    instances = null;
    for (Feature f : featManager.getFeatures()) {
      if (f.getObj().toString().startsWith("new-PRONOUN-bias")) {
        weights[f.getIndex()] = -20;
      }
    }
    Counter<Feature> weightsCounter = Counters.toCounter(weights, featManager.getFeatures());
    weightsCounter.keepTopNKeysByAbsValue(40);
    Logger.logs("weights: " + weightsCounter.toString());    
    System.gc();    
  }

  private LBFGSMinimizer getMinimizer() {
    LBFGSMinimizer lbfgsMinimizer = new LBFGSMinimizer();
    lbfgsMinimizer.setMaxIterations(100);
    lbfgsMinimizer.setMinIterations(0);
    lbfgsMinimizer.setMaxHistoryResets(0);
    lbfgsMinimizer.setVerbose(false);
    lbfgsMinimizer.setIterationCallbackFunction(new CallbackFunction() {
      public void callback(Object... args) {
        //(double[] currentGuess, int iterDone, double value, double[] derivative)
//        double[] x = (double[]) args[0];
//        int iterDone = (Integer) args[1];
//        double negLogProb = (Double) args[2];
//        double[] grad = (double[]) args[3];
//        Logger.startTrack("Discourse Optimize iter " + (iterDone + 1));
//        Counter<Feature> weightsCounter = Counters.toCounter(x, featManager.getFeatures());
//        weightsCounter.keepTopNKeysByAbsValue(40);
//        Logger.logs("weights: " + weightsCounter.toString());
//        Logger.endTrack();
      }
    });
    return lbfgsMinimizer;
  }

  /* Static Stuff */


  static FeatureManager featManager ;

  private static void indexFeatures(List<DocumentDesc> data) {
    class Worker implements Runnable {
      List<DocumentDesc> docs;

      Worker(List<DocumentDesc> docs) {
        this.docs = docs;
      }

      public void run() {
        for (DocumentDesc desc : docs) {
          Document doc = desc.getDocument();
          if (doc == null) continue;
          for (Mention ment: doc.getMentions()) {
            if (ment.isProper()) continue;
            for (Antecedent ant : ment.possibleAnts()) {
              // side-effect of indexing
              ant.featVec();
              //extractFeatures(ant);
            }
            // side-effect of indexing
//            (new AntecedentChoice(mentDatum, null)).
//            extractFeatures();
          }
        }
      }
    }    
    List<List<DocumentDesc>> splits = CollectionUtils.split(data, HobbsGlobals.numThreads);
    List<Worker> workers = Functional.map(splits, new Fn<List<DocumentDesc>, Worker>() {
      public Worker apply(List<DocumentDesc> input) {
        return new Worker(input);
      }
    });
    WorkQueue.invokeAll(workers, true);
  }

  public static void writeFeatures(String path) {
    IOUtils.writeObject(featManager, path);
  }

  public static boolean loadFeatures(String path) {
      if (!(new File(path)).exists()) return false;
    try {
      featManager = (FeatureManager) (new ObjectInputStream(IOUtils.inputStream(path))).readObject();
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  public static void init(List<DocumentDesc> data) {
    featManager = new FeatureManager();
    Logger.startTrack("LogLinearDiscourse Feature Indexing");
    indexFeatures(data);
    Logger.logs("feats: " + featManager.getFeatures());
    Logger.endTrack();
  }

}
