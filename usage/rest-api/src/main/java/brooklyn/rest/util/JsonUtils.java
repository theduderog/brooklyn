package brooklyn.rest.util;

import com.google.common.primitives.Primitives;

public class JsonUtils {

    /** returns a representation of x which can be serialized as a json object */
    public static Object toJsonable(Object x) {
        if (x==null) return null;
        // primitives and strings are simple
        if (isPrimitiveOrBoxer(x.getClass()) || x instanceof String)
            return x;
        
        // TODO plug in to other serialization techniques
        return x.toString();
    }
    
    @SuppressWarnings("unchecked")
    public static boolean isPrimitiveOrBoxer(Class<?> type) {
        return Primitives.allPrimitiveTypes().contains(type) || Primitives.allWrapperTypes().contains(type);
    }
}
