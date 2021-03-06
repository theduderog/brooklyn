package brooklyn.entity.proxying;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Entity;
import brooklyn.management.Task;
import brooklyn.policy.Policy;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class BasicEntitySpec<T extends Entity, S extends BasicEntitySpec<T,S>> implements EntitySpec<T> {

    private static final Logger log = LoggerFactory.getLogger(BasicEntitySpec.ConcreteEntitySpec.class);
    
    private static class ConcreteEntitySpec<T extends Entity> extends BasicEntitySpec<T, ConcreteEntitySpec<T>> {
        ConcreteEntitySpec(Class<T> type) {
            super(type);
        }
    }

    public static <T extends Entity> BasicEntitySpec<T, ?> newInstance(Class<T> type) {
        return new ConcreteEntitySpec<T>(type);
    }

    public static <T extends Entity, U extends T> BasicEntitySpec<T, ?> newInstance(Class<T> type, Class<U> implType) {
        return new ConcreteEntitySpec<T>(type).impl(implType);
    }

    private final Class<T> type;
    private String displayName;
    private Class<? extends T> impl;
    private Entity parent;
    private final Map<String, Object> flags = Maps.newLinkedHashMap();
    private final Map<ConfigKey<?>, Object> config = Maps.newLinkedHashMap();
    private final List<Policy> policies = Lists.newArrayList();
    private final Set<Class<?>> additionalInterfaces = Sets.newLinkedHashSet();
    
    public BasicEntitySpec(Class<T> type) {
        this.type = type;
    }
    
    @SuppressWarnings("unchecked")
    protected S self() {
       return (S) this;
    }

    public S displayName(String val) {
        displayName = val;
        return self();
    }

    public S impl(Class<? extends T> val) {
        checkIsImplementation(checkNotNull(val, "impl"));
        checkIsNewStyleImplementation(val);
        impl = val;
        return self();
    }

    public S additionalInterfaces(Class<?>... vals) {
        for (Class<?> val : vals) {
            additionalInterfaces.add(val);
        }
        return self();
    }

    public S additionalInterfaces(Iterable<Class<?>> val) {
        additionalInterfaces.addAll(Sets.newLinkedHashSet(val));
        return self();
    }

    public S parent(Entity val) {
        parent = checkNotNull(val, "parent");
        return self();
    }
    
    public S configure(Map<?,?> val) {
        for (Map.Entry<?, ?> entry: val.entrySet()) {
            if (entry.getKey()==null) throw new NullPointerException("Null key not permitted");
            if (entry.getKey() instanceof CharSequence)
                flags.put(entry.getKey().toString(), entry.getValue());
            else if (entry.getKey() instanceof ConfigKey<?>)
                config.put((ConfigKey<?>)entry.getKey(), entry.getValue());
            else if (entry.getKey() instanceof HasConfigKey<?>)
                config.put(((HasConfigKey<?>)entry.getKey()).getConfigKey(), entry.getValue());
            else {
                log.warn("Spec "+this+" ignoring unknown config key "+entry.getKey());
            }
        }
        return self();
    }
    
    public S configure(CharSequence key, Object val) {
        flags.put(checkNotNull(key, "key").toString(), val);
        return self();
    }
    
    public <V> S configure(ConfigKey<V> key, V val) {
        config.put(checkNotNull(key, "key"), val);
        return self();
    }

    public <V> S configure(ConfigKey<V> key, Task<? extends V> val) {
        config.put(checkNotNull(key, "key"), val);
        return self();
    }

    public <V> S configure(HasConfigKey<V> key, V val) {
        config.put(checkNotNull(key, "key").getConfigKey(), val);
        return self();
    }

    public <V> S configure(HasConfigKey<V> key, Task<? extends V> val) {
        config.put(checkNotNull(key, "key").getConfigKey(), val);
        return self();
    }

    public <V> S policy(Policy val) {
        policies.add(val);
        return self();
    }

    @Override
    public Class<T> getType() {
        return type;
    }
    
    @Override
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public Class<? extends T> getImplementation() {
        return impl;
    }
    
    public Set<Class<?>> getAdditionalInterfaces() {
        return additionalInterfaces;
    }

    @Override
    public Entity getParent() {
        return parent;
    }
    
    @Override
    public Map<String, ?> getFlags() {
        return Collections.unmodifiableMap(flags);
    }
    
    @Override
    public Map<ConfigKey<?>, Object> getConfig() {
        return Collections.unmodifiableMap(config);
    }
        
    @Override
    public List<Policy> getPolicies() {
        return policies;
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("type", type).toString();
    }
    
    // TODO Duplicates method in BasicEntityTypeRegistry
    private void checkIsImplementation(Class<?> val) {
        if (!type.isAssignableFrom(val)) throw new IllegalStateException("Implementation "+val+" does not implement "+type);
        if (val.isInterface()) throw new IllegalStateException("Implementation "+val+" is an interface, but must be a non-abstract class");
        if (Modifier.isAbstract(val.getModifiers())) throw new IllegalStateException("Implementation "+val+" is abstract, but must be a non-abstract class");
    }

    // TODO Duplicates method in BasicEntityTypeRegistry, and InternalEntityFactory.isNewStyleEntity
    private void checkIsNewStyleImplementation(Class<?> implClazz) {
        try {
            implClazz.getConstructor(new Class[0]);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Implementation "+implClazz+" must have a no-argument constructor");
        } catch (SecurityException e) {
            throw Exceptions.propagate(e);
        }
        
        if (implClazz.isInterface()) throw new IllegalStateException("Implementation "+implClazz+" is an interface, but must be a non-abstract class");
        if (Modifier.isAbstract(implClazz.getModifiers())) throw new IllegalStateException("Implementation "+implClazz+" is abstract, but must be a non-abstract class");
    }
}
