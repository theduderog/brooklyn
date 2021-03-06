/*
 * Copyright 2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.zookeeper;

import java.util.concurrent.TimeUnit;

import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;

import com.google.common.base.Functions;
import com.google.common.base.Objects.ToStringHelper;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Apache Zookeeper instance.
 */
public abstract class AbstractZookeeperImpl extends SoftwareProcessImpl implements Zookeeper {

    private static final Logger log = LoggerFactory.getLogger(AbstractZookeeperImpl.class);
    private static final ObjectName ZOOKEEPER_MBEAN = JmxHelper.createObjectName("org.apache.ZooKeeperService:name0=StandaloneServer_port-1");

    private volatile JmxFeed jmxFeed;

    public AbstractZookeeperImpl() {
    }

    @Override
    public Integer getZookeeperPort() { return getAttribute(ZOOKEEPER_PORT); }

    @Override
    public String getHostname() { return getAttribute(HOSTNAME); }

    @Override
    public void waitForServiceUp(long duration, TimeUnit units) {
        super.waitForServiceUp(duration, units);

        // Wait for the MBean to exist
        JmxHelper helper = new JmxHelper(this);
        try {
            helper.assertMBeanExistsEventually(ZOOKEEPER_MBEAN, units.toMillis(duration));
        } finally {
            helper.disconnect();
        }
    }

    @Override
    protected void connectSensors() {
        connectServiceUpIsRunning();

        jmxFeed = JmxFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .pollAttribute(new JmxAttributePollConfig<Long>(OUTSTANDING_REQUESTS)
                        .objectName(ZOOKEEPER_MBEAN)
                        .attributeName("OutstandingRequests")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(PACKETS_RECEIVED)
                        .objectName(ZOOKEEPER_MBEAN)
                        .attributeName("PacketsReceived")
                        .onError(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(PACKETS_SENT)
                        .objectName(ZOOKEEPER_MBEAN)
                        .attributeName("PacketsSent")
                        .onError(Functions.constant(-1l)))
                .build();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
        if (jmxFeed != null) jmxFeed.stop();
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("zookeeperPort", getZookeeperPort());
    }

}
