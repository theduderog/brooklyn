package brooklyn.entity.basic.lifecycle;

import java.util.*;

import static java.lang.String.format;

public class CommonCommands {

    /**
     * Returns a string for checking whether the given executable is available,
     * and installing it if necessary.
     * <p/>
     * Uses {@link #installPackage} and accepts the same flags e.g. for apt, yum, rpm.
     */
    public static String installExecutable(Map flags, String executable) {
        return missing(executable, installPackage(flags, executable));
    }

    public static String installExecutable(String executable) {
        return installExecutable(new HashMap(), executable);
    }

    /**
     * Returns a command with all output redirected to /dev/null
     */
    public static String quiet(String command) {
        return format("(%s > /dev/null 2>&1)", command);
    }

    /**
     * Returns a command that always exits successfully
     */
    public static String ok(String command) {
        return String.format("(%s || true)", command);
    }

    /**
     * Returns a command for safely running as root, using {@code sudo}.
     * <p/>
     * Ensuring non-blocking if password not set by using {@code -S} which reads
     * from stdin routed to {@code /dev/null} and {@code -E} passes the parent
     * environment in. If already root, simplem runs the command.
     * <p/>
     * The command is not escapped in any ways. If you are using single quotes
     * you need to escape them.
     */
    public static String sudo(String command) {
        return format("(test $UID -eq 0 && %s || sudo -E -n -s -- \'%s\')", command, command);
    }

    /**
     * Returns a command that runs only 1f the operating system is as specified; Checks {@code /etc/issue} for the specified name
     */
    public static String on(String osName, String command) {
        return format("(grep \"%s\" /etc/issue && %s)", osName, command);
    }

    /**
     * Returns a command that runs only if the specified executable is in the path
     */
    public static String file(String path, String command) {
        return format("(test -f %s && %s)", path, command);
    }

    /**
     * Returns a command that runs only if the specified executable is in the path
     */
    public static String exists(String executable, String command) {
        return format("(which %s && %s)", executable, command);
    }

    /**
     * Returns a command that runs only if the specified executable is NOT in the path
     */
    public static String missing(String executable, String command) {
        return format("(which %s || %s)", executable, command);
    }

    /**
     * Returns a sequence of chained commands that runs until one of them fails
     */
    public static String chain(Collection<String> commands) {
        return "(" + join(commands, " && ") + ")";
    }

    /**
     * Returns a sequence of alternative commands that runs until one of the commands succeeds
     */
    public static String alternatives(Collection<String> commands, String failure) {
        return format("(%s || %s)", join(commands, " || "), failure);
    }

    private static String join(Collection<String> c, String separator) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = c.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(separator);
            }
        }
        return sb.toString();
    }

    /**
     * Returns a command for installing the given package.
     * <p/>
     * Flags can contain common overrides for deb, apt, yum, rpm and port
     * as the package names can be different for each of those:
     * <pre>
     * installPackage("libssl-devel", yum:"openssl-devel", apt:"openssl libssl-dev zlib1g-dev")
     * </pre>
     */
    public static String installPackage(Map flags, String packageDefaultName) {
        List<String> commands = new LinkedList<String>();
        commands.add(exists("dpkg", sudo(format("dpkg -i %s", getFlag(flags, "deb", packageDefaultName)))));
        commands.add(exists("apt-get", sudo(format("apt-get update && apt-get install -y %s", getFlag(flags, "apt", packageDefaultName)))));
        commands.add(exists("yum", sudo(format("yum -y install %s", getFlag(flags, "yum", packageDefaultName)))));
        commands.add(exists("rpm", sudo(format("rpm -i %s", getFlag(flags, "rpm", packageDefaultName)))));
        commands.add(exists("port", sudo(format("port install %s", getFlag(flags, "port", packageDefaultName)))));
        String failure = format("(echo \"WARNING: no known/successful package manager to install %s, may fail subsequently\")",packageDefaultName);
        return alternatives(commands, failure);
    }

    public static String installPackage(String packageDefaultName) {
        return installPackage(new HashMap(), packageDefaultName);
    }

    public static final String INSTALL_TAR = installExecutable("tar");
    public static final String INSTALL_CURL = installExecutable("curl");
    public static final String INSTALL_WGET = installExecutable("wget");

    /**
     * Returns command for downloading from a url and saving to a file;
     * currently using {@code curl}.
     * <p/>
     * Will read from a local repository, if files have been copied there
     * ({@code cp -r /tmp/brooklyn/installs/ ~/.brooklyn/repository/})
     * unless <em>skipLocalrepo</em> is {@literal true}.
     * <p/>
     * Ideally use a blobstore staging area.
     */
    public static List<String> downloadUrlAs(Map flags, String url, String entityVersionPath, String pathlessFilenameToSaveAs) {
        Boolean skipLocalRepo = (Boolean) flags.get("skipLocalRepo");
        boolean useLocalRepo =  skipLocalRepo!=null?!skipLocalRepo:true;

        String command = format("curl -L \"%s\" -o %s", url, pathlessFilenameToSaveAs);
        if (useLocalRepo) {
            String file = format("$HOME/.brooklyn/repository/%s/%s", entityVersionPath, pathlessFilenameToSaveAs);
            command = format("if [ -f %s ]; then cp %s ./%s; else %s ; fi", file, file, pathlessFilenameToSaveAs, command);
        }
        command = command + " || exit 9";
        return Arrays.asList(INSTALL_CURL, command);
    }

    private static Object getFlag(Map flags, String flagName, Object defaultValue) {
        Object found = flags.get(flagName);
        return found == null ? defaultValue : found;
    }

    public static List<String> downloadUrlAs(String url, String entityVersionPath, String pathlessFilenameToSaveAs) {
        return downloadUrlAs(new HashMap(), url, entityVersionPath, pathlessFilenameToSaveAs);
    }
}