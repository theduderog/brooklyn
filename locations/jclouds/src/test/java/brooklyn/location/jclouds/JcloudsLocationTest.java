package brooklyn.location.jclouds;

import static org.testng.Assert.assertTrue;

import java.util.Map;

import javax.annotation.Nullable;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.Template;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Predicate;

/**
 * @author Shane Witbeck
 */
public class JcloudsLocationTest implements JcloudsLocationConfig {

    public static final RuntimeException BAIL_OUT_FOR_TESTING = 
            new RuntimeException("early termination for test");
    
    public static class BailOutJcloudsLocation extends JcloudsLocation {
       ConfigBag lastConfigBag;

       public BailOutJcloudsLocation() {
          super();
       }
       
       public BailOutJcloudsLocation(Map<?, ?> conf) {
            super(conf);
        }
        
        
        @Override
        protected Template buildTemplate(ComputeService computeService, ConfigBag config) {
            lastConfigBag = config;
            throw BAIL_OUT_FOR_TESTING;
        }
        protected synchronized void tryObtainAndCheck(Map<?,?> flags, Predicate<ConfigBag> test) {
            try {
                obtain(flags);
            } catch (Throwable e) {
                if (e==BAIL_OUT_FOR_TESTING) {
                    test.apply(lastConfigBag);
                } else {
                    throw Exceptions.propagate(e);
                }
            }
        }
    }
    
    public static BailOutJcloudsLocation newSampleBailOutJcloudsLocationForTesting() {
        BailOutJcloudsLocation jcl = new BailOutJcloudsLocation(MutableMap.of(
                CLOUD_PROVIDER, "aws-ec2",
                ACCESS_IDENTITY, "bogus",
                ACCESS_CREDENTIAL, "bogus",
                USER, "fred",
                MIN_RAM, 16));
        return jcl;
    }
    
    public static Predicate<ConfigBag> checkerFor(final String user, final Integer minRam, final Integer minCores) {
        return new Predicate<ConfigBag>() {
            @Override
            public boolean apply(@Nullable ConfigBag input) {
                Assert.assertEquals(input.get(USER), user);
                Assert.assertEquals(input.get(MIN_RAM), minRam);
                Assert.assertEquals(input.get(MIN_CORES), minCores);
                return true;
            }
        };
    }
    
    @Test
    public void testCreateWithFlagsDirectly() throws Exception {
        BailOutJcloudsLocation jcl = newSampleBailOutJcloudsLocationForTesting();
        jcl.tryObtainAndCheck(MutableMap.of(MIN_CORES, 2), checkerFor("fred", 16, 2));
    }

    @Test
    public void testCreateWithFlagsDirectlyAndOverride() throws Exception {
        BailOutJcloudsLocation jcl = newSampleBailOutJcloudsLocationForTesting();
        jcl.tryObtainAndCheck(MutableMap.of(MIN_CORES, 2, MIN_RAM, 8), checkerFor("fred", 8, 2));
    }

    @Test
    public void testCreateWithFlagsSubLocation() throws Exception {
        BailOutJcloudsLocation jcl = newSampleBailOutJcloudsLocationForTesting();
        jcl = (BailOutJcloudsLocation) jcl.newSubLocation(MutableMap.of(USER, "jon", MIN_CORES, 2));
        jcl.tryObtainAndCheck(MutableMap.of(MIN_CORES, 3), checkerFor("jon", 16, 3));
    }


    // TODO more tests, where flags come in from resolver, named locations, etc
}
