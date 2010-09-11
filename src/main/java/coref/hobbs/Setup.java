package coref.hobbs;

import coref.DocumentDesc;
import coref.MentProp;
import coref.Vocab;
import edu.berkeley.nlp.util.Logger;

import java.util.List;

public class Setup {


  public static void setupDiscourseFeats(List<DocumentDesc> data) {
    if (HobbsGlobals.discourseFeatsPath != null) {
      boolean succeed = LogLinearDiscourseModule.loadFeatures(HobbsGlobals.discourseFeatsPath);
      if (succeed) {
        Logger.logs("[Hobbs] Loaded Discourse Features from %s", HobbsGlobals.discourseFeatsPath);
      } else {
        Logger.logs("[Hobbs] Failed to load Discourse Features from %s", HobbsGlobals.discourseFeatsPath);
      }
    }
    if (LogLinearDiscourseModule.featManager == null) {
      LogLinearDiscourseModule.init(data);
      if (HobbsGlobals.writeDiscourseFeatsPath != null) {
        Logger.logs("[Hobbs] Wrote discourse log-linear feats to " + HobbsGlobals.writeDiscourseFeatsPath);
        LogLinearDiscourseModule.writeFeatures(HobbsGlobals.writeDiscourseFeatsPath);
      }
    }
  }

  public static void setupVocab(List<DocumentDesc> data) {
    if (HobbsGlobals.vocabPath != null) {
      boolean succeed = Vocab.loadVocab(HobbsGlobals.vocabPath);
      if (succeed) {
        Logger.logs("[Hobbs] Loaded vocab successfully from " + HobbsGlobals.vocabPath);
      } else {
        Logger.logs("[Hobbs] Failed to load vocab from " + HobbsGlobals.vocabPath);
      }
    }
    if (Vocab.global == null) {
      Vocab.init(data, HobbsGlobals.numThreads);
      if (HobbsGlobals.writeVocabPath != null) {
        Logger.logs("[Hobbs] Wrote Vocab to " + HobbsGlobals.writeVocabPath);
        Vocab.writeVocab(HobbsGlobals.writeVocabPath);
      }      
    }
  }

}
