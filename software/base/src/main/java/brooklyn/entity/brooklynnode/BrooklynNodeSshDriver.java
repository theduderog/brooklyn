package brooklyn.entity.brooklynnode;

import static java.lang.String.format;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JarBuilder;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.NetworkUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.ssh.CommonCommands;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class BrooklynNodeSshDriver extends JavaSoftwareProcessSshDriver implements BrooklynNodeDriver {
    
    private String expandedInstallDir;

    public BrooklynNodeSshDriver(BrooklynNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public BrooklynNodeImpl getEntity() {
        return (BrooklynNodeImpl) super.getEntity();
    }

    public String getBrooklynHome() {
        return getInstallDir()+"/brooklyn-"+getVersion();
    }

    @Override
    protected String getLogFileLocation() {
        return format("%s/console", getRunDir());
    }
    
    private String getPidFile() {
        return "pid_java";
    }
    
    protected String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }

    @Override
    public void install() {
        String uploadUrl = entity.getConfig(BrooklynNode.DISTRO_UPLOAD_URL);
        DownloadResolver resolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("brooklyn-%s", getVersion()));
        
        newScript("createInstallDir")
                .body.append("mkdir -p "+getInstallDir())
                .failOnNonZeroResultCode()
                .execute();

        List<String> commands = Lists.newArrayList();
        if (uploadUrl != null) {
            // Only upload if not already installed
            boolean exists = newScript("checkIfInstalled")
                    .body.append("cd "+getInstallDir(), "test -f BROOKLYN")
                    .execute() == 0;
            if (!exists) {
                InputStream distroStream = new ResourceUtils(entity).getResourceFromUrl(uploadUrl);
                getMachine().copyTo(distroStream, getInstallDir()+"/"+saveAs);
            }
        } else {
            commands.addAll(CommonCommands.downloadUrlAs(urls, saveAs));
        }
        commands.add(CommonCommands.INSTALL_TAR);
        commands.add("tar xzfv " + saveAs);
        
        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(commands).execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(
                        format("cp -R %s/brooklyn-%s/{bin,conf} .", getInstallDir(), getVersion()),
                        "mkdir -p ./lib/")
                .execute();
        
        SshMachineLocation machine = getMachine();
        BrooklynNode entity = getEntity();
        
        String brooklynPropertiesTempRemotePath = String.format("%s/brooklyn.properties", getRunDir());
        String brooklynPropertiesRemotePath = entity.getConfig(BrooklynNode.BROOKLYN_PROPERTIES_REMOTE_PATH);
        String brooklynPropertiesContents = entity.getConfig(BrooklynNode.BROOKLYN_PROPERTIES_CONTENTS);
        String brooklynPropertiesUri = entity.getConfig(BrooklynNode.BROOKLYN_PROPERTIES_URI);

        // Override the ~/.brooklyn/brooklyn.properties if required
        if (brooklynPropertiesContents != null || brooklynPropertiesUri != null) {
            if (brooklynPropertiesContents != null) {
                machine.copyTo(new ByteArrayInputStream(brooklynPropertiesContents.getBytes()), brooklynPropertiesTempRemotePath);
            } else if (brooklynPropertiesUri != null) {
                InputStream propertiesStream = new ResourceUtils(entity).getResourceFromUrl(brooklynPropertiesUri);
                machine.copyTo(propertiesStream, brooklynPropertiesTempRemotePath);
            }
            newScript(CUSTOMIZING)
                    .body.append(format("cp -p %s %s", brooklynPropertiesTempRemotePath, brooklynPropertiesRemotePath))
                    .execute();
        }

        String brooklynCatalogTempRemotePath = String.format("%s/catalog.xml", getRunDir());
        String brooklynCatalogRemotePath = entity.getConfig(BrooklynNode.BROOKLYN_CATALOG_REMOTE_PATH);
        String brooklynCatalogContents = entity.getConfig(BrooklynNode.BROOKLYN_CATALOG_CONTENTS);
        String brooklynCatalogUri = entity.getConfig(BrooklynNode.BROOKLYN_CATALOG_URI);

        // Override the ~/.brooklyn/catalog.xml if required
        if (brooklynCatalogContents != null || brooklynCatalogUri != null) {
            if (brooklynCatalogContents != null) {
                machine.copyTo(new ByteArrayInputStream(brooklynCatalogContents.getBytes()), brooklynCatalogTempRemotePath);
            } else if (brooklynCatalogUri != null) {
                InputStream catalogStream = new ResourceUtils(entity).getResourceFromUrl(brooklynCatalogUri);
                machine.copyTo(catalogStream, brooklynCatalogTempRemotePath);
            }
            newScript(CUSTOMIZING)
                    .body.append(format("cp -p %s %s", brooklynCatalogTempRemotePath, brooklynCatalogRemotePath))
                    .execute();
        }

        // Copy additional resources to the server
        for (Map.Entry<String,String> entry : getEntity().getAttribute(BrooklynNode.COPY_TO_RUNDIR).entrySet()) {
            Map<String, String> substitutions = ImmutableMap.of("RUN", getRunDir());
            String localResource = entry.getKey();
            String remotePath = entry.getValue();
            String resolvedRemotePath = remotePath;
            for (Map.Entry<String,String> substitution : substitutions.entrySet()) {
                String key = substitution.getKey();
                String val = substitution.getValue();
                resolvedRemotePath = resolvedRemotePath.replace("${"+key+"}", val).replace("$"+key, val);
            }
            machine.copyTo(MutableMap.of("permissions", "0600"), new ResourceUtils(entity).getResourceFromUrl(localResource), resolvedRemotePath);
        }
        
        // TODO Copied from VanillaJavaApp; share code there? Or delete this functionality from here?
        for (String f : getEntity().getClasspath()) {
            // TODO support wildcards

            // If a local folder, then jar it up
            String toinstall;
            if (new File(f).isDirectory()) {
                try {
                    File jarFile = JarBuilder.buildJar(new File(f));
                    toinstall = jarFile.getAbsolutePath();
                } catch (IOException e) {
                    throw new IllegalStateException("Error jarring classpath entry, for directory "+f, e);
                }
            } else {
                toinstall = f;
            }
            
            int result = machine.installTo(new ResourceUtils(entity), toinstall, getRunDir() + "/" + "lib" + "/");
            if (result != 0)
                throw new IllegalStateException(format("unable to install classpath entry %s for %s at %s",f,entity,machine));
            
            // if it's a zip or tgz then expand
            // FIXME dedup with code in machine.installTo above
            String destName = f;
            destName = destName.contains("?") ? destName.substring(0, destName.indexOf('?')) : destName;
            destName = destName.substring(destName.lastIndexOf('/') + 1);

            if (destName.toLowerCase().endsWith(".zip")) {
                result = machine.run(format("cd %s/lib && unzip %s",getRunDir(),destName));
            } else if (destName.toLowerCase().endsWith(".tgz") || destName.toLowerCase().endsWith(".tar.gz")) {
                result = machine.run(format("cd %s/lib && tar xvfz %s",getRunDir(),destName));
            } else if (destName.toLowerCase().endsWith(".tar")) {
                result = machine.run(format("cd %s/lib && tar xvfz %s",getRunDir(),destName));
            }
            if (result != 0)
                throw new IllegalStateException(format("unable to install classpath entry %s for %s at %s (failed to expand archive)",f,entity,machine));
        }
    }

    @Override
    public void launch() {
        String app = getEntity().getAttribute(BrooklynNode.APP);
        String locations = getEntity().getAttribute(BrooklynNode.LOCATIONS);
        Integer httpPort = getEntity().getAttribute(BrooklynNode.HTTP_PORT);
        
        String cmd = "./bin/brooklyn launch";
        if (app != null) {
            cmd += " --app "+app;
        }
        if (locations != null) {
            cmd += " --locations "+locations;
        }
        if (getEntity().isHttpProtocolEnabled("http")) {
            NetworkUtils.checkPortsValid(ImmutableMap.of("httpPort", httpPort));
            cmd += " --port "+httpPort;
        } else if (getEntity().getEnabledHttpProtocols().isEmpty()) {
            cmd += " --noConsole";
        } else {
            throw new IllegalStateException("Unsupported http protocol: "+getEntity().getEnabledHttpProtocols());
        }
        if (getEntity().getAttribute(BrooklynNode.NO_WEB_CONSOLE_AUTHENTICATION)) {
            cmd += " --noConsoleSecurity ";
        }
        cmd += format(" >> %s/console 2>&1 </dev/null &", getRunDir());
        
        log.info("Starting brooklyn on {} using command {}", getMachine(), cmd);
        
        // relies on brooklyn script creating pid file
        newScript(ImmutableMap.of("usePidFile", false), LAUNCHING).
            body.append(
                format("export BROOKLYN_CLASSPATH=%s", getRunDir()+"/lib/\"*\""),
                format("export BROOKLYN_HOME=%s", getBrooklynHome()),
                format(cmd)
        ).execute();
    }

    @Override
    public boolean isRunning() {
        Map<String,String> flags = ImmutableMap.of("usePidFile", getPidFile());
        int result = newScript(flags, CHECK_RUNNING).execute();
        return result == 0;
    }

    @Override
    public void stop() {
        Map<String,String> flags = ImmutableMap.of("usePidFile", getPidFile());
        newScript(flags, STOPPING).execute();
    }

    @Override
    public void kill() {
        Map<String,String> flags = ImmutableMap.of("usePidFile", getPidFile());
        newScript(flags, KILLING).execute();
    }
    
    @Override
    public Map<String, String> getShellEnvironment() {
        Map<String, String> orig = super.getShellEnvironment();
        String origClasspath = orig.get("CLASSPATH");
        String newClasspath = (origClasspath == null ? "" : origClasspath+":") + 
                getRunDir()+"/conf/" + ":" +
                getRunDir()+"/lib/\"*\"";
        Map<String,String> results = new LinkedHashMap<String,String>();
        results.putAll(orig);
        results.put("BROOKLYN_CLASSPATH", newClasspath);
        results.put("BROOKLYN_HOME", getBrooklynHome());
        results.put("RUN", getRunDir());
        return results;
    }
}
