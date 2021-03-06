package brooklyn.management.internal

import static org.testng.Assert.*

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.config.BrooklynProperties
import brooklyn.entity.Entity
import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxying.EntitySpecs
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.management.Task
import brooklyn.test.TestUtils
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
import com.google.common.collect.Lists

class EntityExecutionManagerTest {
    
    protected static final Logger LOG = LoggerFactory.getLogger(EntityExecutionManagerTest.class);
    
    private static final int TIMEOUT_MS = 10*1000
    
    private TestApplication app;
    private TestEntity e;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testGetTasksOfEntity() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        e = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        
        CountDownLatch latch = new CountDownLatch(1)
        Task task = e.executionContext.submit( [tag : ManagementContextInternal.NON_TRANSIENT_TASK_TAG], { latch.countDown() } )
        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        
        Collection<Task> tasks = app.managementContext.executionManager.getTasksWithTag(e);
        assertEquals(tasks, [task])
    }
    
    @Test
    public void testUnmanagedEntityCanBeGcedEvenIfPreviouslyTagged() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        e = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        String eId = e.getId();
        
        e.invoke(TestEntity.MY_EFFECTOR, ImmutableMap.<String,Object>of()).get();
        Set<Task<?>> tasks = app.getManagementContext().getExecutionManager().getTasksWithTag(e);
        Task task = Iterables.get(tasks, 0);
        assertTrue(task.getTags().contains(e));

        Set<Object> tags = app.getManagementContext().getExecutionManager().getTaskTags();
        assertTrue(tags.contains(e), "tags="+tags);
        
        Entities.destroy(e);
        e = null;
        for (int i = 0; i < 5; i++) System.gc();
        
        Set<Object> tags2 = app.getManagementContext().getExecutionManager().getTaskTags();
        for (Object tag : tags2) {
            if (tag instanceof Entity && ((Entity)tag).getId().equals(eId)) {
                fail("tags contains unmanaged entity "+tag);
            }
        }
    }
    
    @Test(groups="Integration")
    public void testUnmanagedEntityGcedOnUnmanageEvenIfEffectorInvoked() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        
        BasicAttributeSensor byteArrayAttrib = new BasicAttributeSensor(Object.class, "test.byteArray", "");

        for (int i = 0; i < 1000; i++) {
            try {
                LOG.debug("testUnmanagedEntityGcedOnUnmanageEvenIfEffectorInvoked: iteration="+i);
                TestEntity entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
                entity.setAttribute(byteArrayAttrib, new BigObject(10*1000*1000));
                entity.invoke(TestEntity.MY_EFFECTOR, ImmutableMap.<String,Object>of()).get();
                Entities.destroy(entity);
            } catch (OutOfMemoryError e) {
                LOG.info("testUnmanagedEntityGcedOnUnmanageEvenIfEffectorInvoked: OOME at iteration="+i);
                throw e;
            }
        }
    }
    
    /**
     * Invoke effector many times, where each would claim 10MB because it stores the return value.
     * If it didn't gc the tasks promptly, it would consume 10GB ram (so would OOME before that).
     */
    @Test(groups="Integration")
    public void testEffectorTasksGcedSoNoOome() throws Exception {
        
        BrooklynProperties brooklynProperties = new BrooklynProperties();
        brooklynProperties.put(BrooklynGarbageCollector.GC_PERIOD, 1);
        brooklynProperties.put(BrooklynGarbageCollector.MAX_TASKS_PER_TAG, 2);
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class, Entities.newManagementContext(brooklynProperties));
        TestEntity entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        
        for (int i = 0; i < 1000; i++) {
            try {
                LOG.debug("testEffectorTasksGced: iteration="+i);
                entity.invoke(TestEntity.IDENTITY_EFFECTOR, [arg: new BigObject(10*1000*1000)]).get();
                
                Thread.sleep(1); // Give GC thread a chance to run
                
            } catch (OutOfMemoryError e) {
                LOG.info("testEffectorTasksGced: OOME at iteration="+i);
                throw e;
            }
        }
    }
    
    @Test(groups="Integration")
    public void testEffectorTasksGcedForMaxPerTag() throws Exception {
        int maxNumTasks = 2;
        BrooklynProperties brooklynProperties = new BrooklynProperties();
        brooklynProperties.put(BrooklynGarbageCollector.GC_PERIOD, 1000);
        brooklynProperties.put(BrooklynGarbageCollector.MAX_TASKS_PER_TAG, 2);
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class, Entities.newManagementContext(brooklynProperties));
        TestEntity entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        
        List<Task<?>> tasks = Lists.newArrayList();
        
        for (int i = 0; i < (maxNumTasks+1); i++) {
            Task<?> task = entity.invoke(TestEntity.MY_EFFECTOR, ImmutableMap.<String,Object>of());
            task.get();
            tasks.add(task);
        }
        
        // Should initially have all tasks
        Set<Task<?>> storedTasks = app.getManagementContext().getExecutionManager().getTasksWithAllTags([entity, ManagementContextInternal.EFFECTOR_TAG]);
        assertEquals(storedTasks, tasks as Set, "storedTasks="+storedTasks+"; expected="+tasks);
        
        // Then oldest should be GC'ed to leave only maxNumTasks
        List recentTasks = tasks.subList(1, maxNumTasks+1);
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            Set<Task<?>> storedTasks2 = app.getManagementContext().getExecutionManager().getTasksWithAllTags([entity, ManagementContextInternal.EFFECTOR_TAG]);
            assertEquals(storedTasks2, recentTasks as Set, "storedTasks="+storedTasks2+"; expected="+recentTasks);
        }
    }
    
    @Test(groups="Integration")
    public void testEffectorTasksGcedForAge() throws Exception {
        int maxTaskAge = 100;
        int maxOverhead = 250;
        int earlyReturnGrace = 10;
        BrooklynProperties brooklynProperties = new BrooklynProperties();
        brooklynProperties.put(BrooklynGarbageCollector.GC_PERIOD, 1);
        brooklynProperties.put(BrooklynGarbageCollector.MAX_TASK_AGE, maxTaskAge);
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class, Entities.newManagementContext(brooklynProperties));
        TestEntity entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        
        Stopwatch stopwatch = new Stopwatch().start();
        Task<?> oldTask = entity.invoke(TestEntity.MY_EFFECTOR, ImmutableMap.<String,Object>of());
        oldTask.get();
        
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            Set<Task<?>> storedTasks = app.getManagementContext().getExecutionManager().getTasksWithAllTags([entity, ManagementContextInternal.EFFECTOR_TAG]);
            assertEquals(storedTasks, [] as Set, "storedTasks="+storedTasks);
        }

        long timeToGc = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        
        assertTrue(timeToGc > (maxTaskAge-earlyReturnGrace), "timeToGc="+timeToGc+"; maxTaskAge="+maxTaskAge);
        assertTrue(timeToGc < (maxTaskAge+maxOverhead), "timeToGc="+timeToGc+"; maxTaskAge="+maxTaskAge);
    }
    
    private static class BigObject implements Serializable {
        private final int sizeBytes;
        private final byte[] data;
        
        BigObject(int sizeBytes) {
            this.sizeBytes = sizeBytes;
            this.data = new byte[sizeBytes];
        }
        
        public String toString() {
            return "BigObject["+sizeBytes+"]";
        }
    }
}
