package coref.hobbs;

import coref.MentionType;
import mochi.io.IOUtils;

import edu.berkeley.nlp.util.Logger;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class EntTypePrototypeReader {

  
  static Map<String, EntTypeProtos> readProtos() {
    if (HobbsGlobals.prototypePath != null) return readProtos(IOUtils.text(new File(HobbsGlobals.prototypePath)));
    Logger.logs("[Hobbs] Using Default Protots");
    InputStream is = ClassLoader.getSystemClassLoader().getResourceAsStream("default-protos.txt");
    Logger.logs("[is] = " + is);
    return readProtos(IOUtils.text(is));
  }

  /**
   * Reads Prototypes
   * @param resourcePath
   * @return
   */
  static Map<String, EntTypeProtos> readProtos(String text) {
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