package coref;

import edu.berkeley.nlp.concurrent.WorkQueue;
import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Logger;
import edu.berkeley.nlp.util.functional.Fn;
import edu.berkeley.nlp.util.functional.Functional;
import fig.basic.Pair;
import mochi.io.IOUtils;

import java.io.File;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.*;

/**
 * Singleton Object
 */
public class Vocab implements Serializable {

  public static final long serialVersionUID = 42L;

  public Map<MentProp, Set<String>> words = new HashMap<MentProp,Set<String>>();
  public Set<MentionTypeSig> mentTypeSigs = new HashSet<MentionTypeSig>();

  private Vocab() {
    mentTypeSigs = new HashSet<MentionTypeSig>();
    for (int numPropers = 0; numPropers < 3; ++numPropers) {
      for (int numNominals = 0; numNominals < 3; ++numNominals) {
        //for (int numPreNoms = 0; numPreNoms < 6; ++numPreNoms) {
          if ((numPropers + numNominals) < 4 && (numPropers + numNominals) > 0) {
            mentTypeSigs.add(new MentionTypeSig(numPropers, numNominals));
          }
        }
      }
  }
  //}


  /* Singleton */

  public static Vocab global;

  public static void init(List<DocumentDesc> docDescs, int numThreads) {    
    Logger.startTrack(String.format("Indexing Vocab with %d threads", numThreads));
    class Worker implements Runnable {
      List<DocumentDesc> docs;
//      Map<MentionType, Set<String>> heads = new HashMap<MentionType, Set<String>>();
//      Map<String, Set<String>> parents = new HashMap<String, Set<String>>();
//      Map<String, Set<String>> deps = new HashMap<String, Set<String>>();
      Map<MentProp,Set<String>> words = new HashMap<MentProp, Set<String>>();
      Set<MentionTypeSig> mentTypeSigs = new HashSet<MentionTypeSig>();
      Worker(List<DocumentDesc> docs) {
        this.docs = docs;
      }
      public void run() {
        for (DocumentDesc docDesc : docs) {
          Document doc = docDesc.getDocument();
          if (doc == null) continue;
          for (Mention ment : doc.getMentions()) {
            for (Pair<MentProp,String> pair: ment.props()) {
              CollectionUtils.addToValueSet(words, pair.getFirst(), pair.getSecond());
            }
          }
        }
      }
    }
    List<List<DocumentDesc>> splits = CollectionUtils.split(docDescs, numThreads);
    List<Worker> workers = Functional.map(splits, new Fn<List<DocumentDesc>, Worker>() {
      public Worker apply(List<DocumentDesc> input) {
        return new Worker(input);
      }});
    WorkQueue.invokeAll(workers, true);
    global = new Vocab();
    for (Worker worker : workers) {
      CollectionUtils.mergeInto(global.words, worker.words);
//      CollectionUtils.mergeInto(global.heads, worker.heads);
//      CollectionUtils.mergeInto(global.parents, worker.parents);
//      CollectionUtils.mergeInto(global.deps, worker.deps);
      global.mentTypeSigs.addAll(worker.mentTypeSigs);
    }
    for (Map.Entry<MentProp,Set<String>> entry: global.words.entrySet()) {
      Logger.logs(String.format("%s -> num words: %d", entry.getKey(), entry.getValue().size()));
    }
    Logger.endTrack();
  }

  public static void writeVocab(String path) {
    IOUtils.writeObject(global, path);
  }

  public static boolean loadVocab(String path)  {
    if (!(new File(path)).exists()) return false;
    try {
      global = (Vocab) (new ObjectInputStream(IOUtils.inputStream(path))).readObject();
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

}
