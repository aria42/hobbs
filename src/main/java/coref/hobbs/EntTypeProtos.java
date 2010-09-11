package coref.hobbs;

import coref.MentionType;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EntTypeProtos implements Serializable {
  public final Map<MentionType, Set<String>> protos;
 

  public EntTypeProtos(Map<MentionType, Set<String>> protos) {    
    this.protos = protos;
  }

  public boolean allowsMentType(MentionType mentType) {
    Set<String> s = protos.get(mentType);
    return s != null && !s.isEmpty();
  }

  public boolean isMatch(Entity ent) {
    for (Map.Entry<MentionType, Set<String>> entry : protos.entrySet()) {
      MentionType mentType = entry.getKey();
      Set<String> protos = entry.getValue();
      List<String> headWords = ent.getHeadWords(mentType);
      if (headWords == null) continue;
      for (String w : headWords) {
        if (protos.contains(w.toLowerCase())) {
          // is match
          return true;
        }
      }
    }
    return false;
  }

//  public boolean isValid(Entity ent) {
//    for (Map.Entry<MentionType, Set<String>> entry : ent.headWords.entrySet()) {
//      if (!entry.getValue().isEmpty() && !allowsMentType(entry.getKey())) return false;
//    }
//    return true;
//  }
}