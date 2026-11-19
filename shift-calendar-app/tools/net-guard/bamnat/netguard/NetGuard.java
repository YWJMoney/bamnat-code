package bamnat.netguard;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 2.0.0 [RVR-42] (총괄 HQ-116): the app's unit and screenshot tests never reach a server outside this computer.
 *
 * The debug build carries the real server addresses (gradle.properties), so the Robolectric tests that build the app's
 * container called api.bamnat.app, s.bamnat.app and the public files for real: tens of thousands of requests a day in the
 * live server's statistics, and the observe page's 'visits dropped' signal fired when the threads stopped.
 *
 * Loaded as a Java agent into every test JVM (app/build.gradle.kts, before any test class), it becomes the JVM's
 * {@link ProxySelector}. Every HTTP(S) request and every plain socket asks it first; for any host that is not this
 * computer (localhost, 127.x, ::1) or a Maven repository Robolectric fetches its Android jars from, it refuses with a
 * {@link ConnectException} before a connection is made, as a phone with no network would see it. The address is written to
 * {@code bamnat.netGuard.log} (one file per test JVM) so CI can show what the tests tried.
 * The server tests that need a server (tools/server_app_tests.py) run one on 127.0.0.1, which stays allowed.
 *
 * Plain Java on purpose: the agent starts before Kotlin's library is on the class path.
 */
public final class NetGuard extends ProxySelector {
    /** The words in every refusal, so a test can tell the guard's refusal from a real one. */
    public static final String REFUSED = "bamnat net guard: no outside network in tests";

    /**
     * Where Robolectric fetches its Android jars (android-all-instrumented) on a machine that has not cached them: Maven
     * Central and the mirrors it redirects to (repo1.maven.org → gcp.mirrors.maven.org on GitHub's runners, so every
     * *.maven.org), and Google's Maven. A host here also matches its subdomains.
     */
    private static final List<String> MAVEN = Arrays.asList("maven.org", "repo.maven.apache.org", "maven.google.com", "dl.google.com");

    private final ProxySelector before;
    private final List<String> allowed = new ArrayList<>(MAVEN);
    private final Path log;

    NetGuard(ProxySelector before) {
        this.before = before;
        String repo = System.getProperty("robolectric.dependency.repo.url");
        if (repo != null) try { String h = URI.create(repo).getHost(); if (h != null) allowed.add(h.toLowerCase(Locale.ROOT)); } catch (RuntimeException ignored) { }
        String more = System.getProperty("bamnat.netGuard.allow");
        if (more != null) for (String h : more.split(",")) if (!h.trim().isEmpty()) allowed.add(h.trim().toLowerCase(Locale.ROOT));
        String dir = System.getProperty("bamnat.netGuard.log");
        this.log = dir == null ? null : Paths.get(dir, "blocked-" + ProcessHandle.current().pid() + ".txt");
    }

    public static void premain(String args, Instrumentation inst) { install(); }

    /** Puts the guard in front of the JVM's proxy choice (the one from -Dhttps.proxyHost and the like stays behind it). */
    public static synchronized void install() {
        ProxySelector now = ProxySelector.getDefault();
        if (!(now instanceof NetGuard)) ProxySelector.setDefault(new NetGuard(now));
    }

    /** Whether the guard is on in this JVM. */
    public static boolean on() { return ProxySelector.getDefault() instanceof NetGuard; }

    static boolean local(String host) {
        if (host == null || host.isEmpty()) return true;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.equals("0.0.0.0") || h.equals("::") || h.equals("::1")) return true;
        if (h.matches("[0-9.]+") || h.indexOf(':') >= 0) {
            try { InetAddress a = InetAddress.getByName(h); return a.isLoopbackAddress() || a.isAnyLocalAddress(); } catch (IOException e) { return false; }
        }
        return false;
    }

    boolean allowed(String host) {
        if (local(host)) return true;
        String h = host.toLowerCase(Locale.ROOT);
        for (String a : allowed) if (h.equals(a) || h.endsWith("." + a)) return true;
        return false;
    }

    @Override
    public List<Proxy> select(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            // socket://name:port with a name URI cannot parse into a host keeps it in the authority.
            String auth = uri.getRawAuthority();
            host = auth == null ? null : auth.replaceFirst(":[0-9]+$", "");
        }
        if (allowed(host)) return before != null ? before.select(uri) : List.of(Proxy.NO_PROXY);
        note(uri.getScheme() + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + (uri.getRawPath() == null ? "" : uri.getRawPath()));
        // Thrown unchecked-but-checked: the callers (HttpURLConnection.connect, Socket.connect) declare IOException, so the
        // app's code sees the same ConnectException as on a phone with no network.
        throw NetGuard.<RuntimeException>sneaky(new ConnectException(REFUSED + " (" + host + ")"));
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        if (before != null) before.connectFailed(uri, sa, ioe);
    }

    private synchronized void note(String what) {
        if (log == null) return;
        try {
            Files.createDirectories(log.getParent());
            Files.write(log, (what + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | UncheckedIOException ignored) { }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneaky(Throwable t) throws T { throw (T) t; }
}
