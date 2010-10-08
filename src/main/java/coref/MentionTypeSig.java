package coref;

import edu.berkeley.nlp.util.Counter;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public class MentionTypeSig implements Serializable {

  public static final long serialVersionUID = 42L;

  public final int numPropers, numNoms;

  public MentionTypeSig(int numPropers, int numNoms) {
    this.numPropers = numPropers;
    this.numNoms = numNoms;
    //this.numPreNoms = numPreNoms;
  }

  public int getCount(MentionType mt) {
    switch (mt) {
      case PROPER: return numPropers;
      case NOMINAL: return numNoms;
      //case PRENOMINAL: return numPreNoms;
      default:
        throw new IllegalStateException();  
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MentionTypeSig that = (MentionTypeSig) o;

    if (numNoms != that.numNoms) return false;
   // if (numPreNoms != that.numPreNoms) return false;
    if (numPropers != that.numPropers) return false;

    return true;
  }

  public int hashCode() {
    int result = numPropers;
    result = 31 * result + numNoms;
    //result = 31 * result + numPreNoms;
    return result;
  }

  public String toString() {
    return String.format("(%d,%d)", numPropers, numNoms);
  }

  /* Factory */
  public static MentionTypeSig fromHeads(Map<MentionType, Set<String>> heads) {
    Counter<MentionType> mentTypeCounts = new Counter<MentionType>();
    for (Map.Entry<MentionType, Set<String>> entry : heads.entrySet()) {
      mentTypeCounts.incrementCount(entry.getKey(), entry.getValue().size());
    }
    if (mentTypeCounts.getCount(MentionType.PRENOMINAL)> 0) {
      throw new RuntimeException("Shouldn't have gotten a prenominal");
    }
    return new MentionTypeSig((int) mentTypeCounts.getCount(MentionType.PROPER),
      (int) mentTypeCounts.getCount(MentionType.NOMINAL));
  }

}
