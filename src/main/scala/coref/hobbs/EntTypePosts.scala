package coref.hobbs

import java.{util => ju}
import scala.collection.JavaConversions._
import coref.Mention
import edu.berkeley.nlp.util.{Counters, Counter}
import edu.berkeley.nlp.math.SloppyMath


object EntTypeFactor {

  def ensureProtos(ent: Entity, posts: Counter[EntType]) {
    val forced = ent.getForcedTypes
    if (!forced.isEmpty) {
      for (t <- EntType.all; if forced.contains(t)) {
        posts.removeKey(t)
      }
    }
  }

  def randPosts(ent: Entity): (Double,Counter[EntType]) =
  {
    val posts = new Counter[EntType]()
    val forced = ent.getForcedTypes
    if (!forced.isEmpty) {
      for (t <- forced) {
        posts.setCount(t, 1.0/forced.size)
      }
      return (0.0,posts)
    }
    for (t <- EntType.all) {
      posts.setCount(t,HobbsGlobals.rand.nextDouble)
    }
    posts.normalize
    (0.0,posts)
  }

  def computeLogZ(ent: Entity,
                  weightedMents: Seq[(Mention,Double)],
                  params: GlobalParams,
                  doEnsureProtos: Boolean = true): Double = {
    val allowed =
      if (doEnsureProtos) {
        val forced = ent.getForcedTypes
        if (forced.isEmpty) EntType.all
        else forced
      }
      else EntType.all
    // Normal
    val posts = new Counter[EntType]()
    val postsArr = new Array[Double](EntType.all.size)
    java.util.Arrays.fill(postsArr,Double.NegativeInfinity)
    for (t <- allowed) {
      val index = EntType.all.indexOf(t)
      val typeParams = params.thetaByType.get(t)
      val logPrior = params.phi.getLogProb(t)
      val logEntProb = typeParams.getEntLogProb(ent)
      val logTotMentProb = weightedMents.foldLeft(0.0) {
        case (sum,(m,w)) => sum + w * typeParams.getMentLogProb(m,ent)
      }
      val logProb = logPrior + logEntProb + logTotMentProb
      postsArr(index) = logProb
    }
    SloppyMath.logAdd(postsArr)
  }

  def computePosts(ent: Entity,
                   weightedMents: Seq[(Mention,Double)],
                   params: GlobalParams,
                   doEnsureProtos: Boolean = true): (Double,Counter[EntType]) =
  {
    val allowed =
      if (doEnsureProtos) {
        val forced = ent.getForcedTypes
        if (forced.isEmpty) EntType.all
        else forced
      }
      else EntType.all
    // Normal
    val posts = new Counter[EntType]()
    val postsArr = new Array[Double](EntType.all.size)
    java.util.Arrays.fill(postsArr,Double.NegativeInfinity)
    for (t <- allowed) {
      val index = EntType.all.indexOf(t)
      val typeParams = params.thetaByType.get(t)
      val logPrior = params.phi.getLogProb(t)
      val logEntProb = typeParams.getEntLogProb(ent)
      val logTotMentProb = weightedMents.foldLeft(0.0) {
        case (sum,(m,w)) => sum + w * typeParams.getMentLogProb(m,ent)
      }
      val logProb = logPrior + logEntProb + logTotMentProb
      postsArr(index) = logProb
    }
    val logSum = SloppyMath.logAdd(postsArr)
    for (t <- allowed) {
      posts.setCount(t, math.exp(postsArr(EntType.all.indexOf(t))-logSum))
    }
    //Counters.makeProbsFromLogScoresInPlace(posts)
    (logSum,posts)
  }

  def updateStats(ent: Entity,
                  weightedMents: Seq[(Mention,Double)],
                  posts: Counter[EntType],
                  stats: GlobalSuffStats)
  {
    for (t <- EntType.all; post = posts.getCount(t); if post > 0.0) {
      stats.phiSuffStats.incrementCount(t, post)
      val typeStats = stats.thetaSuffStats.get(t)
      typeStats.observeEnt(ent, post)
      for ((m,w) <- weightedMents) {
        typeStats.observeMent(m,ent,post * w)
      }
    }
  }

}