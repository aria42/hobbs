package coref;

import java.io.Serializable;
import java.util.List;

public interface MentionFinder extends Serializable {

  public List<Mention> getMentions(Document doc);

}
