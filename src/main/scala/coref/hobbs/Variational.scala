package coref.hobbs

import java.{util => ju}
import scala.collection.JavaConversions._
import edu.berkeley.nlp.math.DoubleArrays
import coref._
import edu.berkeley.nlp.util.{CollectionUtils, Logger, Counter}
import edu.berkeley.nlp.concurrent.WorkQueue
import output.CorefTreeOutput
import scala.collection


object Variational {

  case class Datum(val ents: ju.List[Entity], val pros: ju.List[Mention])

  var useHeuristicRefer = false
  var useHeuristicPros = false
  var globalIter = 0
  def randInit = globalIter == 0

  private def getDatum(doc: Document): Datum = {
    val ents: Seq[Entity] =
      if (useHeuristicRefer) {
        if (globalIter < HobbsGlobals.numSimpleHeuristicReferIters)
          ReferringPartition.heuristic(doc)
        else
          ReferringPartition.slightlySmarterHeuristic(doc)
      }
      else ReferringPartition.learnedGuess(doc,GlobalParams.cur)
    val pros = doc.getMentions.filter(_.isPronoun)
    for ((ent,entIndex) <- ents.zipWithIndex; m: Mention <- ent.ments) {
      m.entFactor = new Array[Double](ents.size)
      m.entFactor(entIndex) = 1.0
    }
    doc.ents = ents
    Datum(ents, pros)
  }

  private def doInferenceAfterEStep(datum: Variational.Datum) : CorefCluster = {
    val chains = datum.ents.map { ent =>
      new coref.CorefChain(ent.toString, new ju.ArrayList[Mention](ent.ments))
    }
    if (!datum.pros.isEmpty && datum.pros.head.entFactor != null) {
      for (pro <- datum.pros) {      
        val maxEntIndex = DoubleArrays.argMax(pro.entFactor)
        val maxEntProb = pro.entFactor(maxEntIndex)
        if (pro.antFactor.last < 0.2) chains(maxEntIndex).ments.add(pro)
        else {
          val breakHere = true
        }
      }
    }
    val chainsToKeep = for {
      (c,e) <- chains.zip(datum.ents)
      val entType = e.entTypeFactors.argMax
      if entType.isModeled && !EntType.toSupress.contains(entType)
    } yield c
    new CorefCluster(chainsToKeep)
  }

  private def logNormalize(logX: Array[Double]): (Array[Double],Double) = {
    val logSum = edu.berkeley.nlp.math.SloppyMath.logAdd(logX)
    val posts = logX.map { logX => math.exp(logX-logSum) }
    (posts,logSum)
  }

  private def updatePro(pro: Mention, params: GlobalParams, datum: Variational.Datum) {
    require(!useHeuristicPros)
    val priorProbs = params.getAntProbs(pro)
    pro.antFactor = new Array[Double](pro.possibleAnts.size)
    // E_T lg P(M| E) for each E
    val expEntLogProbs = new Array[Double](datum.ents.size)
    for ((ent,entIndex) <- datum.ents.zipWithIndex;
         entry <- ent.entTypeFactors.entrySet;
         entType = entry.getKey;
         entTypeProb = entry.getValue.asInstanceOf[Double];
         if entTypeProb > 0.0) {      
      val mentLogProb = params.thetaByType.get(entType).getMentLogProb(pro,ent)
      expEntLogProbs(entIndex) += entTypeProb * mentLogProb
    }    
    for ((ant, antIndex) <- pro.possibleAnts.zipWithIndex; if !ant.isNull) {
      var expLogProb = 0.0
      for ((entProb, entIndex) <- ant.ant.entFactor.zipWithIndex; if entProb > 0) {
        expLogProb += entProb * expEntLogProbs(entIndex)
      }
      val logPrior = math.log(priorProbs(antIndex))
      pro.antFactor(antIndex) = logPrior + expLogProb
    }
    val nullIndex = pro.possibleAnts.size - 1
    pro.antFactor(nullIndex) = math.log(priorProbs(nullIndex)) + Pronoun.getNullLogProbability(pro)                   
    val (normedAntFactor, logSum) = logNormalize(pro.antFactor)
    pro.antFactor = normedAntFactor
    pro.entFactor = new Array[Double](datum.ents.size)
    for{
      (antProb, antIndex) <- pro.antFactor.zipWithIndex
      ant = pro.possibleAnts(antIndex)
      if !ant.isNull
      (entProb, entIndex) <- ant.ant.entFactor.zipWithIndex      
    } pro.entFactor(entIndex) += antProb * entProb
//    if (pro.sentIndex == pro.doc.sents.size-1) {
//      val maxEntIndex = DoubleArrays.argMax(pro.entFactor)
//      val maxEnt = datum.ents(maxEntIndex)
//      val distFn: Antecedent => Int = { ant =>
//        if (ant.isNull) Math.MAX_INT
//        else ant.treeDist
//      }
//  //    Logger.logs(pro.sent.words.mkString(" "))
//      val closestAnts = pro.possibleAnts.sortBy(distFn).take(5)
//      for (ant <- closestAnts; if !ant.isNull) {
//        val possibleEnts = ant.ant.possibleEnts()
//        Logger.logs(possibleEnts.toString)
//      }
//      Logger.logs(pro.entFactor.toSeq.toString)
//    }
  }

  private def updateEntType(entIndex: Int, datum: Datum, params: GlobalParams) {
    val ent = datum.ents(entIndex)
    val weightedMents: Seq[(Mention, Double)] = {
      val referWeightedMents: Seq[(Mention, Double)] = ent.ments.map(m => (m, 1.0))
      val proWeightedMents =
        if (datum.pros.isEmpty || datum.pros.head.entFactor == null) Seq[(Mention, Double)]()
        else datum.pros.map(pro => pro -> pro.entFactor(entIndex))
      referWeightedMents ++ proWeightedMents
    }
    val (logProb, posts): (Double, Counter[EntType]) = {
      if (randInit) EntTypeFactor.randPosts(ent)
      else EntTypeFactor.computePosts(ent, weightedMents, params)
    }
    ent.entTypeFactors = posts
  }

  private def updateAllEntTypes(datum: Datum, params: GlobalParams) {
    for ((ent,entIndex) <- datum.ents.zipWithIndex) {
      updateEntType(entIndex, datum, params)
    }    
  }

  private def updateAllPros(datum: Datum, params: GlobalParams) {
    for (pro <- datum.pros) {
      updatePro(pro, params, datum)
    }    
  }

  private def doEStep(datum: Datum, params: GlobalParams) {
    if (useHeuristicPros) {
      updateAllEntTypes(datum,params)
      return
    }
    for (innerIter <- 0 until HobbsGlobals.numInnerIters) {
      // Ent Type Update
      updateAllEntTypes(datum,params)
      // Pro Factor
      updateAllPros(datum, params)
    }
  }

  private class ErrorStats {
    val tests: Map[String,(Entity => Boolean,Entity => Double)] = {
      Map( "sir"-> (_.getWordList(new MentProp(PropType.MOD, "nn")).contains("Sir"),
                   _.entTypeFactors.getCount(EntType.byName("per-individual"))),
          "mr"-> (_.getWordList(new MentProp(PropType.MOD, "nn")).contains("Mr."),
                   _.entTypeFactors.getCount(EntType.byName("per-individual"))))
    }

    val results = scala.collection.mutable.Map[String,(Double,Double)]()
    
    def update(ent: Entity) {
      for ((id,(validFn,scoreFn)) <- tests; if validFn(ent)) {
        var (correct,total) = results.getOrElse(id,(0.0,0.0))
        results(id) = (correct + scoreFn(ent),total + 1.0)        
      }
    }                 
    
    def report() {
      for ((id,(correct,total)) <- results) {
        Logger.logs("%s test accuracy: %s (%s/%s)",
            id,(correct/total).toString,correct.toString,total.toString)
      }
    }
  }

  private def mapReduce[D,I <: AnyRef,R](data: Seq[D], mapFn: Seq[D] => I, reduceFn: Seq[I] => R): R = {
    val splits = CollectionUtils.split(data, coref.hobbs.HobbsGlobals.numThreads)
    class Worker(val split: Seq[D]) extends Runnable {
      var i: Option[I] = None
      override def run() {
        i = Some(mapFn(split))
      }
    }
    val workers = splits.map(new Worker(_)) 
    WorkQueue.invokeAll(workers,true)
    reduceFn(workers.map(_.i.get))
  }

  private def doParallelIter(data: ju.List[DocumentDesc], params: GlobalParams): (Double,GlobalParams) = {
    def mapFn(split: Seq[DocumentDesc]): GlobalSuffStats = doSerialIter(split,params)._2
    def reduceFn(allStats: Seq[GlobalSuffStats]): GlobalParams = {
      val newParams = new GlobalParams()
      for (stats <- allStats) {
        stats.updateParams(newParams)
      }
      newParams
    }
    val newParams = mapReduce(data, mapFn, reduceFn)
    (0.0,newParams)
  }

  private def updateDiscourseFactor(doc: Document,params: GlobalParams,stats: GlobalSuffStats,datum: Variational.Datum) {
    val breakHere = true
    if (globalIter >= HobbsGlobals.numSimpleHeuristicReferIters) {
      val entAssigns = new Array[Int](doc.getMentions.size)
      java.util.Arrays.fill(entAssigns,-1)
      for ((ent,index) <- datum.ents.zipWithIndex; ment <- ent.ments) {
        entAssigns(ment.index) = index
      }
      // nominal terms
      for (nom: Mention <- doc.getMentions; if nom.isNominal) {
        val ent: Entity = datum.ents(entAssigns(nom.index))
        val isFirst = ent.ments.map(_.index).min == nom.index
        val antProbs = params.getAntProbs(nom)
        if (isFirst) {
          java.util.Arrays.fill(antProbs,0.0)
          antProbs(antProbs.length-1) = 1.0
        } else {
          for ((ant, antIndex) <- nom.possibleAnts.zipWithIndex) {
            if (ant.isNull || !ent.ments.contains(ant.ant)) {
              antProbs(antIndex) = 0.0
            }
          }
          DoubleArrays.probabilisticNormalize(antProbs)
        }
        val elems = nom.possibleAnts.zip(antProbs)
        stats.nomPiSuffStats.add(DiscourseSuffStats.fromAntFactors(elems))
      }
    }
    // pronoun terms
    if (!useHeuristicPros) {
      for (pro <- datum.pros) {
        require(pro.possibleAnts.size == pro.antFactor.size)
        val elems: Seq[(Antecedent, Double)] = pro.possibleAnts.zip(pro.antFactor)
        stats.proPiSuffStats.add(DiscourseSuffStats.fromAntFactors(elems))
      }
    }
  }

  private def output(doc: Document, datum: Variational.Datum) {
    if (HobbsGlobals.outputDir != null) {
      val outFile = new java.io.File(HobbsGlobals.outputDir, doc.id).getAbsolutePath
      val cc = doInferenceAfterEStep(datum)
      CorefTreeOutput.outputDocument(outFile, doc, cc)
      val outMultiEntFile = mochi.io.IOUtils.changeExt(outFile, ".multi")
      val lines = scala.collection.mutable.ArrayBuffer[String]()
      for (ent <- datum.ents; if ent.ments.size > 0) {
        lines += ent.getMentionTypeSig.toString
        lines += ent.ments.mkString("[",",","]")
        lines += ""
      }
      mochi.io.IOUtils.writeLines(outMultiEntFile,lines)
      val outEntInfo = mochi.io.IOUtils.changeExt(outFile, ".entInfo")
      lines.clear
      for (ent <- datum.ents; if ent.ments.size > 0) {
        val entTypePostsStr =
          (for (e <- ent.entTypeFactors.entrySet; if e.getValue.asInstanceOf[Double] > 1.0e-2)
            yield "%s %s".format(e.getKey.name.toString,e.getValue.toString)).mkString(""," ","")
        lines.add(entTypePostsStr)
        for (m <- ent.ments) {
          lines.add(m.words.mkString(""," ",""))
        }
        lines.add("")
      }
      mochi.io.IOUtils.writeLines(outEntInfo,lines)
    }
  }

  private def doSerialIter(data: ju.List[DocumentDesc], params: GlobalParams): (Double, GlobalSuffStats) =  {
    val stats = new GlobalSuffStats
    val errAnalysis = new ErrorStats
    val numDiscourseDocs: Int = (data.size * HobbsGlobals.discourseDataFrac).asInstanceOf[Int]
    for ((desc,docIndex) <- data.view.zipWithIndex) {
      val doc = desc.getDocument
      val datum = getDatum(doc)
      // EStep
      doEStep(datum, params)
      // MStep
      for ((ent,entIndex) <- datum.ents.zipWithIndex) {
        val referWeightedMents = ent.ments.map(m => (m,1.0))
        val proWeightedMents =
          if (useHeuristicPros) Seq[(Mention,Double)]()
          else for (pro <- datum.pros; if pro.entFactor(entIndex) > 1.0e-8) yield  {
                 pro -> pro.entFactor(entIndex)
               }
        val weightedMents = referWeightedMents ++ proWeightedMents
        EntTypeFactor.updateStats(ent, weightedMents, ent.entTypeFactors, stats)
        errAnalysis.update(ent)
      }
      if (docIndex < numDiscourseDocs) updateDiscourseFactor(doc, params, stats, datum)
      // Output
      output(doc, datum)
    }
    if (true) errAnalysis.report    
    (0.0, stats)
  }

  def train(data: ju.List[DocumentDesc],params0: GlobalParams): GlobalParams =  {
    var params = params0
    for (iter <- 0 until HobbsGlobals.numIters) {
      globalIter = iter
      useHeuristicRefer = iter < HobbsGlobals.numHeuristicReferIters
      useHeuristicPros = iter < HobbsGlobals.numNoProFactorIters
      Logger.startTrack("[Hobbs Variational] on iter %s/%s", (iter + 1).toString, HobbsGlobals.numIters.toString)
      Logger.logs("[Hobbs Variational] heuristicProFactor: %s heuristicReferPartition: %s".
          format(useHeuristicPros,useHeuristicRefer))
      val (logProb, newParams) =
        doParallelIter(data,
          params)
      params = newParams
      params.inspect
      GlobalParams.cur = params
      GlobalParams.cur.maximize
      Logger.endTrack();
    }
    params
  }
}