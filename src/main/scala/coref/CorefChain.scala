package coref

import java.{util => ju}
import reflect.BeanProperty

class CorefChain(@BeanProperty val id: String,
                 @BeanProperty val ments: ju.List[Mention]) 