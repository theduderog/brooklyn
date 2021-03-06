package brooklyn.mementos;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brooklyn.entity.rebind.RebindManager;

import com.google.common.annotations.VisibleForTesting;

/**
 * Controls the persisting and reading back of mementos. Used by {@link RebindManager} 
 * to support brooklyn restart.
 */
public interface BrooklynMementoPersister {

    BrooklynMemento loadMemento() throws IOException;
    
    void checkpoint(BrooklynMemento memento);
    
    void delta(Delta delta);

    void stop();

    @VisibleForTesting
    void waitForWritesCompleted(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException;

    public interface Delta {
        Collection<LocationMemento> locations();
        Collection<EntityMemento> entities();
        Collection<PolicyMemento> policies();
        Collection<String> removedLocationIds();
        Collection<String> removedEntityIds();
        Collection<String> removedPolicyIds();
    }
}
