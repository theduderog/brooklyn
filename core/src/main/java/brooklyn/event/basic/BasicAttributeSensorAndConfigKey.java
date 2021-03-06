package brooklyn.event.basic;

import brooklyn.entity.Entity;

/**
 * A {@link Sensor} describing an attribute that can be configured with a default value.
 *
 * The {@link ConfigKey} has the same type, name and description as the sensor,
 * and is typically used to populate the sensor's value at runtime.
 */
public class BasicAttributeSensorAndConfigKey<T> extends AttributeSensorAndConfigKey<T,T> {
    
    public BasicAttributeSensorAndConfigKey(Class<T> type, String name) {
        this(type, name, name, null);
    }
    public BasicAttributeSensorAndConfigKey(Class<T> type, String name, String description) {
        this(type, name, description, null);
    }

    public BasicAttributeSensorAndConfigKey(Class<T> type, String name, String description, T defaultValue) {
        super(type, type, name, description, defaultValue);
    }

    public BasicAttributeSensorAndConfigKey(BasicAttributeSensorAndConfigKey<T> orig, T defaultValue) {
        super(orig, defaultValue);
    }
    
    protected T convertConfigToSensor(T value, Entity entity) { return value; }
    
    public static class StringAttributeSensorAndConfigKey extends BasicAttributeSensorAndConfigKey<String> {

        public StringAttributeSensorAndConfigKey(BasicAttributeSensorAndConfigKey<String> orig, String defaultValue) {
            super(orig, defaultValue);
        }

        public StringAttributeSensorAndConfigKey(String name, String description, String defaultValue) {
            super(String.class, name, description, defaultValue);
        }

        public StringAttributeSensorAndConfigKey(String name, String description) {
            super(String.class, name, description);
        }

        public StringAttributeSensorAndConfigKey(String name) {
            super(String.class, name);
        }
        
    }
    
    public static class IntegerAttributeSensorAndConfigKey extends BasicAttributeSensorAndConfigKey<Integer> {

        public IntegerAttributeSensorAndConfigKey(BasicAttributeSensorAndConfigKey<Integer> orig, Integer defaultValue) {
            super(orig, defaultValue);
        }

        public IntegerAttributeSensorAndConfigKey(String name, String description, Integer defaultValue) {
            super(Integer.class, name, description, defaultValue);
        }

        public IntegerAttributeSensorAndConfigKey(String name, String description) {
            super(Integer.class, name, description);
        }

        public IntegerAttributeSensorAndConfigKey(String name) {
            super(Integer.class, name);
        }
        
    }

}
