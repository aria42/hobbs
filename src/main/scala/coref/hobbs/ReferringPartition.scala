package coref.hobbs

import scala.collection
import scala.collection.JavaConversions._
import java.{util => ju}
import coref.{Antecedent, Document, Mention, SyntacticConfigurations}
import edu.berkeley.nlp.math.{DoubleArrays, SloppyMath}
import edu.berkeley.nlp.util.{Logger, Counters, Counter}

object ReferringPartition {

  def heuristic(doc: Document): Seq[Entity] = {
    val res: ju.List[ju.List[Mention]] = new ju.ArrayList[ju.List[Mention]]()
    val invMapping = new ju.IdentityHashMap[Mention,Int]()
    for (ment: Mention <- doc.getMentions; if !ment.isPronoun) {
      val forced = SyntacticConfigurations.getForced(ment)
      if (forced != null) {
        //require(ment.possibleAnts.exists(_.ant eq forced))
        val entIndex = invMapping.get(forced)
        res.get(entIndex).add(ment)
        invMapping.put(ment,entIndex)
      } else {
        val validAnts =
          if (ment.isProper)
            (for {
               ant <- doc.getMentions.subList(0,ment.index)
               if !ant.isPronoun && ant.headWord.equalsIgnoreCase(ment.headWord)
            } yield (new Antecedent(ment,ant)))
          else ment.possibleAnts.filter(a => !a.isNull && a.ant.headWord.equalsIgnoreCase(ment.headWord))
        if (validAnts.isEmpty) {
          val newEnt = new ju.ArrayList[Mention]()
          invMapping.put(ment,res.size)
          newEnt.add(ment)
          res.add(newEnt)
        } else {
          val entDists = new Counter[Int]()
          for (ant <- validAnts) {
            val entIndex = invMapping(ant.ant)
            if (!entDists.containsKey(entIndex)) {
              entDists.setCount(entIndex,ant.treeDist)
            } else {
              entDists.setCount(entIndex,math.min(entDists.getCount(entIndex),ant.treeDist))
            }
          }
          val minEntIndex =  Counters.sortedKeys(entDists).last
          res.get(minEntIndex).add(ment)
          invMapping.put(ment,minEntIndex)
        }
      }
    }
    res.map(ments => Entity.heuristicFromMentions(ments))
  }

  // A Part represents mention in a potential Entity
  // Part = Mention + old Part + pieceDiscourseLogProb (prob of mention attaching to old part)
  class Part(ment: Mention, old: Part, pieceDiscourseLogProb: Double) {
    def this(ment: Mention, discourseLogProb: Double) = this(ment,null,discourseLogProb)
    // referring mention indices
    val mentIndices: Set[Int] = (if (old == null) Set[Int]() else old.mentIndices) + ment.referIndex
    // additive amongst all parts
    val discourseLogProb: Double = pieceDiscourseLogProb + (if (old==null) 0 else old.discourseLogProb)
    // entity from ments
    val ent: Entity =
      if (old == null) Entity.heuristicFromMentions(Seq(ment))
      else {
        Entity.heuristicFromMentions(ment +: old.ent.ments.toIndexedSeq)
      }
    val semanticLogProb: Double = {
      if (ment.headWord.equalsIgnoreCase("ruling")) {
        val break = true
      }
      val (logSum, posts) = EntTypeFactor.computePosts(ent,ent.ments.map(m => m -> 1.0),GlobalParams.cur)
      ent.entTypeFactors = posts
      logSum
    }
    val likelyEntTypes: Set[EntType] = {
      (for (e <- ent.entTypeFactors.entrySet; if (e.getValue.asInstanceOf[Double] > 0.05)) yield e.getKey).toSet  
    }
    val score: Double = semanticLogProb + discourseLogProb
  }


  class Hypothesis(val parts: Seq[Part], val mentAssigns: Map[Int,Int]) {

    def this() = this(Seq(),Map())
    val score = parts.map(_.score).sum

    def insertInto(ment: Mention, discourseLogProb: Double, partIndex: Int): Hypothesis = {
      val before = parts.take(partIndex)
      val cur = new Part(ment,parts(partIndex),discourseLogProb)
      val after = parts.drop(partIndex+1)
      new Hypothesis(before ++ (cur +: after),this.mentAssigns + (ment.referIndex -> partIndex))
    }

    def successors(ment: Mention,likelyEntTypes: Set[EntType], cutOff: Double): ju.List[Hypothesis] = {
      if (score < cutOff) return java.util.Collections.emptyList()
      var numPruned = 0
      var total = 0
      var res = new ju.ArrayList[Hypothesis]()
      val (newDiscourseLogProb,partDiscourseLogProbs) = {
        if (ment.isProper) {
          val logProbs = new Array[Double](parts.size)
          for ((part,partIndex) <- parts.zipWithIndex) {
            logProbs(partIndex) = math.log(part.ent.ments.size/(ment.referIndex+HobbsGlobals.properAlpha))
          }
          val newLogProb = math.log(HobbsGlobals.properAlpha/(ment.referIndex+HobbsGlobals.properAlpha))
          (newLogProb,logProbs)
        } else {
          require(ment.isNominal)
          val antProbs = GlobalParams.cur.getAntProbs(ment)
          val partProbs = new Array[Double](parts.size)
          val summedAntProbs = new Array[Double](parts.size)
          for ((ant,antIndex) <- ment.possibleAnts.zipWithIndex; if !ant.isNull) {
            val partIndex = mentAssigns(ant.ant.referIndex)
            partProbs(partIndex) = partProbs(partIndex) + antProbs(antIndex)
          }
          DoubleArrays.logInPlace(partProbs)
          (math.log(antProbs.last), partProbs)          
        }
      }
      val newParts = new ju.ArrayList[Part](parts)
      newParts.add(new Part(ment,newDiscourseLogProb))
      res.add(new Hypothesis(newParts, this.mentAssigns + (ment.referIndex-> parts.size)))
      for ((part,partIndex) <- parts.zipWithIndex;
           partDiscourseLogProb = partDiscourseLogProbs(partIndex))
      {
        if  (score + partDiscourseLogProb > cutOff &&
             !likelyEntTypes.intersect(part.likelyEntTypes).isEmpty)
          res.add(insertInto(ment,partDiscourseLogProb,partIndex))
        else
          numPruned += 1
        total += 1
      }
      res
    }
  }

  def learnedGuess(doc: Document, params: GlobalParams): Seq[Entity] =
  {
    // If too many mentions, revert to greedy (not same as heuristic)
    val numHyps =
      if (doc.getReferMentions.size < 200) HobbsGlobals.numReferHyps
      else 1    
    var hyps = ju.Collections.singletonList(new Hypothesis())
    for (ment <- doc.getMentions; if !ment.isPronoun) {
      var succs: ju.List[Hypothesis] = new ju.ArrayList[Hypothesis]()
      val singleEntTypePosts =
        EntTypeFactor.computePosts(Entity.heuristicFromMentions(Seq(ment)),List(ment -> 1.0),GlobalParams.cur)._2
      val likelyEntTypes: Set[EntType] =
        (for (e <- singleEntTypePosts.entrySet; if (e.getValue.asInstanceOf[Double] > 0.05)) yield e.getKey).toSet
      var cutoff = scala.Double.NegativeInfinity      
      for (hyp <- hyps) {
        succs.addAll(hyp.successors(ment,likelyEntTypes,cutoff))
        succs = succs.sortBy(-_.score).take(numHyps)
        cutoff = succs.last.score
      }
      hyps = succs.take(HobbsGlobals.numReferHyps)
    }
    val ents = hyps.head.parts.map(_.ent)
    ents
  }
}