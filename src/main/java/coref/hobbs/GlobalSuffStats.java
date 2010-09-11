package coref.hobbs;

import edu.berkeley.nlp.util.Counter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlobalSuffStats  {

  public Counter<EntType> phiSuffStats;
  public Map<EntType, EntTypeSuffStats> thetaSuffStats;
  public List<DiscourseSuffStats> proPiSuffStats;
  public List<DiscourseSuffStats> nomPiSuffStats;

  public GlobalSuffStats() {
    init();
  }

  public void updateParams(GlobalParams params) {
    for (Map.Entry<EntType, Double> entry : phiSuffStats.entrySet()) {
      params.phi.observe(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<EntType, EntTypeSuffStats> entry : thetaSuffStats.entrySet()) {
      EntType type = entry.getKey();
      entry.getValue().updateParams(params.thetaByType.get(type));
    }
    for (DiscourseSuffStats piSuffStat : proPiSuffStats) {
     params.proPi.observe(piSuffStat);
    }
    for (DiscourseSuffStats piSuffStat : nomPiSuffStats) {
     params.nomPi.observe(piSuffStat);
    }
    init();
  }

  private void init() {
    phiSuffStats = new Counter<EntType>();
    thetaSuffStats = new HashMap<EntType, EntTypeSuffStats>();
    proPiSuffStats = new ArrayList<DiscourseSuffStats>();
    nomPiSuffStats = new ArrayList<DiscourseSuffStats>();
    for (EntType type : EntType.all()) {
      thetaSuffStats.put(type, new EntTypeSuffStats(type));
    }
  }
}
