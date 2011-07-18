package brooklyn.entity.messaging.qpid

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.JavaApp;
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedJavaAppSetup;
import brooklyn.util.SshBasedJavaWebAppSetup

/**
 * Start a {@link QpidBroker} in a {@link Location} accessible over ssh.
 */
public class QpidSetup extends SshBasedJavaAppSetup {
    public static final String DEFAULT_VERSION = "0.10"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"qpid"
    public static final int DEFAULT_FIRST_AMQP_PORT = 5672

    private int amqpPort
    private int rmiPort

    public static QpidSetup newInstance(QpidBroker entity, SshMachineLocation machine) {
        Integer suggestedVersion = entity.getConfig(QpidBroker.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(QpidBroker.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(QpidBroker.SUGGESTED_RUN_DIR)
        Integer suggestedJmxPort = entity.getConfig(QpidBroker.SUGGESTED_JMX_PORT)
        String suggestedJmxHost = entity.getConfig(QpidBroker.SUGGESTED_JMX_HOST)
        Integer suggestedAmqpPort = entity.getConfig(QpidBroker.SUGGESTED_AMQP_PORT)

        String version = suggestedVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: (DEFAULT_INSTALL_DIR+"/"+"${version}"+"/"+"qpid-broker-${version}")
        String runDir = suggestedRunDir ?: (BROOKLYN_HOME_DIR+"/"+"${entity.application.id}"+"/"+"qpid-${entity.id}")
        String jmxHost = suggestedJmxHost ?: machine.getAddress().getHostName()
        int jmxPort = machine.obtainPort(toDesiredPortRange(suggestedJmxPort, DEFAULT_FIRST_JMX_PORT))
        int rmiPort = machine.obtainPort(toDesiredPortRange(jmxPort - 100))
        int amqpPort = machine.obtainPort(toDesiredPortRange(suggestedAmqpPort, DEFAULT_FIRST_AMQP_PORT))

        QpidSetup result = new QpidSetup(entity, machine)
        result.setRmiPort(rmiPort)
        result.setJmxPort(jmxPort)
        result.setJmxHost(jmxHost)
        result.setAmqpPort(amqpPort)
        result.setVersion(version)
        result.setInstallDir(installDir)
        result.setRunDir(runDir)

        return result
    }

    public QpidSetup(QpidBroker entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public QpidSetup setAmqpPort(int val) {
        amqpPort = val
        return this
    }

    public QpidSetup setRmiPort(int val) {
        rmiPort = val
        return this
    }

    /** JMX is configured using command line switch. */
    @Override
    protected Map getJmxConfigOptions() { [:] }

    @Override
    protected void postStart() {
        entity.setAttribute(Attributes.JMX_PORT, jmxPort)
        entity.setAttribute(Attributes.JMX_HOST, jmxHost)
        entity.setAttribute(Attributes.AMQP_PORT, amqpPort)
        entity.setAttribute(Attributes.VERSION, version)
    }

    @Override
    public List<String> getInstallScript() {
        makeInstallScript([
                "wget http://download.nextag.com/apache/qpid/${version}/qpid-java-broker-${version}.tar.gz",
                "tar xvzf qpid-java-broker-${version}.tar.gz",
            ])
    }

    /**
     * Creates the directories Qpid needs to run in a different location from where it is installed.
     */
    public List<String> getRunScript() {
        List<String> script = [
            "cd ${runDir}",
			"nohup ./bin/qpid-server -m ${jmxPort} -p ${amqpPort} --exclude-0-10 ${amqpPort} &",
        ]
        return script
    }

    public Map<String, String> getRunEnvironment() {
        Map<String, String> env = [
			"QPID_HOME" : "${runDir}",
			"QPID_WORK" : "${runDir}",
			"QPID_OPTS" : toJavaDefinesString(getJvmStartupProperties()),
        ]
        return env
    }

    /** @see SshBasedJavaAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() {
       return makeCheckRunningScript("qpid", "qpid-server.pid")
    }

    @Override
    public List<String> getConfigScript() {
        List<String> script = [
            "mkdir -p ${runDir}",
            "cd ${runDir}",
            "cp -R ${installDir}/{bin,etc,lib} .",
        ]
        return script
    }

    @Override
    public List<String> getRestartScript() {
       return makeRestartScript("qpid-server.pid")
    }

    @Override
    public List<String> getShutdownScript() {
       return makeShutdownScript("qpid-server.pid")
    }

    @Override
    protected void postShutdown() {
        machine.releasePort(rmiPort)
        machine.releasePort(jmxPort)
        machine.releasePort(amqpPort);
    }
}