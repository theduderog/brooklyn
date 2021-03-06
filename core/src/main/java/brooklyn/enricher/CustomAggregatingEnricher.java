package brooklyn.enricher;

import groovy.lang.Closure;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.AbstractAggregatingEnricher;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEventListener;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.base.Function;
import com.google.common.base.Throwables;

/**
 * Subscribes to events from producers with a sensor of type T, aggregates them with the 
 * provided closure and emits the result on the target sensor V.
 * @param <T>
 */
public class CustomAggregatingEnricher<S,T> extends AbstractAggregatingEnricher<S,T> implements SensorEventListener<S> {
    
    private static final Logger LOG = LoggerFactory.getLogger(CustomAggregatingEnricher.class);
    
    protected final Function<Collection<S>, T> aggregator;
    
    /**
     * The valid keys for the flags are:
     * - producers: a collection of entities to be aggregated
     * - allMembers: indicates that should track members of the entity that the aggregator is associated with,
     *               to aggregate across all those members.
     * - filter:     a Predicate or Closure, indicating which entities to include
     * 
     * @param flags
     * @param source
     * @param target
     * @param aggregator   Aggregates a collection of values, to return a single value for the target sensor
     * @param defaultValue Default value to populate the collection given to aggregator, defaults to null
     */
    public CustomAggregatingEnricher(Map<String,?> flags, AttributeSensor<? extends S> source, AttributeSensor<T> target,
            Function<Collection<S>, T> aggregator, S defaultValue) {
        super(flags, source, target, defaultValue);
        this.aggregator = aggregator;
    }
    
    public CustomAggregatingEnricher(Map<String,?> flags, AttributeSensor<? extends S> source, AttributeSensor<T> target,
            Function<Collection<S>, T> aggregator) {
        this(flags, source, target, aggregator, null);
    }
    
    public CustomAggregatingEnricher(AttributeSensor<? extends S> source, AttributeSensor<T> target,
            Function<Collection<S>, T> aggregator, S defaultValue) {
        this(Collections.<String,Object>emptyMap(), source, target, aggregator, defaultValue);
    }
    
    public CustomAggregatingEnricher(AttributeSensor<? extends S> source, AttributeSensor<T> target,
            Function<Collection<S>, T> aggregator) {
        this(Collections.<String,Object>emptyMap(), source, target, aggregator, null);
    }

    /**
     * @param flags
     * @param source
     * @param target
     * @param aggregator   Should take a collection of values and return a single, aggregate value
     * @param defaultValue
     * 
     * @see #CustomAggregatingEnricher(Map<String,?>, AttributeSensor<S>, AttributeSensor<T> target, Function<Collection<S>, T> aggregator, S defaultValue)
     */
    @SuppressWarnings("unchecked")
    public CustomAggregatingEnricher(Map<String,?> flags, AttributeSensor<? extends S> source, AttributeSensor<T> target,
            Closure<?> aggregator, S defaultValue) {
        this(flags, source, target, GroovyJavaMethods.<Collection<S>, T>functionFromClosure((Closure<T>)aggregator), defaultValue);
    }

    public CustomAggregatingEnricher(Map<String,?> flags, AttributeSensor<? extends S> source, AttributeSensor<T> target, Closure<?> aggregator) {
        this(flags, source, target, aggregator, null);
    }

    public CustomAggregatingEnricher(AttributeSensor<S> source, AttributeSensor<T> target, Closure<?> aggregator, S defaultValue) {
        this(Collections.<String,Object>emptyMap(), source, target, aggregator, defaultValue);
    }

    public CustomAggregatingEnricher(AttributeSensor<S> source, AttributeSensor<T> target, Closure<?> aggregator) {
        this(Collections.<String,Object>emptyMap(), source, target, aggregator, null);
    }

    @Override
    public void onUpdated() {
        try {
            entity.setAttribute(target, getAggregate());
        } catch (Throwable t) {
            LOG.warn("Error calculating and setting aggregate for enricher "+this, t);
            throw Throwables.propagate(t);
        }
    }
    
    public T getAggregate() {
        synchronized (values) {
            return (T) aggregator.apply(values.values());
        }
    }

    // FIXME Clean up explosion of overloading, caused by groovy-equivalent default vals...
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            Map<String,?> flags, AttributeSensor<S> source, AttributeSensor<T> target, Closure<?> aggregator, S defaultVal) {
        return new CustomAggregatingEnricher<S,T>(flags, source, target, aggregator, defaultVal);
    }
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            Map<String,?> flags, AttributeSensor<S> source, AttributeSensor<T> target, Closure<?> aggregator) {
        return newEnricher(flags, source, target, aggregator, null);
    }
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            AttributeSensor<S> source, AttributeSensor<T> target, Closure<?> aggregator, S defaultVal) {
        return newEnricher(Collections.<String,Object>emptyMap(), source, target, aggregator, defaultVal);
    }
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            AttributeSensor<S> source, AttributeSensor<T> target, Closure<?> aggregator) {
        return newEnricher(Collections.<String,Object>emptyMap(), source, target, aggregator, null);
    }
    
    
    // FIXME Clean up explosion of overloading, caused by groovy-equivalent default vals...
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            Map<String,?> flags, AttributeSensor<S> source, AttributeSensor<T> target, Function<Collection<S>, T> aggregator, S defaultVal) {
        return new CustomAggregatingEnricher<S,T>(flags, source, target, aggregator, defaultVal);
    }
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            Map<String,?> flags, AttributeSensor<S> source, AttributeSensor<T> target, Function<Collection<S>, T> aggregator) {
        return newEnricher(flags, source, target, aggregator, null);
    }
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            AttributeSensor<S> source, AttributeSensor<T> target, Function<Collection<S>, T> aggregator, S defaultVal) {
        return newEnricher(Collections.<String,Object>emptyMap(), source, target, aggregator, defaultVal);
    }
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            AttributeSensor<S> source, AttributeSensor<T> target, Function<Collection<S>, T> aggregator) {
        return newEnricher(Collections.<String,Object>emptyMap(), source, target, aggregator, null);
    }
    
    public static <N extends Number, T extends Number> CustomAggregatingEnricher<N,T> newSummingEnricher(
            Map<String,?> flags, AttributeSensor<N> source, final AttributeSensor<T> target) {
        
        Function<Collection<N>, T> aggregator = new Function<Collection<N>, T>() {
            @Override public T apply(Collection<N> vals) {
                Object result = (vals == null || vals.isEmpty()) ? 0 : sum(vals);
                return TypeCoercions.castPrimitive(result, (Class<T>)target.getType());
            }
        };
        return new CustomAggregatingEnricher<N,T>(flags, source, target, aggregator);
    }
    public static <N extends Number> CustomAggregatingEnricher<N,N> newSummingEnricher(
            AttributeSensor<N> source, AttributeSensor<N> target) {
        return newSummingEnricher(Collections.<String,Object>emptyMap(), source, target);
    }

    // TODO semantics of ZERO default seem very odd for an average (okay for summing)
    /** creates an enricher which averages over all children/members, 
     * counting ZERO for sensors which have not yet published anything;
     * to have those sensors excluded, pass null as an additional argument (defaultValue)
     */
    // this function can't strictly return <N,Double> like the others because 
    // we have to supply a 0 of instance of N
    public static CustomAggregatingEnricher<Number,Double> newAveragingEnricher(
            Map<String,?> flags, AttributeSensor<? extends Number> source, AttributeSensor<Double> target) {
        return newAveragingEnricher(flags, source, target, 0);
    }
    /** defaultValue of null means that the sensor is excluded */
    public static <N extends Number> CustomAggregatingEnricher<N,Double> newAveragingEnricher(
            Map<String,?> flags, AttributeSensor<? extends N> source, AttributeSensor<Double> target,
            N defaultValue) {
        
        Function<Collection<N>, Double> aggregator = new Function<Collection<N>, Double>() {
            @Override public Double apply(Collection<N> vals) {
                int count = count(vals);
                return (count==0) ? 0d : ((Double) sum(vals) / count);
            }
        };
        return new CustomAggregatingEnricher<N,Double>(flags, source, target, aggregator, defaultValue);
    }
    /** averages the given source sensor over all children/members, storing in target */
    public static <N extends Number> CustomAggregatingEnricher<Number,Double> newAveragingEnricher(
            AttributeSensor<N> source, AttributeSensor<Double> target) {
        return newAveragingEnricher(Collections.<String,Object>emptyMap(), source, target);
    }

    private static <N extends Number> double sum(Iterable<N> vals) {
        double result = 0d;
        if (vals!=null) for (Number val : vals) if (val!=null) result += val.doubleValue();
        return result;
    }
    
    private static int count(Iterable<? extends Object> vals) {
        int result = 0;
        if (vals!=null) for (Object val : vals) if (val!=null) result++;
        return result;
    }

}
