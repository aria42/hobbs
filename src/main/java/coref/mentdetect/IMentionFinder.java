package coref.mentdetect;

import coref.Document;
import coref.Mention;
import java.io.Serializable;
import java.util.List;

public interface IMentionFinder extends Serializable {

  public List<Mention> getMentions(Document doc);

}
