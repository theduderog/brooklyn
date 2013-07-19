package brooklyn.entity.messaging.storm;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(StormServerImpl.class)
public interface StormServer extends Entity, SoftwareProcess {

	ConfigKey<String> ZOOKEEPER = ConfigKeys.newStringConfigKey("storm.zookeeper", "ZooKeeper nodes", "999.999.999.999");
	ConfigKey<String> NIMBUS_HOST = ConfigKeys.newStringConfigKey("storm.zookeeper", "ZooKeeper nodes", "999.999.999.999");
	ConfigKey<String> ZOOKEEPER = ConfigKeys.newStringConfigKey("storm.zookeeper", "ZooKeeper nodes", "999.999.999.999");

}
