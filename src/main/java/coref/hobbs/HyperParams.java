package coref.hobbs;

import coref.MentProp;
import coref.MentionType;
import coref.PropType;
import edu.berkeley.nlp.util.Counter;

public class HyperParams {
  public static Counter<MentProp> alphas;
  public static boolean isTokenProp(MentProp r) {
    return alphas.getCount(r) > (1-1.0e-10);
  }

  public static void init() {
    alphas = new Counter<MentProp>();
    for (MentProp r : MentProp.byType(PropType.HEAD)) {
      if (r.equals(EntTypeParams.PRO_PROP.get())) {
        alphas.setCount(r,1.0);
      } else {
        alphas.setCount(r, HobbsGlobals.headAlpha);
      }
    }
    for (MentProp r : MentProp.byType(PropType.MOD)) {
      alphas.setCount(r, HobbsGlobals.modAlpha);
    }
    for (MentProp r : MentProp.byType(PropType.GOV)) {
      alphas.setCount(r, 1.0);
    }
  }
}
