package ash.grammar.node;

import ash.grammar.semantics.TypeInstance;

/**
 * Created by samtebbs on 15/03/2016.
 */
public interface Expression extends Analysable {
    public TypeInstance getType();
}
