package coref;

import coref.mentdetect.IMentionFinder;

/**
 * Contains Info on how to load document, whether it is labeled
 * and whether
 */
public class DocumentDesc {
  private final String treePath;
  private final IMentionFinder mentFinder;  
  public boolean failedToLoad = false;
  
  public DocumentDesc(String treePath,
                      IMentionFinder mentFinder)
  {
    this.treePath = treePath;
    this.mentFinder = mentFinder;    
  }

  public Document getDocument() {
    try {
      String fname = new java.io.File(treePath).getName();
      Document doc = DocumentLoader.fromPrefix(treePath,mentFinder);
      return doc;
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("Bad doc: " + treePath);
      this.failedToLoad = true;
    }
    return null;
  }
}
