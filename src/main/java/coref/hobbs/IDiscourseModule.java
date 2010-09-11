package coref.hobbs;

import coref.Mention;

public interface IDiscourseModule  {
  public double[] getAntProbs(Mention ment);
  public void observe(DiscourseSuffStats antChoiceSuffStats);
  public void makeDistribution();    
}
