package coref.hobbs

import coref.Mention

object Pronoun {

  val referential =
      Set("he", "i", "his", "who", "you", "she", "him", "my", "her", "himself", "herself",
          "they", "their", "we", "who", "them", "our", "those", "us","everyone",
           "them", "our","who")
  // meaning potentially non-referrential, not necessarily,
  // 'who' should be here too
  val nonreferential =
      Set("it","its","that","which","here")

  val reflexives =
    Set("himself","herself","themselves","ourselves","itself")

  def getNullLogProbability(pro: Mention): Double =  {
    if (referential(pro.headWord.toLowerCase)) -5000.0
    else math.log(1.0/nonreferential.size)   
  }


}