package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.util.Collections;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class RebindSshMachineLocationTest {

    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext managementContext;
    private TestApplication origApp;
    private SshMachineLocation origLoc;
    private File mementoDir;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        managementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        origApp = ApplicationBuilder.newManagedApp(EntitySpecs.spec(TestApplication.class), managementContext);
        origLoc = new SshMachineLocation(MutableMap.of("address", "localhost"));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }
    
    @Test(groups="Integration")
    public void testMachineUsableAfterRebind() throws Exception {
        origApp.start(ImmutableList.of(origLoc));

        assertEquals(origLoc.execScript(Collections.<String,Object>emptyMap(), "mysummary", ImmutableList.of("true")), 0);

        TestApplication newApp = (TestApplication) rebind();
        SshMachineLocation newLoc = (SshMachineLocation) Iterables.getOnlyElement(newApp.getLocations(), 0);
        
        assertEquals(newLoc.execScript(Collections.<String,Object>emptyMap(), "mysummary", ImmutableList.of("true")), 0);
    }
    
    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
}
