package coref;

import edu.berkeley.nlp.util.functional.Fn;
import edu.berkeley.nlp.util.functional.Functional;
import edu.berkeley.nlp.util.functional.Predicate;

import java.util.*;

public class MentProp {

  public final PropType kind;
  public final String name;

  public MentProp(PropType kind, String name) {
    this.kind = kind;
    this.name = name;
  }
  
  public String toString() {
    return String.format("MentProp(%s-%s)", kind, name);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MentProp mentProp = (MentProp) o;

    if (kind != mentProp.kind) return false;
    if (name != null ? !name.equals(mentProp.name) : mentProp.name != null) return false;

    return true;
  }

  public int hashCode() {
    int result = kind != null ? kind.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }


  /* Static */

  private static List<MentProp> all;
  private static List<MentProp> allNonHead;
  //private static Map<MentProp, Set<String>> vocabMap;
  private static Map<PropType, Collection<MentProp>> propsByType;
  
  public static List<MentProp> allNonHead() {
    if (allNonHead == null) {
      allNonHead = Functional.filter(all(), new Predicate<MentProp>() {
        public Boolean apply(MentProp input) {
          return input.kind != PropType.HEAD;
        }
      }) ;
    }
    return allNonHead;
  }

  public static List<MentProp> all() {
    return all;
  }

  public static Set<String> getVocab(MentProp r) {
    return Vocab.global.words.get(r);
  }

  public static void init() {
    all = new ArrayList<MentProp>();
    all.addAll(Vocab.global.words.keySet());
  }

  public static MentProp getHeadProp(MentionType mentType) {
    return new MentProp(PropType.HEAD, mentType.toString());
  }

  public static Collection<MentProp> byType(PropType type) {
    if (propsByType == null) {
      propsByType = Functional.groupBy(all(), new Fn<MentProp, PropType>() {
        public PropType apply(MentProp input) {
          return input.kind;
        }
      }) ;
    }
    return propsByType.get(type);
  }
}
