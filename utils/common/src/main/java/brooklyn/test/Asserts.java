package brooklyn.test;

import groovy.lang.Closure;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@Beta
public class Asserts {

    private static final Logger log = LoggerFactory.getLogger(Asserts.class);

    private Asserts() {}
    
    // --- selected routines from testng.Assert for visibility without needing that package
    
    /**
     * Asserts that a condition is true. If it isn't,
     * an AssertionError, with the given message, is thrown.
     * @param condition the condition to evaluate
     * @param message the assertion error message
     */
    public static void assertTrue(boolean condition, String message) {
        if (!condition) fail(message);
    }

    /**
     * Fails a test with the given message.
     * @param message the assertion error message
     */
    static public void fail(String message) {
        throw new AssertionError(message);
    }

    // --- new routines
    
    public static <T> void eventually(Supplier<? extends T> supplier, Predicate<T> predicate) {
        eventually(ImmutableMap.<String,Object>of(), supplier, predicate);
    }
    
    public static <T> void eventually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<T> predicate) {
        eventually(flags, supplier, predicate, (String)null);
    }
    
    public static <T> void eventually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<T> predicate, String errMsg) {
        Duration timeout = toDuration(flags.get("timeout"), Duration.ONE_SECOND);
        Duration period = toDuration(flags.get("period"), Duration.millis(10));
        long periodMs = period.toMilliseconds();
        long startTime = System.currentTimeMillis();
        long expireTime = startTime+timeout.toMilliseconds();
        
        boolean first = true;
        T supplied = supplier.get();
        while (first || System.currentTimeMillis() <= expireTime) {
            supplied = supplier.get();
            if (predicate.apply(supplied)) {
                return;
            }
            first = false;
            if (periodMs > 0) sleep(periodMs);
        }
        fail("supplied="+supplied+"; predicate="+predicate+(errMsg!=null?"; "+errMsg:""));
    }
    
    // TODO improve here -- these methods aren't very useful without timeouts
    public static <T> void continually(Supplier<? extends T> supplier, Predicate<T> predicate) {
        continually(ImmutableMap.<String,Object>of(), supplier, predicate);
    }

    public static <T> void continually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<? super T> predicate) {
        continually(flags, supplier, predicate, (String)null);
    }

    public static <T> void continually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<T> predicate, String errMsg) {
        Duration duration = toDuration(flags.get("timeout"), Duration.ONE_SECOND);
        Duration period = toDuration(flags.get("period"), Duration.millis(10));
        long periodMs = period.toMilliseconds();
        long startTime = System.currentTimeMillis();
        long expireTime = startTime+duration.toMilliseconds();
        
        boolean first = true;
        while (first || System.currentTimeMillis() <= expireTime) {
            assertTrue(predicate.apply(supplier.get()), "supplied="+supplier.get()+"; predicate="+predicate+(errMsg!=null?"; "+errMsg:""));
            if (periodMs > 0) sleep(periodMs);
            first = false;
        }
    }

    
    
    public static void succeedsEventually(Runnable r) {
        succeedsEventually(ImmutableMap.<String,Object>of(), r);
    }

    public static void succeedsEventually(Map<String,?> flags, Runnable r) {
        succeedsEventually(flags, toCallable(r));
    }
    
    public static void succeedsEventually(Callable<?> c) {
        succeedsEventually(ImmutableMap.<String,Object>of(), c);
    }
    
    // FIXME duplication with TestUtils.BooleanWithMessage
    public static class BooleanWithMessage {
        boolean value; String message;
        public BooleanWithMessage(boolean value, String message) {
            this.value = value; this.message = message;
        }
        public boolean asBoolean() {
            return value;
        }
        public String toString() {
            return message;
        }
    }

    /**
     * Convenience method for cases where we need to test until something is true.
     *
     * The runnable will be invoked periodically until it succesfully concludes.
     * <p>
     * The following flags are supported:
     * <ul>
     * <li>abortOnError (boolean, default true)
     * <li>abortOnException - (boolean, default false)
     * <li>timeout - (a Duration or an integer in millis, defaults to 30*SECONDS)
     * <li>period - (a Duration or an integer in millis, for fixed retry time; if not set, defaults to exponentially increasing from 1 to 500ms)
     * <li>minPeriod - (a Duration or an integer in millis; only used if period not explicitly set; the minimum period when exponentially increasing; defaults to 1ms)
     * <li>maxPeriod - (a Duration or an integer in millis; only used if period not explicitly set; the maximum period when exponentially increasing; defaults to 500ms)
     * <li>maxAttempts - (integer, Integer.MAX_VALUE)
     * </ul>
     * 
     * The following flags are deprecated:
     * <ul>
     * <li>useGroovyTruth - (defaults to false; any result code apart from 'false' will be treated as success including null; ignored for Runnables which aren't Callables)
     * </ul>
     * 
     * @param flags, accepts the flags listed above
     * @param r
     * @param finallyBlock
     */
    public static void succeedsEventually(Map<String,?> flags, Callable<?> c) {
        boolean abortOnException = get(flags, "abortOnException", false);
        boolean abortOnError = get(flags, "abortOnError", false);
        boolean useGroovyTruth = get(flags, "useGroovyTruth", false);
        boolean logException = get(flags, "logException", true);

        // To speed up tests, default is for the period to start small and increase...
        Duration duration = toDuration(flags.get("timeout"), Duration.THIRTY_SECONDS);
        Duration fixedPeriod = toDuration(flags.get("period"), null);
        Duration minPeriod = (fixedPeriod != null) ? fixedPeriod : toDuration(flags.get("minPeriod"), Duration.millis(1));
        Duration maxPeriod = (fixedPeriod != null) ? fixedPeriod : toDuration(flags.get("maxPeriod"), Duration.millis(500));
        int maxAttempts = get(flags, "maxAttempts", Integer.MAX_VALUE);
        int attempt = 0;
        long startTime = System.currentTimeMillis();
        try {
            Throwable lastException = null;
            Object result = null;
            long lastAttemptTime = 0;
            long expireTime = startTime+duration.toMilliseconds();
            long sleepTimeBetweenAttempts = minPeriod.toMilliseconds();
            
            while (attempt < maxAttempts && lastAttemptTime < expireTime) {
                try {
                    attempt++;
                    lastAttemptTime = System.currentTimeMillis();
                    result = c.call();
                    if (log.isTraceEnabled()) log.trace("Attempt {} after {} ms: {}", new Object[] {attempt, System.currentTimeMillis() - startTime, result});
                    if (useGroovyTruth) {
                        if (groovyTruth(result)) return;
                    } else if (Boolean.FALSE.equals(result)) {
                        if (result instanceof BooleanWithMessage) 
                            log.warn("Test returned an instance of BooleanWithMessage but useGroovyTruth is not set! " +
                                     "The result of this probably isn't what you intended.");
                        return;
                    } else {
                        return;
                    }
                    lastException = null;
                } catch(Throwable e) {
                    lastException = e;
                    if (log.isTraceEnabled()) log.trace("Attempt {} after {} ms: {}", new Object[] {attempt, System.currentTimeMillis() - startTime, e.getMessage()});
                    if (abortOnException) throw e;
                    if (abortOnError && e instanceof Error) throw e;
                }
                long sleepTime = Math.min(sleepTimeBetweenAttempts, expireTime-System.currentTimeMillis());
                if (sleepTime > 0) Thread.sleep(sleepTime);
                sleepTimeBetweenAttempts = Math.min(sleepTimeBetweenAttempts*2, maxPeriod.toMilliseconds());
            }
            
            log.debug("TestUtils.executeUntilSucceedsWithFinallyBlockInternal exceeded max attempts or timeout - {} attempts lasting {} ms", attempt, System.currentTimeMillis()-startTime);
            if (lastException != null)
                throw lastException;
            fail("invalid result: "+result);
        } catch (Throwable t) {
            if (logException) log.info("failed succeeds-eventually, "+attempt+" attempts, "+
                    (System.currentTimeMillis()-startTime)+"ms elapsed "+
                    "(rethrowing): "+t);
            throw propagate(t);
        }
    }

    public static <T> void succeedsContinually(Runnable r) {
        succeedsContinually(ImmutableMap.<String,Object>of(), r);
    }
    
    public static <T> void succeedsContinually(Map<String,?> flags, Runnable r) {
        succeedsContinually(flags, toCallable(r));
    }

    public static void succeedsContinually(Callable<?> c) {
        succeedsContinually(ImmutableMap.<String,Object>of(), c);
    }
    
    public static void succeedsContinually(Map<String,?> flags, Callable<?> job) {
        Duration duration = toDuration(flags.get("timeout"), Duration.ONE_SECOND);
        Duration period = toDuration(flags.get("period"), Duration.millis(10));
        long periodMs = period.toMilliseconds();
        long startTime = System.currentTimeMillis();
        long expireTime = startTime+duration.toMilliseconds();
        
        boolean first = true;
        while (first || System.currentTimeMillis() <= expireTime) {
            try {
                job.call();
            } catch (Exception e) {
                throw propagate(e);
            }
            if (periodMs > 0) sleep(periodMs);
            first = false;
        }
    }
    
    private static Duration toDuration(Object duration) {
        return Duration.of(duration);
    }
            
    private static Duration toDuration(Object duration, Duration defaultVal) {
        if (duration == null)
            return defaultVal;
        else 
            return Duration.of(duration);
    }
    
    public static void assertFails(Runnable r) {
        assertFailsWith(toCallable(r), Predicates.alwaysTrue());
    }
    
    public static void assertFails(Callable<?> c) {
        assertFailsWith(c, Predicates.alwaysTrue());
    }
    
    public static void assertFailsWith(Callable<?> c, final Closure<Boolean> exceptionChecker) {
        assertFailsWith(c, new Predicate<Throwable>() {
            public boolean apply(Throwable input) {
                return exceptionChecker.call(input);
            }
        });
    }
    
    public static void assertFailsWith(Runnable c, final Class<? extends Throwable> validException, final Class<? extends Throwable> ...otherValidExceptions) {
        final List<Class> validExceptions = ImmutableList.<Class>builder()
                .add(validException)
                .addAll(ImmutableList.copyOf(otherValidExceptions))
                .build();
        
        assertFailsWith(c, new Predicate<Throwable>() {
            public boolean apply(Throwable e) {
                for (Class<?> validException: validExceptions) {
                    if (validException.isInstance(e)) return true;
                }
                fail("Test threw exception of unexpected type "+e.getClass()+"; expecting "+validExceptions);
                return false;
            }
        });
    }

    public static void assertFailsWith(Runnable r, Predicate<? super Throwable> exceptionChecker) {
        assertFailsWith(toCallable(r), exceptionChecker);
    }
    
    public static void assertFailsWith(Callable<?> c, Predicate<? super Throwable> exceptionChecker) {
        boolean failed = false;
        try {
            c.call();
        } catch (Throwable e) {
            failed = true;
            if (!exceptionChecker.apply(e)) {
                log.debug("Test threw invalid exception (failing)", e);
                fail("Test threw invalid exception: "+e);
            }
            log.debug("Test for exception successful ("+e+")");
        }
        if (!failed) fail("Test code should have thrown exception but did not");
    }

    private static boolean groovyTruth(Object o) {
        // TODO Doesn't handle matchers (see http://docs.codehaus.org/display/GROOVY/Groovy+Truth)
        if (o == null) {
            return false;
        } else if (o instanceof Boolean) {
            return (Boolean)o;
        } else if (o instanceof String) {
            return !((String)o).isEmpty();
        } else if (o instanceof Collection) {
            return !((Collection)o).isEmpty();
        } else if (o instanceof Map) {
            return !((Map)o).isEmpty();
        } else if (o instanceof Iterator) {
            return ((Iterator)o).hasNext();
        } else if (o instanceof Enumeration) {
            return ((Enumeration)o).hasMoreElements();
        } else {
            return true;
        }
    }
    
    private static <T> T get(Map<String,?> map, String key, T defaultVal) {
        Object val = map.get(key);
        return (T) ((val == null) ? defaultVal : val);
    }
    
    private static Callable<?> toCallable(Runnable r) {
        return (r instanceof Callable) ? (Callable<?>)r : Executors.callable(r);
    }
    
    private static void sleep(long periodMs) {
        if (periodMs > 0) {
            try {
                Thread.sleep(periodMs);
            } catch (InterruptedException e) {
                throw propagate(e);
            }
        }
    }
    
    private static RuntimeException propagate(Throwable t) {
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        if (t instanceof RuntimeException) throw (RuntimeException)t;
        if (t instanceof Error) throw (Error)t;
        throw new RuntimeException(t);
    }
}
