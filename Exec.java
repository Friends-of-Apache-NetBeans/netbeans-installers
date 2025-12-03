/*
 * Copyright 2025 Codelerity Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.io.*;
import java.math.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import java.util.stream.*;

/**
 * Source file for use in workflows for downloading and building installers.
 *
 * Usage -
 *
 * <ul>
 * <li>{@code java Exec.java build os arch package-type}</li>
 * <li>{@code java Exec.java validate[|-downloads|-cache]}</li>
 * </ul>
 */
public class Exec {

    private static final String COMMAND_BUILD = "build";
    private static final String COMMAND_VALIDATE = "validate";
    private static final String COMMAND_VALIDATE_DOWNLOADS = "validate-downloads";
    private static final String COMMAND_VALIDATE_CACHE = "validate-cache";
    private static final String COMMAND_HASH = "hash";

    private final Path workingDir, cacheDir;
    private final Properties config;

    private final String os, arch, type;
    private final HttpClient httpClient;

    Exec(Path workingDir, Path cacheDir, Properties config,
            String os, String arch, String type) {
        this.workingDir = Objects.requireNonNull(workingDir);
        this.cacheDir = Objects.requireNonNull(cacheDir);
        this.config = Objects.requireNonNull(config);
        this.os = os;
        this.arch = arch;
        this.type = type;
        httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    void build() throws Exception {

        String version = config.getProperty("netbeans.version", "");
        if (version.isBlank()) {
            throw new IllegalStateException("NetBeans version not set");
        }

        if (type.equals("innosetup") && System.getenv("INNOSETUP_PATH") == null) {
            throw new IllegalStateException("Environment variable with InnoSetup path not found");
        }

        Path nbpackage = configureNBPackage();
        Path pkgConfig = workingDir.resolve("config")
                .resolve(os + "-" + arch + "-" + type + ".properties");
        Path jdk = resource("jdk." + os + "." + arch);
        Path netbeans = resource("netbeans");
        Path dist = Files.createDirectories(workingDir.resolve("dist"));

        List<String> cmd = buildNBPackageCommandLine(
                nbpackage, netbeans, pkgConfig, dist, jdk, version);

        System.out.println("Running NBPackage\n -- " + cmd);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        pb.start().waitFor();

        processAndHashOutput(dist);

    }

    void validate(boolean downloads) throws Exception {
        String version = config.getProperty("netbeans.version", "");
        if (version.isBlank()) {
            throw new IllegalArgumentException("NetBeans version not set");
        }
        List<String> resources = config.keySet().stream()
                .map(Object::toString)
                .filter(k -> k.endsWith(".url"))
                .map(k -> k.substring(0, k.lastIndexOf(".")))
                .toList();
        for (String resource : resources) {
            String urlString = config.getProperty(resource + ".url", "");
            if (urlString.isBlank()) {
                throw new IllegalArgumentException("Resource url not set : " + resource);
            }
            try {
                new URI(urlString);
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException("Invalid URL for resource : " + resource, ex);
            }
            String hashString = config.getProperty(resource + ".sha", "");
            try {
                Hash.forHashString(hashString);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid hash for resource : " + resource, ex);
            }
        }
        if (downloads) {
            for (String resource : resources) {
                resource(resource);
            }
        }
    }

    void warmCache() throws Exception {
        resource("nbpackage");
        resource("netbeans");
    }

    Path resource(String id) throws Exception {
        String link = config.getProperty(id + ".url", "");
        String hash = config.getProperty(id + ".sha", "");
        if (link.isBlank() || hash.isBlank()) {
            throw new IllegalArgumentException("Missing link and hash for resource : " + id);
        }
        URI uri = URI.create(link);
        Path resource = cacheDir.resolve(fileName(uri));
        if (!Files.exists(resource)) {
            System.out.println("Downloading " + id + " from " + uri);
            download(uri, resource);
        }
        System.out.println("Verifying : " + resource.getFileName());
        Hash.verifyFile(resource, hash);
        return resource;
    }

    Path configureNBPackage()
            throws Exception {
        Path nbpackageDir = workingDir.resolve("nbpackage");
        if (Files.exists(nbpackageDir)) {
            System.out.println("Found existing NBPackage dir");
        } else {
            Files.createDirectory(nbpackageDir);
            extractNBPackage(resource("nbpackage"), nbpackageDir);
        }
        if (os.equals("windows")) {
            return nbpackageDir.resolve("bin").resolve("nbpackage.cmd");
        } else {
            Path launcher = nbpackageDir.resolve("bin").resolve("nbpackage");
            Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwxr-xr-x"));
            return launcher;
        }
    }

    void extractNBPackage(Path zip, Path destination) throws IOException {
        try (FileSystem zipFS = FileSystems.newFileSystem(zip)) {
            Path source = findFile(zipFS.getPath("/"), "nbpackage*");
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!source.equals(dir)) {
                        Files.copy(dir, destination.resolve(source.relativize(dir).toString()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, destination.resolve(source.relativize(file).toString()));
                    return FileVisitResult.CONTINUE;
                }

            });
        }
    }

    List<String> buildNBPackageCommandLine(Path nbpackage, Path netbeans,
            Path pkgConfig, Path dist, Path jdk, String version) {
        List<String> cmd = new ArrayList<>();
        cmd.add(nbpackage.toString());
        cmd.add("--verbose");
        cmd.add("--input");
        cmd.add(netbeans.toString());
        cmd.add("--config");
        cmd.add(pkgConfig.toString());
        cmd.add("-Pversion=" + version);
        cmd.add("-Pruntime=" + jdk.toString());
        if (type.equals("innosetup")) {
            cmd.add("-Pinnosetup.tool=" + System.getenv("INNOSETUP_PATH"));
        } else if (type.equals("pkg")) {
            String APP_CERT_ID = System.getenv("APP_CERT_ID");
            if (APP_CERT_ID != null) {
                cmd.add("-Pmacos.codesign-id=" + APP_CERT_ID);
            }
            String INST_CERT_ID = System.getenv("INST_CERT_ID");
            if (INST_CERT_ID != null) {
                cmd.add("-Pmacos.pkgbuild-id=" + INST_CERT_ID);
            }
        }
        cmd.add("--output");
        cmd.add(dist.toString());
        return List.copyOf(cmd);
    }

    void processAndHashOutput(Path dist) throws IOException {
        Path result = switch (type) {
            case "deb" ->
                findFile(dist, "*.deb");
            case "rpm" ->
                findFile(dist, "*.rpm");
            case "innosetup" ->
                findFile(dist, "*.exe");
            case "pkg" ->
                findFile(dist, "*.pkg");
            default ->
                throw new IllegalStateException();
        };
        String pckFormat= switch(type){
            case "deb","rpm","pkg" -> type;
            case "innosetup" -> "exe";
            default -> type;
        };
        String fileName=result.getFileName().toString();
        String productIndentifier="%1$s-%2$s-%3$s".formatted(os,pckFormat,arch);
        String resultHash = Hash.SHA256.hashFile(result);
        Files.writeString(result.resolveSibling(fileName + ".sha256"),
                resultHash + "  " + result.getFileName());
        String frontMatter="""
                           %1$s-link: %2$s
                           %1$s-hash: %3$s

                           """.formatted(productIndentifier,
                                   fileName,
                                   resultHash );
        Files.writeString(result.resolveSibling(fileName + ".frontmatter"), frontMatter);
    }

    void download(URI link, Path destination) throws IOException, InterruptedException {
        httpClient.send(HttpRequest.newBuilder(link).build(),
                HttpResponse.BodyHandlers.ofFile(destination,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
    }

    String fileName(URI uri) {
        String path = uri.getPath();
        return path.substring(path.lastIndexOf("/") + 1);
    }

    Path findFile(Path dir, String glob) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            Iterator<Path> itr = stream.iterator();
            if (itr.hasNext()) {
                return itr.next();
            }
            throw new IOException("No file found matching " + glob + " in " + dir);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static void main(String[] args) throws Exception {

        final String command;
        final String os, arch, type;
        Path distDir= Path.of("");

        String cmd = args.length == 0 ? COMMAND_VALIDATE : args[0];
        switch (cmd) {
            case COMMAND_BUILD -> {
                if (args.length != 4) {
                    throw new IllegalArgumentException("Usage : build <os> <arch> <package-type>");
                }
                os = args[1];
                arch = args[2];
                type = args[3];
                command = cmd;
            }
            case COMMAND_VALIDATE, COMMAND_VALIDATE_DOWNLOADS, COMMAND_VALIDATE_CACHE -> {
                os = arch = type = null;
                command = cmd;
            }
            case COMMAND_HASH ->{
                if (args.length != 5) {
                    throw new IllegalArgumentException("Usage : hash <os> <arch> <package-type> <dist-dir>");
                }
                os = args[1];
                arch = args[2];
                type = args[3];
                distDir = Path.of( args[4] );
                command = cmd;
            }
            default ->
                throw new IllegalArgumentException("Unknown command : " + cmd);
        }

        Path workingDir = Path.of("").toAbsolutePath();
        System.out.println("Working Dir : " + workingDir);
        Path cacheDir = workingDir.resolve("cache");
        if (Files.exists(cacheDir)) {
            System.out.println("Found cache directory");
            System.out.println("Cache /");
            try (Stream<Path> files = Files.list(cacheDir)) {
                files.sorted().forEach(file -> System.out.println(" - " + file.getFileName()));
            }
        } else {
            System.out.println("Creating cache directory");
            Files.createDirectory(cacheDir);
        }
        System.out.println("Loading Configuration");
        Properties config = new Properties();
        try (Reader configReader = Files.newBufferedReader(workingDir.resolve("build.properties"))) {
            config.load(configReader);
        }
        config.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().toString()))
                .forEach(e -> System.out.println(" - " + e.getKey() + " : " + e.getValue()));

        Exec exec = new Exec(workingDir, cacheDir, config, os, arch, type);

        switch (command) {
            case COMMAND_VALIDATE -> {
                exec.validate(false);
            }
            case COMMAND_VALIDATE_DOWNLOADS -> {
                exec.validate(true);
            }
            case COMMAND_VALIDATE_CACHE -> {
                exec.validate(false);
                exec.warmCache();
            }
            case COMMAND_BUILD -> {
                exec.validate(false);
                exec.build();
            }
            case COMMAND_HASH -> {
                exec.processAndHashOutput(distDir);
            }
        }

        System.out.println("OK - Exiting");
    }

    enum Hash {

        SHA1("SHA-1", 40),
        SHA256("SHA-256", 64),
        SHA512("SHA-512", 128);

        private final String digestAlgo;
        private final int hashLength;

        private Hash(String digestAlgo, int hashLength) {
            this.digestAlgo = digestAlgo;
            this.hashLength = hashLength;
        }

        String hashFile(Path file) {
            try (InputStream input = Files.newInputStream(file)) {
                MessageDigest digest = MessageDigest.getInstance(digestAlgo);
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
                return String.format("%0" + hashLength + "x", new BigInteger(1, digest.digest()));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        static Hash forHashString(String hashString) {
            for (Hash hash : Hash.values()) {
                if (hash.hashLength == hashString.length()) {
                    return hash;
                }
            }
            throw new IllegalArgumentException("Invalid hash format : " + hashString);
        }

        static void verifyFile(Path file, String expected) {
            Hash hash;
            try {
                hash = Hash.forHashString(expected);
            } catch (Exception ex) {
                throw new IllegalArgumentException(
                        String.format("""

                            Invalid hash format : %s
                            For file : %s
                            """, expected, file.getFileName())
                );
            }
            String fileHash = hash.hashFile(file);
            if (!expected.equalsIgnoreCase(fileHash)) {
                throw new IllegalArgumentException(
                        String.format("""

                            Incorrect hash for file : %s
                            Expected : %s
                            Actual   : %s
                            """, file.getFileName(), expected, fileHash));
            }
        }
    }
}
