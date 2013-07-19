package brooklyn.entity.messaging.storm;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.ssh.CommonCommands;

public class StormSshDriver extends AbstractSoftwareProcessSshDriver implements
		StormDriver {

	public StormSshDriver(EntityLocal entity, SshMachineLocation machine) {
		super(entity, machine);
	}

	@Override
	public boolean isRunning() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub

	}

	@Override
	public void install() {
		newScript(INSTALLING).body.append(
				CommonCommands.INSTALL_WGET,
				CommonCommands.INSTALL_TAR,
				CommonCommands.installPackage(MutableMap.of("yum", "gcc gcc-c++ uuid uuid-devel libuuid-devel make python-2.6.6 unzip"
						//, TODO apt?
						), "gcc gcc-c++ make python-2.6.6"),
				"wget http://download.zeromq.org/zeromq-2.1.7.tar.gz",
				"tar zxf zeromq-2.1.7.tar.gz",
				"pushd zeromq-2.1.7",
				"./configure",
				"make",
				CommonCommands.sudo("make install"), //TODO - install in private dir
				"popd",
				CommonCommands.installPackage("git"),
				"git clone https://github.com/nathanmarz/jzmq.git",
				"pushd jzmq",
				"./autogen.sh",
				"./configure",
				"make",
				CommonCommands.sudo("make install"), //TODO - install in private dir
				"popd",
				"wget https://github.com/downloads/nathanmarz/storm/storm-0.8.1.zip"
				)
				.execute();
		copyResource("classpath://brooklyn/entity/messaging/storm/storm-recipe.rb", getInstallDir());
	}

	@Override
	public void customize() {
		newScript(CUSTOMIZING).body.append(
				"unzip " + getInstallDir() + "/storm-0.8.1.zip",
				"cd storm-0.8.1",
				"cat >> conf/storm.yaml <<EOF\nstorm.zookeeper.servers:\n - \"" + getEntity().getConfig(StormServer.ZOOKEEPER) + "\"\nEOF",
				"echo 'storm.local.dir: \"" + getRunDir() + "/runtime\"' >> conf/storm.yaml",
				"echo 'nimbus.host: \"" + getEntity().getConfig(StormServer.NIMBUS_HOST) + "\"' >> conf/storm.yaml",
				"cat >> conf/storm.yaml <<EOF\nsupervisor.slots.ports:\n - \"" + getEntity().getConfig(StormServer.SUPERVISOR_PORT) + "\"\nEOF"
				)
				.execute();

	}

	@Override
	public void launch() {
		// TODO Auto-generated method stub

	}

}
