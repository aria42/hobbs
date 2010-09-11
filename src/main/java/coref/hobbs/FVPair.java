package coref.hobbs;

import edu.berkeley.nlp.classify.Feature;

public class FVPair {
  public final Feature f;
  public final double v;

  public FVPair(Feature f, double v) {
    this.f = f;
    this.v = v;
  }
}
