package brooklyn.location.basic;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.util.ResourceUtils;
import brooklyn.util.config.ConfigBag;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class LocationConfigUtils {

    private static final Logger log = LoggerFactory.getLogger(LocationConfigUtils.class);
        
    public static String getKeyData(ConfigBag config, ConfigKey<String> dataKey, ConfigKey<String> fileKey) {
        boolean unused = config.isUnused(dataKey);
        String data = config.get(dataKey);
        if (groovyTruth(data) && !unused) return data;

        String file = config.get(fileKey);
        if (groovyTruth(file)) {
            String fileTidied = ResourceUtils.tidyFilePath(file);
            try {
                String fileData = Files.toString(new File(fileTidied), Charsets.UTF_8);
                if (groovyTruth(data)) {
                    if (!fileData.trim().equals(data.trim()))
                        log.warn(dataKey.getName()+" and "+fileKey.getName()+" both specified; preferring the former");
                } else {
                    data = fileData;
                    config.put(dataKey, data);
                    config.get(dataKey);
                }
            } catch (IOException e) {
                log.warn("Invalid file for "+fileKey+" (value "+file+
                        (fileTidied.equals(file) ? "" : "; converted to "+fileTidied)+
                        "); may fail provisioning "+config.getDescription());
            }
        }
        return data;
    }
    
    public static String getPrivateKeyData(ConfigBag config) {
        return getKeyData(config, LocationConfigKeys.PRIVATE_KEY_DATA, LocationConfigKeys.PRIVATE_KEY_FILE);
        
        // used to also check:
        // "sshPrivateKey"
    }
    
    public static String getPublicKeyData(ConfigBag config) {
        String data = getKeyData(config, LocationConfigKeys.PUBLIC_KEY_DATA, LocationConfigKeys.PUBLIC_KEY_FILE);
        if (groovyTruth(data)) return data;
        
        String privateKeyFile = config.get(LocationConfigKeys.PRIVATE_KEY_FILE);
        if (groovyTruth(privateKeyFile)) {
            File f = new File(privateKeyFile+".pub");
            if (f.exists()) {
                log.debug("Trying to load "+LocationConfigKeys.PUBLIC_KEY_DATA.getName()+" from "+LocationConfigKeys.PRIVATE_KEY_FILE.getName() + " " + f.getAbsolutePath()+" for "+config.getDescription());
                try {
                    data = Files.toString(f, Charsets.UTF_8);
                    config.put(LocationConfigKeys.PUBLIC_KEY_DATA, data);
                    if (log.isDebugEnabled())
                        log.debug("Loaded public key "+LocationConfigKeys.PUBLIC_KEY_DATA.getName()+" from "+LocationConfigKeys.PRIVATE_KEY_FILE.getName() + " " + f.getAbsolutePath()+" for "+config.getDescription()+": "+data);
                    return data;
                } catch (IOException e) {
                    log.debug("Not able to load "+f.getAbsolutePath()+" for "+config.getDescription());
                }
            }
        }
        
        // used to also check:
        // "sshPublicKey"

        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getConfigCheckingDeprecatedAlternatives(ConfigBag configBag, ConfigKey<T> preferredKey,
            ConfigKey<?> ...deprecatedKeys) {
        T value = null;
        if (configBag.containsKey(preferredKey))
            value = configBag.get(preferredKey);
        for (ConfigKey<?> deprecatedKey: deprecatedKeys) {
            T altValue = null;
            if (configBag.containsKey(deprecatedKey))
                altValue = (T) configBag.get(deprecatedKey);
            if (altValue!=null) {
                if (value!=null) {
                    if (value.equals(altValue)) {
                        // fine -- nothing
                    } else {
                        log.warn("Detected deprecated key "+deprecatedKey+" with value "+altValue+" used in addition to "+preferredKey+" " +
                        		"with value "+value+" for "+configBag.getDescription()+"; ignoring");
                        configBag.remove(deprecatedKey);
                    }
                } else {
                    log.warn("Detected deprecated key "+deprecatedKey+" with value "+altValue+" used instead of recommended "+preferredKey+"; " +
                            "promoting to preferred key status");
                    configBag.put(preferredKey, altValue);
                    configBag.remove(deprecatedKey);
                    value = altValue;
                }
            }
        }
        if (value==null)
            value = configBag.get(preferredKey);
        return value;
    }

}
