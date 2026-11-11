package ohi.andre.consolelauncher.managers;

import android.content.Context;
import android.os.Build;
import android.system.Os;

import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.tukaani.xz.XZInputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * APT Package Resolver implementing standard Debian/Termux repository index fetching,
 * package parsing (Packages.gz / Packages.xz), recursive dependency resolution,
 * resilient .deb extraction with symlink fallback copying, and launcher shims.
 */
public class AptPackageResolver {

    public static final String APT_MAIN_REPO_BASE = "https://packages.termux.dev/apt/termux-main/";
    public static final String APT_FALLBACK_REPO_BASE = "https://packages.termux.org/apt/termux-main/";

    private static final Map<String, AptEntry> sParsedIndex = new ConcurrentHashMap<>();

    public static class AptEntry {
        public String packageName;
        public String version;
        public String filename;
        public String downloadUrl;
        public String sha256;
        public long size;
        public String architecture;
        public String description;
        public List<String> dependencies = new ArrayList<>();
    }

    public interface IndexCallback {
        void onSuccess(File indexFile);
        void onError(String error);
    }

    public interface ResolveCallback {
        void onFound(AptEntry entry);
        void onNotFound(String message);
        void onError(String error);
    }

    public interface ExtractCallback {
        void onProgress(String message);
        void onSuccess(File binaryFile, List<String> installedFiles);
        void onError(String error);
    }

    public static String getTermuxArch() {
        String primaryAbi = PackageManager.getPrimaryAbi().toLowerCase();
        if (primaryAbi.contains("arm64") || primaryAbi.contains("aarch64")) {
            return "aarch64";
        } else if (primaryAbi.contains("v7") || primaryAbi.contains("arm")) {
            return "arm";
        } else if (primaryAbi.contains("64")) {
            return "x86_64";
        } else {
            return "i686";
        }
    }

    /**
     * Synchronously fetches and decompresses the Packages index file for the current architecture.
     * Caches the uncompressed Packages text file locally.
     */
    public static File fetchPackageIndexSync(final Context context, final boolean forceRefresh) throws IOException {
        String arch = getTermuxArch();
        File cacheDir = context.getCacheDir();
        File indexFile = new File(cacheDir, "apt_packages_" + arch + ".txt");

        long cacheTtl = 24 * 60 * 60 * 1000L; // 24 hours
        if (!forceRefresh && indexFile.exists() && indexFile.length() > 50000 &&
                (System.currentTimeMillis() - indexFile.lastModified() < cacheTtl)) {
            return indexFile;
        }

        List<String> indexCandidates = new ArrayList<>();
        indexCandidates.add(APT_MAIN_REPO_BASE + "dists/stable/main/binary-" + arch + "/Packages.gz");
        indexCandidates.add(APT_MAIN_REPO_BASE + "dists/stable/main/binary-" + arch + "/Packages.xz");
        indexCandidates.add(APT_FALLBACK_REPO_BASE + "dists/stable/main/binary-" + arch + "/Packages.gz");
        indexCandidates.add(APT_FALLBACK_REPO_BASE + "dists/stable/main/binary-" + arch + "/Packages.xz");
        indexCandidates.add("https://termux.org/packages/" + arch + "/Packages.xz");
        indexCandidates.add("https://termux.org/packages/" + arch + "/Packages.gz");
        indexCandidates.add("https://termux.org/packages/" + arch + "/Packages");

        HttpURLConnection conn = null;
        String activeUrl = null;

        for (String candidateUrl : indexCandidates) {
            try {
                URL url = new URL(candidateUrl);
                HttpURLConnection testConn = (HttpURLConnection) url.openConnection();
                testConn.setInstanceFollowRedirects(true);
                testConn.setConnectTimeout(10000);
                testConn.setReadTimeout(15000);
                testConn.connect();

                int code = testConn.getResponseCode();
                if (code == 200) {
                    conn = testConn;
                    activeUrl = candidateUrl;
                    break;
                } else {
                    testConn.disconnect();
                }
            } catch (Exception ignored) {}
        }

        if (conn == null) {
            if (indexFile.exists() && indexFile.length() > 0) {
                return indexFile;
            }
            throw new IOException("Failed to download APT repository Packages index for architecture: " + arch);
        }

        InputStream rawStream = conn.getInputStream();
        InputStream decompressedStream;
        if (activeUrl.endsWith(".gz")) {
            decompressedStream = new GZIPInputStream(rawStream);
        } else if (activeUrl.endsWith(".xz")) {
            decompressedStream = new XZInputStream(rawStream);
        } else {
            decompressedStream = rawStream;
        }

        File tempIndex = new File(cacheDir, "apt_packages_" + arch + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tempIndex)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = decompressedStream.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
        }
        decompressedStream.close();

        if (tempIndex.length() > 1000) {
            if (indexFile.exists()) indexFile.delete();
            tempIndex.renameTo(indexFile);
            sParsedIndex.clear(); // invalidate in-memory cache
            return indexFile;
        } else {
            tempIndex.delete();
            throw new IOException("Downloaded APT index was empty or corrupted.");
        }
    }

    /**
     * Asynchronously fetches the Packages index.
     */
    public static void fetchPackageIndex(final Context context, final boolean forceRefresh, final IndexCallback callback) {
        new Thread(() -> {
            try {
                File indexFile = fetchPackageIndexSync(context, forceRefresh);
                if (callback != null) callback.onSuccess(indexFile);
            } catch (Exception e) {
                Tuils.log(e);
                if (callback != null) callback.onError("APT index error: " + e.getLocalizedMessage());
            }
        }).start();
    }

    /**
     * Returns the parsed in-memory index map, parsing the cached index file if not yet loaded.
     */
    public static synchronized Map<String, AptEntry> getParsedIndex(Context context, File indexFile) {
        if (!sParsedIndex.isEmpty()) return sParsedIndex;
        if (indexFile == null || !indexFile.exists() || indexFile.length() == 0) return sParsedIndex;

        try (BufferedReader reader = new BufferedReader(new FileReader(indexFile))) {
            String line;
            AptEntry currentEntry = null;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    if (currentEntry != null && currentEntry.packageName != null && currentEntry.filename != null) {
                        sParsedIndex.put(currentEntry.packageName.toLowerCase(), currentEntry);
                    }
                    currentEntry = null;
                    continue;
                }

                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();

                    if ("Package".equalsIgnoreCase(key)) {
                        currentEntry = new AptEntry();
                        currentEntry.packageName = value;
                    } else if (currentEntry != null) {
                        if ("Version".equalsIgnoreCase(key)) {
                            currentEntry.version = value;
                        } else if ("Filename".equalsIgnoreCase(key)) {
                            currentEntry.filename = value;
                            currentEntry.downloadUrl = APT_MAIN_REPO_BASE + value;
                        } else if ("Depends".equalsIgnoreCase(key)) {
                            currentEntry.dependencies = parseDependsString(value);
                        } else if ("Size".equalsIgnoreCase(key)) {
                            try { currentEntry.size = Long.parseLong(value); } catch (Exception ignored) {}
                        } else if ("SHA256".equalsIgnoreCase(key)) {
                            currentEntry.sha256 = value;
                        } else if ("Architecture".equalsIgnoreCase(key)) {
                            currentEntry.architecture = value;
                        } else if ("Description".equalsIgnoreCase(key)) {
                            currentEntry.description = value;
                        }
                    }
                }
            }
            if (currentEntry != null && currentEntry.packageName != null && currentEntry.filename != null) {
                sParsedIndex.put(currentEntry.packageName.toLowerCase(), currentEntry);
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
        return sParsedIndex;
    }

    /**
     * Resolves a package entry by name from the official APT index.
     */
    public static void resolvePackage(final Context context, final String packageName, final boolean forceRefreshIndex, final ResolveCallback callback) {
        final String cleanName = packageName != null ? packageName.trim().toLowerCase() : "";
        if (cleanName.isEmpty()) {
            if (callback != null) callback.onError("Package name is empty.");
            return;
        }

        fetchPackageIndex(context, forceRefreshIndex, new IndexCallback() {
            @Override
            public void onSuccess(File indexFile) {
                new Thread(() -> {
                    try {
                        Map<String, AptEntry> index = getParsedIndex(context, indexFile);
                        AptEntry entry = index.get(cleanName);
                        if (entry != null && entry.filename != null) {
                            if (callback != null) callback.onFound(entry);
                        } else {
                            if (callback != null) callback.onNotFound("Package '" + cleanName + "' not found in official Termux APT repository (" + getTermuxArch() + ").");
                        }
                    } catch (Exception e) {
                        Tuils.log(e);
                        if (callback != null) callback.onError("Failed reading APT index: " + e.getLocalizedMessage());
                    }
                }).start();
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    /**
     * Resolves the full dependency tree for a package in topological order:
     * leaf dependencies first, and the root package last.
     */
    public static List<AptEntry> resolveDependencyOrder(Context context, String rootPackageName) {
        try {
            File indexFile = fetchPackageIndexSync(context, false);
            Map<String, AptEntry> index = getParsedIndex(context, indexFile);
            List<AptEntry> result = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            Set<String> inStack = new HashSet<>();

            String cleanName = rootPackageName.trim().toLowerCase();
            resolveDfs(cleanName, index, context, visited, inStack, result);
            return result;
        } catch (Exception e) {
            Tuils.log(e);
            return Collections.emptyList();
        }
    }

    private static void resolveDfs(String pkgName, Map<String, AptEntry> index, Context context,
                                   Set<String> visited, Set<String> inStack, List<AptEntry> result) {
        if (pkgName == null || pkgName.isEmpty()) return;
        if (visited.contains(pkgName) || inStack.contains(pkgName)) return;

        inStack.add(pkgName);

        AptEntry entry = index.get(pkgName);
        if (entry != null && entry.dependencies != null) {
            for (String dep : entry.dependencies) {
                resolveDfs(dep.toLowerCase(), index, context, visited, inStack, result);
            }
        }

        inStack.remove(pkgName);
        visited.add(pkgName);

        if (entry != null) {
            // Check if already installed
            if (!isPackageAlreadyInstalled(context, entry)) {
                result.add(entry);
            }
        }
    }

    public static boolean isPackageAlreadyInstalled(Context context, AptEntry entry) {
        try {
            Map<String, String> installedMap = PackageManager.getInstalledPackagesMap(context);
            if (installedMap.containsKey(entry.packageName.toLowerCase())) {
                return true;
            }
            TermuxSessionManager sessionMgr = new TermuxSessionManager(context);
            TermuxSessionManager.SessionConfig config = sessionMgr.createSessionConfig();
            File prefixDir = new File(config.getPrefixPath());

            // Check if binary exists
            File binFile = new File(prefixDir, "bin/" + entry.packageName);
            if (binFile.exists()) return true;

            // Check if library file exists (e.g. libtalloc.so, libtalloc.so.2)
            if (entry.packageName.startsWith("lib")) {
                File so1 = new File(prefixDir, "lib/" + entry.packageName + ".so");
                File so2 = new File(prefixDir, "lib/" + entry.packageName + ".so.2");
                if (so1.exists() || so2.exists()) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static List<String> parseDependsString(String dependsRaw) {
        List<String> list = new ArrayList<>();
        if (dependsRaw == null || dependsRaw.trim().isEmpty()) return list;

        String[] parts = dependsRaw.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            // Handle alternatives like "a | b"
            if (trimmed.contains("|")) {
                trimmed = trimmed.split("\\|")[0].trim();
            }
            // Strip version constraints like "(>= 1.2.0)"
            int parenIdx = trimmed.indexOf('(');
            if (parenIdx > 0) {
                trimmed = trimmed.substring(0, parenIdx).trim();
            }
            if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                list.add(trimmed);
            }
        }
        return list;
    }

    private static class SymlinkTask {
        final File targetFile;
        final String linkTarget;
        final File prefixDir;
        final File homeDir;

        SymlinkTask(File targetFile, String linkTarget, File prefixDir, File homeDir) {
            this.targetFile = targetFile;
            this.linkTarget = linkTarget;
            this.prefixDir = prefixDir;
            this.homeDir = homeDir;
        }
    }

    /**
     * Synchronous extraction of a downloaded .deb archive into the app's internal $PREFIX directory.
     * Recreates directory trees, sets executable permissions (0755) on binaries and .so libraries,
     * and guarantees symlink resolution with file-copy fallback.
     */
    public static void extractDebArchiveSync(final File debFile, final File prefixDir, final File homeDir, final ExtractCallback callback) throws Exception {
        if (callback != null) callback.onProgress("Unpacking Debian package archive: " + debFile.getName());

        FileInputStream fis = new FileInputStream(debFile);
        ArArchiveInputStream arStream = new ArArchiveInputStream(fis);
        ArArchiveEntry arEntry;
        boolean dataFound = false;
        List<String> extractedFiles = new ArrayList<>();
        File primaryBinary = null;
        List<SymlinkTask> pendingSymlinks = new ArrayList<>();

        try {
            while ((arEntry = arStream.getNextEntry()) != null) {
                String name = arEntry.getName().trim();
                if (name.startsWith("data.tar")) {
                    dataFound = true;
                    if (callback != null) callback.onProgress("Extracting package filesystem (" + name + ")...");

                    InputStream rawTarStream;
                    if (name.endsWith(".xz")) {
                        rawTarStream = new XZInputStream(arStream);
                    } else if (name.endsWith(".gz")) {
                        rawTarStream = new GZIPInputStream(arStream);
                    } else {
                        rawTarStream = arStream;
                    }

                    TarArchiveInputStream tarStream = new TarArchiveInputStream(rawTarStream);
                    TarArchiveEntry tarEntry;

                    try {
                        while ((tarEntry = tarStream.getNextEntry()) != null) {
                            String entryName = tarEntry.getName();

                            String relativePath = entryName;
                            if (relativePath.startsWith("./")) relativePath = relativePath.substring(2);
                            if (relativePath.startsWith("data/data/com.termux/files/usr/")) {
                                relativePath = relativePath.substring("data/data/com.termux/files/usr/".length());
                            } else if (relativePath.startsWith("data/data/com.termux/files/")) {
                                relativePath = relativePath.substring("data/data/com.termux/files/".length());
                            }

                            if (relativePath.isEmpty() || relativePath.equals(".")) continue;

                            File targetFile = new File(prefixDir, relativePath);

                            if (tarEntry.isDirectory()) {
                                if (!targetFile.exists()) targetFile.mkdirs();
                            } else if (tarEntry.isSymbolicLink()) {
                                File parent = targetFile.getParentFile();
                                if (parent != null && !parent.exists()) parent.mkdirs();
                                
                                // File.exists() returns false for dangling symlinks.
                                // Unconditionally delete and remove target path before creating symlink.
                                targetFile.delete();
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    try {
                                        Os.remove(targetFile.getAbsolutePath());
                                    } catch (Exception ignored) {}
                                }

                                String linkTarget = tarEntry.getLinkName();
                                String termuxPrefix = "/data/data/com.termux/files/usr";
                                if (linkTarget.startsWith(termuxPrefix)) {
                                    linkTarget = prefixDir.getAbsolutePath() + linkTarget.substring(termuxPrefix.length());
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    try {
                                        Os.symlink(linkTarget, targetFile.getAbsolutePath());
                                    } catch (Exception symlinkEx) {
                                        Tuils.log(symlinkEx);
                                    }
                                }
                                pendingSymlinks.add(new SymlinkTask(targetFile, linkTarget, prefixDir, homeDir));
                                extractedFiles.add(targetFile.getAbsolutePath());
                            } else {
                                File parent = targetFile.getParentFile();
                                if (parent != null && !parent.exists()) parent.mkdirs();

                                // Unconditionally delete and remove target path before writing regular file
                                targetFile.delete();
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    try {
                                        Os.remove(targetFile.getAbsolutePath());
                                    } catch (Exception ignored) {}
                                }

                                try (FileOutputStream out = new FileOutputStream(targetFile)) {
                                    byte[] buf = new byte[8192];
                                    int len;
                                    while ((len = tarStream.read(buf)) != -1) {
                                        out.write(buf, 0, len);
                                    }
                                    out.flush();
                                } catch (Exception readEx) {
                                    Tuils.log(readEx);
                                }

                                if (relativePath.startsWith("bin/") || relativePath.contains(".so")) {
                                    targetFile.setExecutable(true, false);
                                    targetFile.setReadable(true, false);
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        try {
                                            Os.chmod(targetFile.getAbsolutePath(), 0755);
                                        } catch (Exception ignored) {}
                                    }
                                }

                                if (relativePath.startsWith("bin/")) {
                                    primaryBinary = targetFile;
                                    createLauncherShim(targetFile, homeDir, prefixDir);
                                }

                                extractedFiles.add(targetFile.getAbsolutePath());
                            }
                        }
                    } catch (Exception tarEx) {
                        Tuils.log(tarEx);
                    }
                    break;
                }
            }
        } catch (Exception arEx) {
            Tuils.log(arEx);
        }

        arStream.close();
        fis.close();

        // Post-process symlinks: ensure valid targets, fallback copy if symlink failed
        for (SymlinkTask task : pendingSymlinks) {
            try {
                File realTarget;
                if (task.linkTarget.startsWith("/")) {
                    realTarget = new File(task.linkTarget);
                } else {
                    realTarget = new File(task.targetFile.getParentFile(), task.linkTarget);
                }

                // If symlink does not exist or target is dangling and real target exists, copy directly
                if (!task.targetFile.exists() && realTarget.exists()) {
                    copyFile(realTarget, task.targetFile);
                }

                if (task.targetFile.exists()) {
                    task.targetFile.setReadable(true, false);
                    task.targetFile.setExecutable(true, false);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            Os.chmod(task.targetFile.getAbsolutePath(), 0755);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                Tuils.log(e);
            }
        }

        if (!dataFound) {
            if (callback != null) callback.onError("No data.tar archive found in .deb file.");
            throw new IOException("No data.tar archive found in .deb file.");
        }

        if (callback != null) {
            callback.onSuccess(primaryBinary, extractedFiles);
        }
    }

    /**
     * Asynchronously extracts a downloaded .deb archive.
     */
    public static void extractDebArchive(final File debFile, final File prefixDir, final File homeDir, final ExtractCallback callback) {
        new Thread(() -> {
            try {
                extractDebArchiveSync(debFile, prefixDir, homeDir, callback);
            } catch (Exception e) {
                Tuils.log(e);
                if (callback != null) callback.onError("Extraction error: " + e.getLocalizedMessage());
            }
        }).start();
    }

    public static void copyFile(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
        }
    }

    /**
     * Creates an intelligent launcher shim in $HOME/tui/bin/<binaryName> that sets up
     * $PREFIX, $HOME, $LD_LIBRARY_PATH, and automatically delegates to proot if installed
     * to resolve hardcoded /data/data/com.termux/files/usr paths.
     */
    public static void createLauncherShim(File actualBinary, File homeDir, File prefixDir) {
        try {
            File tuiBinDir = new File(homeDir, "tui/bin");
            if (!tuiBinDir.exists()) tuiBinDir.mkdirs();

            String binName = actualBinary.getName();
            File shimFile = new File(tuiBinDir, binName);

            StringBuilder sb = new StringBuilder();
            sb.append("#!/system/bin/sh\n");
            sb.append("export PREFIX=\"").append(prefixDir.getAbsolutePath()).append("\"\n");
            sb.append("export HOME=\"").append(homeDir.getAbsolutePath()).append("\"\n");
            sb.append("export PATH=\"$HOME/tui/bin:$PREFIX/bin:$PATH\"\n");
            sb.append("export LD_LIBRARY_PATH=\"$PREFIX/lib:$PREFIX/lib/pulseaudio:$LD_LIBRARY_PATH\"\n");
            sb.append("export TERM=\"xterm-256color\"\n");
            sb.append("export TMPDIR=\"$PREFIX/tmp\"\n");
            sb.append("export PROOT_TMP_DIR=\"$PREFIX/tmp\"\n");
            sb.append("export PROOT_NO_SECCOMP=1\n\n");

            sb.append("mkdir -p \"$PREFIX/tmp\" 2>/dev/null\n");
            sb.append("chmod 777 \"$PREFIX/tmp\" 2>/dev/null\n\n");

            // Ensure permissions
            sb.append("chmod 755 \"").append(actualBinary.getAbsolutePath()).append("\" 2>/dev/null\n\n");

            if ("proot".equals(binName)) {
                sb.append("DISTRO_BASE=\"$PREFIX/var/lib/proot-distro/installed-rootfs\"\n");
                sb.append("mkdir -p \"$DISTRO_BASE\" 2>/dev/null\n\n");

                sb.append("case \"$1\" in\n");
                sb.append("  \"login\")\n");
                sb.append("    shift\n");
                sb.append("    RAW_D=\"${1:-alpine}\"\n");
                sb.append("    case \"$RAW_D\" in\n");
                sb.append("      alpine|apline|alpin|alpne) DISTRO=\"alpine\" ;;\n");
                sb.append("      debian|debain|deb|debi) DISTRO=\"debian\" ;;\n");
                sb.append("      ubuntu|ubunutu|ubunt|ubantu) DISTRO=\"ubuntu\" ;;\n");
                sb.append("      *) DISTRO=\"$RAW_D\" ;;\n");
                sb.append("    esac\n");
                sb.append("    ROOTFS=\"$DISTRO_BASE/$DISTRO\"\n");
                sb.append("    if [ ! -d \"$ROOTFS\" ]; then\n");
                sb.append("      echo \"Distribution '$DISTRO' is not installed.\"\n");
                sb.append("      echo \"Run 'proot install $DISTRO' first.\"\n");
                sb.append("      exit 1\n");
                sb.append("    fi\n");
                sb.append("    shift\n");
                sb.append("    exec \"").append(actualBinary.getAbsolutePath()).append("\" \\\n");
                sb.append("      --kill-on-exit \\\n");
                sb.append("      -r \"$ROOTFS\" \\\n");
                sb.append("      -0 \\\n");
                sb.append("      -b /dev \\\n");
                sb.append("      -b /proc \\\n");
                sb.append("      -b /sys \\\n");
                sb.append("      -b \"$PREFIX/tmp:/tmp\" \\\n");
                sb.append("      -b \"$PREFIX/tmp:/data/data/com.termux/files/usr/tmp\" \\\n");
                sb.append("      -b /data \\\n");
                sb.append("      -b \"$HOME:/root/host_home\" \\\n");
                sb.append("      -w /root \\\n");
                sb.append("      /bin/sh -l \"$@\"\n");
                sb.append("    ;;\n");
                sb.append("  \"run\")\n");
                sb.append("    shift\n");
                sb.append("    DISTRO=\"$1\"\n");
                sb.append("    shift\n");
                sb.append("    ROOTFS=\"$DISTRO_BASE/$DISTRO\"\n");
                sb.append("    if [ ! -d \"$ROOTFS\" ]; then\n");
                sb.append("      echo \"Distribution '$DISTRO' is not installed.\"\n");
                sb.append("      echo \"Run 'proot install $DISTRO' first.\"\n");
                sb.append("      exit 1\n");
                sb.append("    fi\n");
                sb.append("    exec \"").append(actualBinary.getAbsolutePath()).append("\" \\\n");
                sb.append("      --kill-on-exit \\\n");
                sb.append("      -r \"$ROOTFS\" \\\n");
                sb.append("      -0 \\\n");
                sb.append("      -b /dev \\\n");
                sb.append("      -b /proc \\\n");
                sb.append("      -b /sys \\\n");
                sb.append("      -b \"$PREFIX/tmp:/tmp\" \\\n");
                sb.append("      -b \"$PREFIX/tmp:/data/data/com.termux/files/usr/tmp\" \\\n");
                sb.append("      -b /data \\\n");
                sb.append("      -b \"$HOME:/root/host_home\" \\\n");
                sb.append("      -w /root \\\n");
                sb.append("      /bin/sh -c \"$*\"\n");
                sb.append("    ;;\n");
                sb.append("  \"list\")\n");
                sb.append("    echo \"PRoot Linux Distributions:\"\n");
                sb.append("    for d in alpine debian ubuntu; do\n");
                sb.append("      if [ -d \"$DISTRO_BASE/$d\" ] && [ -f \"$DISTRO_BASE/$d/bin/sh\" ]; then\n");
                sb.append("        echo \"  * $d [Installed]\"\n");
                sb.append("      else\n");
                sb.append("        echo \"  * $d [Not installed]\"\n");
                sb.append("      fi\n");
                sb.append("    done\n");
                sb.append("    exit 0\n");
                sb.append("    ;;\n");
                sb.append("  \"remove\"|\"reset\")\n");
                sb.append("    shift\n");
                sb.append("    DISTRO=\"$1\"\n");
                sb.append("    if [ -d \"$DISTRO_BASE/$DISTRO\" ]; then\n");
                sb.append("      rm -rf \"$DISTRO_BASE/$DISTRO\"\n");
                sb.append("      echo \"Removed distribution '$DISTRO'.\"\n");
                sb.append("    else\n");
                sb.append("      echo \"Distribution '$DISTRO' is not installed.\"\n");
                sb.append("    fi\n");
                sb.append("    exit 0\n");
                sb.append("    ;;\n");
                sb.append("  \"install\")\n");
                sb.append("    shift\n");
                sb.append("    RAW_D=\"${1:-alpine}\"\n");
                sb.append("    case \"$RAW_D\" in\n");
                sb.append("      alpine|apline|alpin|alpne) DISTRO=\"alpine\" ;;\n");
                sb.append("      debian|debain|deb|debi) DISTRO=\"debian\" ;;\n");
                sb.append("      ubuntu|ubunutu|ubunt|ubantu) DISTRO=\"ubuntu\" ;;\n");
                sb.append("      *) DISTRO=\"$RAW_D\" ;;\n");
                sb.append("    esac\n");
                sb.append("    echo \"Preparing $DISTRO Linux container...\"\n");
                sb.append("    ROOTFS=\"$DISTRO_BASE/$DISTRO\"\n");
                sb.append("    if [ -d \"$ROOTFS\" ] && [ -f \"$ROOTFS/bin/sh\" ]; then\n");
                sb.append("      echo \"Distribution '$DISTRO' is already installed. Run 'proot login $DISTRO' to start.\"\n");
                sb.append("      exit 0\n");
                sb.append("    fi\n");
                sb.append("    echo \"Please run 'proot install $DISTRO' in T-UI for automated download with live progress bar.\"\n");
                sb.append("    exit 0\n");
                sb.append("    ;;\n");
                sb.append("  *)\n");
                sb.append("    exec \"").append(actualBinary.getAbsolutePath()).append("\" \\\n");
                sb.append("      --kill-on-exit \\\n");
                sb.append("      --link2symlink \\\n");
                sb.append("      -0 \\\n");
                sb.append("      -b /dev \\\n");
                sb.append("      -b /proc \\\n");
                sb.append("      -b /sys \\\n");
                sb.append("      -b \"$PREFIX:/data/data/com.termux/files/usr\" \\\n");
                sb.append("      -b \"$HOME:/data/data/com.termux/files/home\" \\\n");
                sb.append("      -b \"$PREFIX/tmp:/tmp\" \\\n");
                sb.append("      -b \"$PREFIX/tmp:/data/data/com.termux/files/usr/tmp\" \\\n");
                sb.append("      -b /data \\\n");
                sb.append("      \"$@\"\n");
                sb.append("    ;;\n");
                sb.append("esac\n");
            } else {
                // For mpv or general binaries, default to --help if no arguments passed to prevent no-surface driver segfaults
                if ("mpv".equals(binName)) {
                    sb.append("if [ \"$1\" = \"\" ]; then\n");
                    sb.append("    set -- --help\n");
                    sb.append("fi\n\n");
                }

                sb.append("if [ -f \"$PREFIX/bin/proot\" ]; then\n");
                sb.append("    chmod 755 \"$PREFIX/bin/proot\" 2>/dev/null\n");
                sb.append("    exec \"$PREFIX/bin/proot\" \\\n");
                sb.append("      --kill-on-exit \\\n");
                sb.append("      --link2symlink \\\n");
                sb.append("      -0 \\\n");
                sb.append("      -b /dev \\\n");
                sb.append("      -b /proc \\\n");
                sb.append("      -b /sys \\\n");
                sb.append("      -b \"$PREFIX:/data/data/com.termux/files/usr\" \\\n");
                sb.append("      -b \"$HOME:/data/data/com.termux/files/home\" \\\n");
                sb.append("      -b \"$PREFIX/tmp:/tmp\" \\\n");
                sb.append("      -b \"$PREFIX/tmp:/data/data/com.termux/files/usr/tmp\" \\\n");
                sb.append("      -b /data \\\n");
                sb.append("      \"").append(actualBinary.getAbsolutePath()).append("\" \"$@\"\n");
                sb.append("elif [ -e \"/data/data/com.termux/files/usr/bin/").append(binName).append("\" ]; then\n");
                sb.append("    exec \"/data/data/com.termux/files/usr/bin/").append(binName).append("\" \"$@\"\n");
                sb.append("else\n");
                sb.append("    exec \"").append(actualBinary.getAbsolutePath()).append("\" \"$@\"\n");
                sb.append("fi\n");
            }

            FileOutputStream fos = new FileOutputStream(shimFile);
            fos.write(sb.toString().getBytes());
            fos.flush();
            fos.close();

            shimFile.setExecutable(true, false);
            shimFile.setReadable(true, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    Os.chmod(shimFile.getAbsolutePath(), 0755);
                } catch (Exception ignored) {}
            }

            // If proot, also create proot-distro shim pointing to the same wrapper
            if ("proot".equals(binName)) {
                File distroShim = new File(tuiBinDir, "proot-distro");
                FileOutputStream distroFos = new FileOutputStream(distroShim);
                distroFos.write(sb.toString().getBytes());
                distroFos.flush();
                distroFos.close();
                distroShim.setExecutable(true, false);
                distroShim.setReadable(true, false);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        Os.chmod(distroShim.getAbsolutePath(), 0755);
                    } catch (Exception ignored) {}
                }
            }

        } catch (Exception e) {
            Tuils.log(e);
        }
    }
}
