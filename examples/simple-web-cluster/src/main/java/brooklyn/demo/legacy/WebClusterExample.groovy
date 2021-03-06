package brooklyn.demo.legacy

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.proxy.nginx.NginxControllerImpl
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.ControlledDynamicWebAppClusterImpl
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory
import brooklyn.launcher.BrooklynLauncher
import brooklyn.policy.autoscaling.AutoScalerPolicy
import brooklyn.util.CommandLineUtil

import com.google.common.collect.Lists

/**
 * Launches a clustered and load-balanced set of web servers.
 * Demonstrates syntax, so many of the options used here are the defaults.
 * (So the class could be much simpler, as in WebClusterExampleAlt.)
 * <p>
 * Requires: 
 * -Xmx512m -Xms128m -XX:MaxPermSize=256m
 * and brooklyn-all jar, and this jar or classes dir, on classpath.
 * 
 * @deprecated in 0.5; see {@link brooklyn.demo.WebClusterExample}
 */
@Deprecated
public class WebClusterExample extends AbstractApplication {
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterExample)
    
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()

    public static final String WAR_PATH = "classpath://hello-world-webapp.war"
    
    public WebClusterExample(Map props=[:]) {
        super(props)
    }
    

    NginxController nginxController = new NginxControllerImpl(this,
//        domain: 'webclusterexample.brooklyn.local',
        port:"8000+")
    
    JBoss7ServerFactory jbossFactory = new JBoss7ServerFactory(httpPort: "8080+", war: WAR_PATH); 

    ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppClusterImpl(this,
        name: "WebApp cluster",
        controller: nginxController,
        initialSize: 1,
        factory: jbossFactory)
    
    AutoScalerPolicy policy = AutoScalerPolicy.builder()
            .metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_LAST_PER_NODE)
            .sizeRange(1, 5)
            .metricRange(10, 100)
            .build();
    

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "localhost");
        WebClusterExample app = new WebClusterExample(name:'Brooklyn WebApp Cluster example')
        
        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(app)
                .webconsolePort(port)
                .location(location)
                .start();
         
        app.web.cluster.addPolicy(app.policy)
        Entities.dumpInfo(launcher.getApplications());
    }
}
