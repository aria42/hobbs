package coref.hobbs

import edu.berkeley.nlp.util.optionparser.Experiment
import coref.mentdetect.AllNPMentFinder
import edu.berkeley.nlp.util.Logger
import scala.collection.JavaConversions._
import coref.{MentProp, DocumentDesc}
import mochi.io.IOUtils

object Main extends Runnable {

  def run() {
    Logger.logs("[Hobbs] Loading Data")
    var data = getData
    Logger.logs("[Hobbs] training with %s docs", data.size.toString)
    Logger.logs("[Hobbs] Output Directory: %s", HobbsGlobals.outputDir);
    if (!IOUtils.exists(new java.io.File(HobbsGlobals.outputDir))) {
      Logger.logs("[Hobbs] Creating Output Directory")
      new java.io.File(HobbsGlobals.outputDir).mkdirs
    }
    Setup.setupVocab(data)
    data = data.filter(!_.failedToLoad)
    MentProp.init()
    EntType.init()
    HyperParams.init()
    GlobalParams.init()
    Setup.setupDiscourseFeats(data)
    // In the processing of training, spits out
    // hypothesis after each variational e-step
    Variational.train(data,GlobalParams.cur)
    Logger.logs("[Hobbs] Done")
  }

  def getData(): Seq[DocumentDesc] = {
    for (treePath <- mochi.io.IOUtils.lines(HobbsGlobals.fileList))
      yield new DocumentDesc(treePath ,new AllNPMentFinder())
  }


  def main(args: Array[String]) {
    Experiment.run(args,this, false, false, new HobbsGlobals())
  }

}