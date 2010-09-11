package coref.hobbs;

import coref.MentionType;
import mochi.io.IOUtils;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class EntTypePrototypeReader {

  /**
   * Reads Prototypes
   * @param resourcePath
   * @return
   */
  static Map<String, EntTypeProtos> readProtos(String protoPath) {
    String text = IOUtils.text(new File(protoPath));
    String[] pieces = text.split("\n\n");
    Map<String, EntTypeProtos> res = new HashMap<String, EntTypeProtos>();
    for (String p: pieces) {
      String[] lines = p.split("\n");
      String entType = lines[0];
      Map<MentionType, Set<String>> protosByMentType = new HashMap<MentionType, Set<String>>();
      for (int i=1; i < lines.length; ++i) {
        String line = lines[i];
        String[] fields = line.split("\\s+");
        MentionType mt = MentionType.fromShortName(fields[0]);
        Set<String> protos = new HashSet<String>();
        for (int j=1; j < fields.length; ++j) {
          String proto = fields[j];
          protos.add(proto);
        }
        protosByMentType.put(mt, protos);
      }
      res.put(entType, new EntTypeProtos(protosByMentType));
    }
    return res;
  }

  public static void main(String[] args) {
    System.out.println(readProtos("ent-type-protos.txt").values());
  }


}