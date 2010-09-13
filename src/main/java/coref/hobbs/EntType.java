package coref.hobbs;

import edu.berkeley.nlp.util.Logger;
import edu.berkeley.nlp.util.functional.Functional;
import edu.berkeley.nlp.util.functional.Predicate;
import fig.basic.Indexer;

import java.util.*;

public class EntType {

  public boolean isModeled;
  public EntTypeProtos protos;
  public String name;

  private EntType(String name, EntTypeProtos protos) {
    this.name = name;
    this.protos = protos;
    this.isModeled = (protos != null);
  }

  public String toString() {
    return String.format("EntType(%s)", name);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    EntType entType = (EntType) o;

    if (name != null ? !name.equals(entType.name) : entType.name != null) return false;

    return true;
  }

  public int hashCode() {
    return name != null ? name.hashCode() : 0;
  }

  /* Static  */

  private static Indexer<EntType> trueEntTypes;
  private static Indexer<EntType> junkEntTypes;
  private static Indexer<EntType> allEntTypes;
  public static Set<EntType> toSupress = new HashSet<EntType>();

  public static List<EntType> all() {
    return allEntTypes;
  }

  public static List<EntType> allTrue() {
    if (trueEntTypes == null) {
      trueEntTypes = new Indexer(Functional.filter(all(), new Predicate<EntType>() {
        public Boolean apply(EntType input) {
          return input.isModeled;
        }
      })) ;
    }
    return trueEntTypes;
  }

  public static List<EntType> allJunk() {
    if (junkEntTypes == null) {
      junkEntTypes = new Indexer(Functional.filter(all(), new Predicate<EntType>() {
        public Boolean apply(EntType input) {
          return !input.isModeled;
        }
      }));
    }
    return junkEntTypes;
  }

  public static void init() {
    List<EntType> allEntTypesList = new ArrayList<EntType>();
    Map<String, EntTypeProtos> protoMap = EntTypePrototypeReader.readProtos();
    for (Map.Entry<String, EntTypeProtos> entry : protoMap.entrySet()) {
      String name = entry.getKey();
      EntTypeProtos protos = entry.getValue();
      allEntTypesList.add(new EntType(name,protos));
    }
    for (int idx=0; idx < HobbsGlobals.numJunkTypes; ++idx) {
      String name = String.format("Junk(%d)", idx);
      allEntTypesList.add(new EntType(name, null));
    }
    allEntTypes = new Indexer(allEntTypesList);
    if (HobbsGlobals.typesToSupress == null) {
      toSupress = new HashSet<EntType>(EntType.all());
    } else {
      String[] names = HobbsGlobals.typesToSupress.split(",");
      toSupress = new HashSet<EntType>();
      for (String n: names) toSupress.add(EntType.byName(n));
    }
    Logger.logs("[Types] Not outputting entites of type: %s",toSupress.toString());
  }

  public static EntType byName(String name) {
    for (EntType entType: EntType.all()) {
      if (entType.name.equalsIgnoreCase(name)) return entType;
    }
    return null;
  }
}
