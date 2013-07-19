package brooklyn.entity.messaging.storm;

import java.util.Collection;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.EntityType;
import brooklyn.entity.Group;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.mementos.EntityMemento;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

public class StormServerImpl extends SoftwareProcessImpl implements StormServer {

	@Override
	public Class getDriverInterface() {
		return StormDriver.class;
	}



}
