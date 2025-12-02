import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import com.sun.net.httpserver.*;

public class minecraft {

    // ========== CONFIGURATION ==========
    static class CONFIG {
        static final String UUID = System.getenv("UUID") != null ? System.getenv("UUID") : "3b1e5011-14dc-4679-9fee-c99a80978cbd";
        static final String FILE_PATH = System.getenv("FILE_PATH") != null ? System.getenv("FILE_PATH") : "/tmp/.cache";
        static final String SUB_PATH = System.getenv("SUB_PATH") != null ? System.getenv("SUB_PATH") : "sub";
        static final int PORT = Integer.parseInt(System.getenv("SERVER_PORT") != null ? System.getenv("SERVER_PORT") : (System.getenv("PORT") != null ? System.getenv("PORT") : "3000"));
        static final String NAME = System.getenv("NAME") != null ? System.getenv("NAME") : "vless";

        // CDN settings
        static final String CFIP = System.getenv("CFIP") != null ? System.getenv("CFIP") : "www.kick.com";
        static final int CFPORT = Integer.parseInt(System.getenv("CFPORT") != null ? System.getenv("CFPORT") : "443");

        // Argo Tunnel
        static final String ARGO_DOMAIN = System.getenv("ARGO_DOMAIN") != null ? System.getenv("ARGO_DOMAIN") : "";
        static final String ARGO_AUTH = System.getenv("ARGO_AUTH") != null ? System.getenv("ARGO_AUTH") : "";
        static final int ARGO_PORT = Integer.parseInt(System.getenv("ARGO_PORT") != null ? System.getenv("ARGO_PORT") : "8001");

        // sb settings
        static final String SB_VERSION = System.getenv("SB_VERSION") != null ? System.getenv("SB_VERSION") : "1.11.15";
        static final String SB_NAME = System.getenv("SB_NAME") != null ? System.getenv("SB_NAME") : "HY2";
        static final int SB_PORT = Integer.parseInt(System.getenv("SB_PORT") != null ? System.getenv("SB_PORT") : (System.getenv("SERVER_PORT") != null ? System.getenv("SERVER_PORT") : (System.getenv("PORT") != null ? System.getenv("PORT") : "38059")));
        static final String SB_UUID = System.getenv("SB_UUID") != null ? System.getenv("SB_UUID") : (System.getenv("UUID") != null ? System.getenv("UUID") : "3b1e5011-14dc-4679-9fee-c99a80978cbd");
        static final String SB_SNI = System.getenv("SB_SNI") != null ? System.getenv("SB_SNI") : "time.android.com";
        static final String SB_MASS_PROXY = System.getenv("SB_MASS_PROXY") != null ? System.getenv("SB_MASS_PROXY") : "https://www.gstatic.com";
        static final String SB_DOMAIN = System.getenv("SB_DOMAIN") != null ? System.getenv("SB_DOMAIN") : System.getenv("DOMAIN");
        static final String SB_HOST = System.getenv("SB_HOST") != null ? System.getenv("SB_HOST") : "127.0.0.1";
        static final String SB_OBFS_PWD = System.getenv("SB_OBFS_PWD") != null ? System.getenv("SB_OBFS_PWD") : java.util.UUID.randomUUID().toString().substring(0, 11) + java.util.UUID.randomUUID().toString().substring(0, 11);
    }

    // ========== CONSTANTS ==========
    static class COLORS {
        static final String reset = "\u001B[0m";
        static final String bright = "\u001B[1m";
        static final String dim = "\u001B[2m";
        static final String red = "\u001B[31m";
        static final String green = "\u001B[32m";
        static final String yellow = "\u001B[33m";
        static final String blue = "\u001B[34m";
        static final String magenta = "\u001B[35m";
        static final String cyan = "\u001B[36m";
        static final String white = "\u001B[37m";
    }

    // ========== SYSTEM VARIABLES ==========
    static final String ARCH = getArch();

    private static String getArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        return (arch.contains("arm") || arch.contains("aarch64")) ? "arm64" : "amd64";
    }

    static final String TAR_NAME = "sing-box-" + CONFIG.SB_VERSION + "-linux-" + ARCH + ".tar.gz";
    static final String DOWNLOAD_URL = System.getenv("SB_URL") != null ? System.getenv("SB_URL") : "https://github.com/SagerNet/sing-box/releases/download/v" + CONFIG.SB_VERSION + "/" + TAR_NAME;

    // File paths
    static class PATHS {
        static final String SB_BASE_DIR = Paths.get(CONFIG.FILE_PATH, "sb").toString();
        static final String SB_CERT_DIR = Paths.get(CONFIG.FILE_PATH, "sb", "cert").toString();
        static final String SB_CERT_PATH = Paths.get(CONFIG.FILE_PATH, "sb", "cert", "cert.pem").toString();
        static final String SB_KEY_PATH = Paths.get(CONFIG.FILE_PATH, "sb", "cert", "key.pem").toString();
        static final String SB_JSON = Paths.get(CONFIG.FILE_PATH, "sb", "sb.json").toString();
        static final String SB_BIN = Paths.get(CONFIG.FILE_PATH, "sb", "sb").toString();
        static final String SB_LOG_FILE = Paths.get(CONFIG.FILE_PATH, "sb", "sb.log").toString();
        static final String X_CONFIG = Paths.get(CONFIG.FILE_PATH, "config.json").toString();
        static final String BOOT_LOG = Paths.get(CONFIG.FILE_PATH, "boot.log").toString();
    }

    // Global state
    static class State {
        static List<String> xLinks = new ArrayList<>();
        static List<String> sboxLinks = new ArrayList<>();
        static String xBase64 = "";
        static String sboxBase64 = "";
        static Process sbProcess = null;
    }

    // ========== LOGGER ==========
    static class Logger {
        static void info(String message) {
            System.out.println(COLORS.cyan + "[INFO] " + message + COLORS.reset);
        }

        static void success(String message) {
            System.out.println(COLORS.green + "[OK] " + message + COLORS.reset);
        }

        static void warning(String message) {
            System.out.println(COLORS.yellow + "[WARN] " + message + COLORS.reset);
        }

        static void error(String message) {
            System.out.println(COLORS.red + "[ERROR] " + message + COLORS.reset);
        }

        static void step(String message) {
            System.out.println(COLORS.blue + "--> " + message + COLORS.reset);
        }

        static void header(String message) {
            System.out.println("\n" + COLORS.bright + COLORS.magenta + "=".repeat(60) + COLORS.reset);
            System.out.println(COLORS.bright + COLORS.magenta + message + COLORS.reset);
            System.out.println(COLORS.bright + COLORS.magenta + "=".repeat(60) + COLORS.reset + "\n");
        }

        static void divider() {
            System.out.println(COLORS.dim + "-".repeat(60) + COLORS.reset);
        }

        static void config(String key, String value) {
            System.out.println("  " + COLORS.cyan + key + ":" + COLORS.reset + " " + COLORS.yellow + value + COLORS.reset);
        }
    }

    // ========== SYSTEM UTILITIES ==========
    static class SystemUtils {
        /**
         * Get public IP address synchronously
         */
        static String getPublicIpSync() {
            Pattern ipRegex = Pattern.compile("\\b((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(\\.(?!$)|$)){4}\\b");
            String[] curlCandidates = {
                "https://ifconfig.co",
                "https://ifconfig.me/ip",
                "https://api.ipify.org",
                "https://ifconfig.io/ip"
            };

            for (String url : curlCandidates) {
                try {
                    Process process = new ProcessBuilder("curl", "-sS", url).start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line);
                    }
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        Matcher matcher = ipRegex.matcher(output.toString().trim());
                        if (matcher.find()) {
                            return matcher.group(0);
                        }
                    }
                } catch (Exception e) {
                    // Continue to next candidate
                }
            }

            try {
                Process process = new ProcessBuilder("dig", "+short", "myip.opendns.com", "@resolver1.opendns.com").start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    Matcher matcher = ipRegex.matcher(output.toString().trim());
                    if (matcher.find()) {
                        return matcher.group(0);
                    }
                }
            } catch (Exception e) {
                // Fall through
            }

            return null;
        }

        /**
         * Get ISP information
         */
        static String getISPInfo() throws Exception {
            try {
                Process process = new ProcessBuilder("/bin/sh", "-c", "curl -s https://speed.cloudflare.com/meta | awk -F\\\" '{print $26\"-\"$18}' | sed -e 's/ /_/g'").start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String isp = reader.readLine().trim();
                int exitCode = process.waitFor();
                if (exitCode == 0 && isp.length() > 0) {
                    return isp;
                }
            } catch (Exception e) {
                Logger.error("Failed to get ISP info: " + e.getMessage());
            }
            return "UNKNOWN";
        }

        /**
         * Ensure directory exists
         */
        static void ensureDirectory(String dirPath) {
            Path path = Paths.get(dirPath);
            if (!Files.exists(path)) {
                try {
                    Files.createDirectories(path);
                    Logger.success("Directory created: " + dirPath);
                } catch (IOException e) {
                    Logger.error("Failed to create directory: " + e.getMessage());
                }
            }
        }

        /**
         * Download file with progress
         */
        static void downloadFile(String fileName, String fileUrl) throws Exception {
            String filePath = Paths.get(CONFIG.FILE_PATH, fileName).toString();
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            new File(filePath).setExecutable(true);
            Logger.success("Downloaded: " + fileName);
        }

        /**
         * Apply system optimizations for better performance
         */
        static void applySystemOptimizations() {
            Logger.step("Applying system optimizations for maximum performance...");

            String[] optimizations = {
                "sysctl -w net.core.rmem_max=268435456",
                "sysctl -w net.core.wmem_max=268435456",
                "sysctl -w net.ipv4.tcp_rmem=\"4096 87380 268435456\"",
                "sysctl -w net.ipv4.tcp_wmem=\"4096 16384 268435456\"",
                "sysctl -w net.core.netdev_max_backlog=100000",
                "sysctl -w net.core.somaxconn=65535",
                "sysctl -w net.ipv4.tcp_max_syn_backlog=65535",
                "sysctl -w net.ipv4.tcp_congestion_control=bbr",
                "sysctl -w net.ipv4.tcp_fastopen=3",
                "sysctl -w net.core.default_qdisc=fq_codel",
                "sysctl -w fs.file-max=2097152",
                "sysctl -w fs.nr_open=2097152",
                "sysctl -w net.ipv4.tcp_mem=\"786432 2097152 3145728\"",
                "sysctl -w net.ipv4.udp_mem=\"786432 2097152 3145728\"",
                "sysctl -w net.ipv4.tcp_slow_start_after_idle=0",
                "sysctl -w net.ipv4.tcp_tw_reuse=1",
                "sysctl -w net.ipv4.tcp_fin_timeout=30",
                "sysctl -w net.ipv4.tcp_keepalive_time=1200",
                "sysctl -w net.ipv4.tcp_keepalive_intvl=30",
                "sysctl -w net.ipv4.tcp_keepalive_probes=3"
            };

            int applied = 0;
            for (String cmd : optimizations) {
                try {
                    Process process = new ProcessBuilder(cmd.split(" ")).start();
                    if (process.waitFor() == 0) {
                        applied++;
                    }
                } catch (Exception e) {
                    // Ignore errors if no root privileges
                }
            }

            if (applied > 0) {
                Logger.success("Applied " + applied + " system optimizations");
            } else {
                Logger.warning("Could not apply system optimizations (root privileges required)");
            }
        }
    }

    // ========== SB MANAGER ==========
    static class SbManager {
        /**
         * Get server host (domain, IP, or fallback)
         */
        static String getServerHost() throws Exception {
            if (CONFIG.SB_DOMAIN != null) {
                Logger.info("Using domain: " + CONFIG.SB_DOMAIN);
                return CONFIG.SB_DOMAIN;
            }

            String publicIp = SystemUtils.getPublicIpSync();
            if (publicIp != null) {
                Logger.info("Using public IP: " + publicIp);
                return publicIp;
            }

            Logger.warning("Using fallback host: " + CONFIG.SB_HOST);
            return CONFIG.SB_HOST;
        }

        /**
         * Ensure TLS certificates exist
         */
        static Map<String, String> ensureCertificates() {
            SystemUtils.ensureDirectory(PATHS.SB_CERT_DIR);

            // Use external certificates if provided
            if (System.getenv("EXTERNAL_CERT") != null && System.getenv("EXTERNAL_KEY") != null &&
                    Files.exists(Paths.get(System.getenv("EXTERNAL_CERT"))) && Files.exists(Paths.get(System.getenv("EXTERNAL_KEY")))) {
                Logger.info("Using external TLS certificates");
                Map<String, String> certs = new HashMap<>();
                certs.put("cert", System.getenv("EXTERNAL_CERT"));
                certs.put("key", System.getenv("EXTERNAL_KEY"));
                return certs;
            }

            // Generate self-signed certificates
            if (!Files.exists(Paths.get(PATHS.SB_CERT_PATH)) || !Files.exists(Paths.get(PATHS.SB_KEY_PATH))) {
                Logger.step("Generating self-signed TLS certificate");
                try {
                    Process process = new ProcessBuilder("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
                            "-subj", "/CN=" + CONFIG.SB_SNI,
                            "-keyout", PATHS.SB_KEY_PATH,
                            "-out", PATHS.SB_CERT_PATH,
                            "-days", "365").start();
                    if (process.waitFor() != 0) {
                        Logger.error("Failed to generate TLS certificate");
                        Map<String, String> certs = new HashMap<>();
                        certs.put("cert", null);
                        certs.put("key", null);
                        return certs;
                    }
                    Logger.success("TLS certificate generated");
                } catch (Exception e) {
                    Logger.error("Failed to generate TLS certificate: " + e.getMessage());
                    Map<String, String> certs = new HashMap<>();
                    certs.put("cert", null);
                    certs.put("key", null);
                    return certs;
                }
            }

            Map<String, String> certs = new HashMap<>();
            certs.put("cert", PATHS.SB_CERT_PATH);
            certs.put("key", PATHS.SB_KEY_PATH);
            return certs;
        }

        /**
         * Ensure sb binary is downloaded and ready
         */
        static boolean ensureBinary() throws Exception {
            if (Files.exists(Paths.get(PATHS.SB_BIN))) {
                return true;
            }

            SystemUtils.ensureDirectory(PATHS.SB_BASE_DIR);
            Logger.step("Downloading sb (" + ARCH + ")");

            String tarPath = Paths.get(PATHS.SB_BASE_DIR, TAR_NAME).toString();

            // Download sb
            Process curlProcess = new ProcessBuilder("curl", "-L", "-sS", "-o", tarPath, DOWNLOAD_URL).start();
            if (curlProcess.waitFor() != 0) {
                Logger.error("Failed to download sb");
                return false;
            }

            // Extract archive
            Process tarProcess = new ProcessBuilder("tar", "-xzf", tarPath, "-C", PATHS.SB_BASE_DIR).start();
            if (tarProcess.waitFor() != 0) {
                Logger.error("Failed to extract sb archive");
                return false;
            }

            // Move binary to correct location
            String extractedDir = Paths.get(PATHS.SB_BASE_DIR, "sing-box-" + CONFIG.SB_VERSION + "-linux-" + ARCH).toString();
            if (Files.exists(Paths.get(extractedDir, "sing-box"))) {
                Files.move(Paths.get(extractedDir, "sing-box"), Paths.get(PATHS.SB_BIN));
                new File(PATHS.SB_BIN).setExecutable(true);
                Logger.success("sb installed successfully");
                return true;
            }

            Logger.error("sb binary not found in archive");
            return false;
        }

        /**
         * Create sb configuration
         */
        static void writeConfiguration(String cert, String key) throws IOException {
            Logger.step("Creating sb configuration");

            String json = "{\n" +
                    "  \"log\": {\n" +
                    "    \"level\": \"info\",\n" +
                    "    \"timestamp\": true\n" +
                    "  },\n" +
                    "  \"inbounds\": [\n" +
                    "    {\n" +
                    "      \"type\": \"hysteria2\",\n" +
                    "      \"tag\": \"hy2-in\",\n" +
                    "      \"listen\": \"::\",\n" +
                    "      \"listen_port\": " + CONFIG.SB_PORT + ",\n" +
                    "      \"users\": [\n" +
                    "        {\n" +
                    "          \"password\": \"" + CONFIG.SB_UUID + "\"\n" +
                    "        }\n" +
                    "      ],\n" +
                    "      \"tls\": {\n" +
                    "        \"enabled\": true,\n" +
                    "        \"server_name\": \"" + CONFIG.SB_SNI + "\",\n" +
                    "        \"alpn\": [\"h3\"],\n" +
                    "        \"certificate_path\": \"" + cert.replace("\\", "\\\\") + "\",\n" +
                    "        \"key_path\": \"" + key.replace("\\", "\\\\") + "\"\n" +
                    "      },\n" +
                    "      \"obfs\": {\n" +
                    "        \"type\": \"salamander\",\n" +
                    "        \"password\": \"" + CONFIG.SB_OBFS_PWD + "\"\n" +
                    "      },\n" +
                    "      \"masquerade\": {\n" +
                    "        \"type\": \"proxy\",\n" +
                    "        \"url\": \"" + CONFIG.SB_MASS_PROXY + "\",\n" +
                    "        \"rewrite_host\": true\n" +
                    "      },\n" +
                    "      \"ignore_client_bandwidth\": false,\n" +
                    "      \"up_mbps\": 100,\n" +
                    "      \"down_mbps\": 100\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"outbounds\": [\n" +
                    "    {\n" +
                    "      \"type\": \"direct\",\n" +
                    "      \"tag\": \"direct\"\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"type\": \"block\",\n" +
                    "      \"tag\": \"block\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";

            try (FileWriter writer = new FileWriter(PATHS.SB_JSON)) {
                writer.write(json);
            }
            Logger.success("sb configuration created");
        }

        /**
         * Start sb process
         */
        static Process start() throws Exception {
            Logger.step("Starting sb...");

            if (!Files.exists(Paths.get(PATHS.SB_BIN))) {
                Logger.error("sb binary not found");
                return null;
            }

            // Validate configuration first
            Process checkProcess = new ProcessBuilder(PATHS.SB_BIN, "check", "-c", PATHS.SB_JSON).start();
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(checkProcess.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line);
            }
            if (checkProcess.waitFor() != 0) {
                Logger.error("sb configuration error: " + errorOutput.toString());
                return null;
            }

            Logger.success("sb configuration validated");

            // Start sb process
            File logFile = new File(PATHS.SB_LOG_FILE);
            ProcessBuilder pb = new ProcessBuilder(PATHS.SB_BIN, "run", "-c", PATHS.SB_JSON);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));
            Process child = pb.start();

            // Verify process started successfully
            Thread.sleep(2000);
            if (child.isAlive()) {
                Logger.success("sb started successfully");
            }

            return child;
        }

        /**
         * Initialize sb service
         */
        static Process initialize() throws Exception {
            Logger.header("SB CONFIGURATION");

            // Display configuration
            Logger.config("Node Name", CONFIG.SB_NAME);
            Logger.config("Port", String.valueOf(CONFIG.SB_PORT));
            Logger.config("UUID", CONFIG.SB_UUID);
            Logger.config("SNI", CONFIG.SB_SNI);
            Logger.config("Domain", CONFIG.SB_DOMAIN != null ? CONFIG.SB_DOMAIN : "Not set");
            Logger.config("Fallback Host", CONFIG.SB_HOST);
            Logger.config("Version", CONFIG.SB_VERSION);
            Logger.config("Architecture", ARCH);

            // Setup certificates
            Map<String, String> certs = ensureCertificates();
            if (certs.get("cert") == null || certs.get("key") == null) {
                Logger.error("Certificate setup failed, skipping sb");
                return null;
            }

            // Download binary
            if (!ensureBinary()) {
                Logger.error("Binary download failed, skipping sb");
                return null;
            }

            // Create configuration and start
            writeConfiguration(certs.get("cert"), certs.get("key"));
            return start();
        }

        /**
         * Generate shareable links
         */
        static String generateLinks() throws Exception {
            String isp = SystemUtils.getISPInfo();
            String serverHost = getServerHost();
            String insecure = System.getenv("EXTERNAL_CERT") != null ? "0" : "1";

            String baseUrl = "hysteria2://" + CONFIG.SB_UUID + "@" + serverHost + ":" + CONFIG.SB_PORT + "/?sni=" + CONFIG.SB_SNI + "&obfs=salamander&obfs-password=" + CONFIG.SB_OBFS_PWD + "&insecure=" + insecure + "#" + CONFIG.SB_NAME + "-" + isp;

            State.sboxLinks = Collections.singletonList(baseUrl);
            State.sboxBase64 = Base64.getEncoder().encodeToString(baseUrl.getBytes(StandardCharsets.UTF_8));

            return baseUrl;
        }
    }

    // ========== X MANAGER ==========
    static class XManager {
        /**
         * Create X configuration
         */
        static void createConfiguration() throws IOException {
            Logger.step("Creating X configuration");

            String json = "{\n" +
                    "  \"log\": {\n" +
                    "    \"access\": \"/dev/null\",\n" +
                    "    \"error\": \"/dev/null\",\n" +
                    "    \"loglevel\": \"none\"\n" +
                    "  },\n" +
                    "  \"inbounds\": [\n" +
                    "    {\n" +
                    "      \"port\": " + CONFIG.ARGO_PORT + ",\n" +
                    "      \"protocol\": \"vless\",\n" +
                    "      \"settings\": {\n" +
                    "        \"clients\": [{\n" +
                    "          \"id\": \"" + CONFIG.UUID + "\",\n" +
                    "          \"flow\": \"xtls-rprx-vision\"\n" +
                    "        }],\n" +
                    "        \"decryption\": \"none\",\n" +
                    "        \"fallbacks\": [\n" +
                    "          { \"dest\": 3001 },\n" +
                    "          { \"path\": \"/vless-argo\", \"dest\": 3002 }\n" +
                    "        ]\n" +
                    "      },\n" +
                    "      \"streamSettings\": { \"network\": \"tcp\" }\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"port\": 3001,\n" +
                    "      \"listen\": \"127.0.0.1\",\n" +
                    "      \"protocol\": \"vless\",\n" +
                    "      \"settings\": {\n" +
                    "        \"clients\": [{ \"id\": \"" + CONFIG.UUID + "\" }],\n" +
                    "        \"decryption\": \"none\"\n" +
                    "      },\n" +
                    "      \"streamSettings\": {\n" +
                    "        \"network\": \"ws\",\n" +
                    "        \"security\": \"none\",\n" +
                    "        \"wsSettings\": { \"path\": \"/vless-argo\" }\n" +
                    "      }\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"port\": 3002,\n" +
                    "      \"listen\": \"127.0.0.1\",\n" +
                    "      \"protocol\": \"vless\",\n" +
                    "      \"settings\": {\n" +
                    "        \"clients\": [{ \"id\": \"" + CONFIG.UUID + "\", \"level\": 0 }],\n" +
                    "        \"decryption\": \"none\"\n" +
                    "      },\n" +
                    "      \"streamSettings\": {\n" +
                    "        \"network\": \"ws\",\n" +
                    "        \"security\": \"none\",\n" +
                    "        \"wsSettings\": { \"path\": \"/vless-argo\" }\n" +
                    "      },\n" +
                    "      \"sniffing\": {\n" +
                    "        \"enabled\": true,\n" +
                    "        \"destOverride\": [\"http\", \"tls\", \"quic\"],\n" +
                    "        \"metadataOnly\": false\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"dns\": {\n" +
                    "    \"servers\": [\"https+local://8.8.8.8/dns-query\"]\n" +
                    "  },\n" +
                    "  \"outbounds\": [\n" +
                    "    { \"protocol\": \"freedom\", \"tag\": \"direct\" },\n" +
                    "    { \"protocol\": \"blackhole\", \"tag\": \"block\" }\n" +
                    "  ]\n" +
                    "}";

            try (FileWriter writer = new FileWriter(PATHS.X_CONFIG)) {
                writer.write(json);
            }
            Logger.success("X configuration created");
        }

        /**
         * Get system architecture
         */
        static String getSystemArchitecture() {
            String arch = System.getProperty("os.arch").toLowerCase();
            return (arch.contains("arm") || arch.contains("aarch64")) ? "arm" : "amd";
        }

        /**
         * Get files to download for current architecture
         */
        static List<Map<String, String>> getFilesForArchitecture(String architecture) {
            List<Map<String, String>> files = new ArrayList<>();
            if (architecture.equals("arm")) {
                Map<String, String> file1 = new HashMap<>();
                file1.put("fileName", "web");
                file1.put("fileUrl", "https://arm64.ssss.nyc.mn/web");
                files.add(file1);
                Map<String, String> file2 = new HashMap<>();
                file2.put("fileName", "bot");
                file2.put("fileUrl", "https://arm64.ssss.nyc.mn/2go");
                files.add(file2);
            } else {
                Map<String, String> file1 = new HashMap<>();
                file1.put("fileName", "web");
                file1.put("fileUrl", "https://amd64.ssss.nyc.mn/web");
                files.add(file1);
                Map<String, String> file2 = new HashMap<>();
                file2.put("fileName", "bot");
                file2.put("fileUrl", "https://amd64.ssss.nyc.mn/2go");
                files.add(file2);
            }
            return files;
        }

        /**
         * Download and setup X components
         */
        static void downloadAndRun() throws Exception {
            String architecture = getSystemArchitecture();
            List<Map<String, String>> filesToDownload = getFilesForArchitecture(architecture);

            if (filesToDownload.isEmpty()) {
                Logger.warning("No files found for architecture: " + architecture);
                return;
            }

            Logger.step("Downloading files for " + architecture + " architecture");

            for (Map<String, String> file : filesToDownload) {
                SystemUtils.downloadFile(file.get("fileName"), file.get("fileUrl"));
            }
            Logger.success("All files downloaded successfully");

            // Set file permissions
            String[] filesToAuthorize = {"./web", "./bot"};
            for (String relativeFilePath : filesToAuthorize) {
                String absoluteFilePath = Paths.get(CONFIG.FILE_PATH, relativeFilePath).toString();
                if (Files.exists(Paths.get(absoluteFilePath))) {
                    new File(absoluteFilePath).setExecutable(true);
                    Logger.success("Permissions set for: " + absoluteFilePath);
                }
            }

            // Start services
            startXCore();
            startCloudflared();
        }

        /**
         * Start X core
         */
        static void startXCore() throws Exception {
            String command = "nohup " + Paths.get(CONFIG.FILE_PATH, "web").toString() + " -c " + PATHS.X_CONFIG + " >/dev/null 2>&1 &";
            Process process = new ProcessBuilder("/bin/sh", "-c", command).start();
            Logger.success("X core started");
        }

        /**
         * Start Cloudflared tunnel
         */
        static void startCloudflared() throws Exception {
            if (!Files.exists(Paths.get(CONFIG.FILE_PATH, "bot"))) {
                return;
            }

            String args;
            if (CONFIG.ARGO_AUTH.matches("^[A-Z0-9a-z=]{120,250}$")) {
                args = "tunnel --edge-ip-version auto --no-autoupdate --protocol http2 run --token " + CONFIG.ARGO_AUTH;
            } else if (CONFIG.ARGO_AUTH.matches("TunnelSecret")) {
                args = "tunnel --edge-ip-version auto --config " + Paths.get(CONFIG.FILE_PATH, "tunnel.yml").toString() + " run";
            } else {
                args = "tunnel --edge-ip-version auto --no-autoupdate --protocol http2 --logfile " + PATHS.BOOT_LOG + " --loglevel info --url http://localhost:" + CONFIG.ARGO_PORT;
            }

            String command = "nohup " + Paths.get(CONFIG.FILE_PATH, "bot").toString() + " " + args + " >/dev/null 2>&1 &";
            Process process = new ProcessBuilder("/bin/sh", "-c", command).start();
            Logger.success("Cloudflared tunnel started");
        }

        /**
         * Extract Argo tunnel domains
         */
        static String extractDomains() throws Exception {
            String argoDomain;

            if (!CONFIG.ARGO_AUTH.isEmpty() && !CONFIG.ARGO_DOMAIN.isEmpty()) {
                argoDomain = CONFIG.ARGO_DOMAIN;
                Logger.config("ARGO_DOMAIN", argoDomain);
                generateLinks(argoDomain);
            } else {
                Logger.step("Waiting for Cloudflared to start...");
                Thread.sleep(8000);

                if (Files.exists(Paths.get(PATHS.BOOT_LOG))) {
                    String fileContent = new String(Files.readAllBytes(Paths.get(PATHS.BOOT_LOG)));
                    Pattern pattern = Pattern.compile("https?://([^ ]*trycloudflare\\.com)/?");
                    Matcher matcher = pattern.matcher(fileContent);
                    List<String> domains = new ArrayList<>();
                    while (matcher.find()) {
                        domains.add(matcher.group(1));
                    }
                    argoDomain = !domains.isEmpty() ? domains.get(0) : "fallback.trycloudflare.com";
                    Logger.config("Argo Domain", argoDomain);
                } else {
                    Logger.warning("Boot log not found, using fallback domain");
                    argoDomain = "fallback.trycloudflare.com";
                }

                generateLinks(argoDomain);
            }

            return argoDomain;
        }

        /**
         * Generate X shareable links
         */
        static String generateLinks(String argoDomain) throws Exception {
            String isp = SystemUtils.getISPInfo();

            Thread.sleep(2000);
            String vlessLink = "vless://" + CONFIG.UUID + "@" + CONFIG.CFIP + ":" + CONFIG.CFPORT + "?encryption=none&security=tls&sni=" + argoDomain + "&type=ws&host=" + argoDomain + "&path=%2Fvless-argo%3Fed%3D2560#" + CONFIG.NAME + "-" + isp;

            State.xLinks = Collections.singletonList(vlessLink);
            State.xBase64 = Base64.getEncoder().encodeToString(vlessLink.getBytes(StandardCharsets.UTF_8));

            return vlessLink;
        }
    }

    // ========== HTTP SERVER ==========
    static class HttpServer {
        /**
         * Create HTTP server
         */
        static void createServer() throws Exception {
            com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(CONFIG.PORT), 0);
            server.createContext("/", new HttpHandler() {
                public void handle(HttpExchange exchange) throws IOException {
                    String response = "{\"service\": \"Hello World\"}";
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            });

            server.createContext("/" + CONFIG.SUB_PATH, new HttpHandler() {
                public void handle(HttpExchange exchange) throws IOException {
                    String response = generateHtml(State.xLinks.stream().map(HttpServer::escapeHtml).toArray(String[]::new),
                            State.sboxLinks.stream().map(HttpServer::escapeHtml).toArray(String[]::new),
                            escapeHtml(State.xBase64 != null ? State.xBase64 : ""),
                            escapeHtml(State.sboxBase64 != null ? State.sboxBase64 : ""));
                    exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                    os.close();
                }
            });

            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();
            Logger.success("HTTP server running on port: " + CONFIG.PORT);
        }

        /**
         * Escape HTML special characters
         */
        static String escapeHtml(String text) {
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#039;");
        }

        /**
         * Generate HTML page
         */
        static String generateHtml(String[] xLinks, String[] sboxLinks, String xBase64, String sboxBase64) {
            return """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>Xray-Sing</title>
                        <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
                        <style>
                            """ + getStyles() + """
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1><i class="fas fa-shield-alt"></i> Xray-Sing</h1>
                                <p>Secure and fast configurations</p>
                                <div class="status-indicator">
                                    <i class="fas fa-circle"></i> Online
                                </div>
                            </div>
                    
                            <div class="grid">
                                """ + generateXCard(xLinks, xBase64) + """
                                """ + generateSboxCard(sboxLinks, sboxBase64) + """
                            </div>
                        </div>
                    
                        <div class="toast" id="toast">Copied to clipboard!</div>
                    
                        <script>
                            """ + getScript() + """
                        </script>
                    </body>
                    </html>""";
        }

        /**
         * Get CSS styles
         */
        static String getStyles() {
            return """
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    :root {
                        --primary: #6366f1;
                        --primary-dark: #4f46e5;
                        --secondary: #10b981;
                        --dark: #1f2937;
                        --darker: #111827;
                        --light: #f9fafb;
                        --gray: #6b7280;
                        --border: #374151;
                    }
                    
                    body {
                        font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
                        background: linear-gradient(135deg, var(--darker) 0%, var(--dark) 100%);
                        color: var(--light);
                        min-height: 100vh;
                        line-height: 1.6;
                    }
                    
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                        padding: 2rem;
                    }
                    
                    .header {
                        text-align: center;
                        margin-bottom: 3rem;
                    }
                    
                    .header h1 {
                        font-size: 3rem;
                        font-weight: 700;
                        background: linear-gradient(135deg, var(--primary) 0%, var(--secondary) 100%);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        margin-bottom: 0.5rem;
                    }
                    
                    .header p {
                        color: var(--gray);
                        font-size: 1.2rem;
                    }
                    
                    .grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
                        gap: 2rem;
                        margin-bottom: 2rem;
                    }
                    
                    .card {
                        background: rgba(255, 255, 255, 0.05);
                        backdrop-filter: blur(10px);
                        border: 1px solid var(--border);
                        border-radius: 1rem;
                        padding: 2rem;
                        transition: all 0.3s ease;
                    }
                    
                    .card:hover {
                        transform: translateY(-5px);
                        border-color: var(--primary);
                        box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
                    }
                    
                    .card-header {
                        display: flex;
                        align-items: center;
                        gap: 1rem;
                        margin-bottom: 1.5rem;
                    }
                    
                    .card-icon {
                        width: 50px;
                        height: 50px;
                        background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%);
                        border-radius: 12px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 1.5rem;
                    }
                    
                    .card-title {
                        font-size: 1.5rem;
                        font-weight: 600;
                    }
                    
                    .card-subtitle {
                        color: var(--gray);
                        font-size: 0.9rem;
                    }
                    
                    .link-item {
                        background: rgba(255, 255, 255, 0.05);
                        border: 1px solid var(--border);
                        border-radius: 0.75rem;
                        padding: 1rem;
                        margin-bottom: 1rem;
                        display: flex;
                        align-items: center;
                        gap: 1rem;
                        transition: all 0.3s ease;
                    }
                    
                    .link-item:hover {
                        border-color: var(--primary);
                        background: rgba(99, 102, 241, 0.1);
                    }
                    
                    .link-content {
                        flex: 1;
                        word-break: break-all;
                        font-family: 'Monaco', 'Consolas', monospace;
                        font-size: 0.85rem;
                        color: var(--light);
                    }
                    
                    .copy-btn {
                        background: var(--primary);
                        color: white;
                        border: none;
                        padding: 0.5rem 1rem;
                        border-radius: 0.5rem;
                        cursor: pointer;
                        display: flex;
                        align-items: center;
                        gap: 0.5rem;
                        font-size: 0.9rem;
                        transition: all 0.3s ease;
                        white-space: nowrap;
                    }
                    
                    .copy-btn:hover {
                        background: var(--primary-dark);
                        transform: scale(1.05);
                    }
                    
                    .copy-btn.copied {
                        background: var(--secondary);
                    }
                    
                    .base64-section {
                        margin-top: 1.5rem;
                    }
                    
                    .base64-title {
                        font-size: 1.1rem;
                        font-weight: 600;
                        margin-bottom: 1rem;
                        display: flex;
                        align-items: center;
                        gap: 0.5rem;
                    }
                    
                    .base64-content {
                        background: rgba(0, 0, 0, 0.3);
                        border: 1px solid var(--border);
                        border-radius: 0.75rem;
                        padding: 1rem;
                        font-family: 'Monaco', 'Consolas', monospace;
                        font-size: 0.8rem;
                        line-height: 1.4;
                        word-break: break-all;
                        margin-bottom: 1rem;
                        position: relative;
                    }
                    
                    .protocol-badge {
                        background: var(--primary);
                        color: white;
                        padding: 0.25rem 0.75rem;
                        border-radius: 1rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                        margin-bottom: 0.5rem;
                        display: inline-block;
                    }
                    
                    .status-indicator {
                        display: inline-flex;
                        align-items: center;
                        gap: 0.5rem;
                        padding: 0.25rem 0.75rem;
                        background: var(--secondary);
                        color: white;
                        border-radius: 1rem;
                        font-size: 0.75rem;
                        font-weight: 600;
                        margin-left: 1rem;
                    }
                    
                    .status-indicator.offline {
                        background: #ef4444;
                    }
                    
                    @media (max-width: 768px) {
                        .container {
                            padding: 1rem;
                        }
                    
                        .grid {
                            grid-template-columns: 1fr;
                        }
                    
                        .header h1 {
                            font-size: 2rem;
                        }
                    
                        .link-item {
                            flex-direction: column;
                            align-items: stretch;
                        }
                    
                        .copy-btn {
                            align-self: stretch;
                            justify-content: center;
                        }
                    }
                    
                    .toast {
                        position: fixed;
                        bottom: 2rem;
                        right: 2rem;
                        background: var(--secondary);
                        color: white;
                        padding: 1rem 2rem;
                        border-radius: 0.75rem;
                        box-shadow: 0 10px 25px rgba(0, 0, 0, 0.3);
                        transform: translateY(100px);
                        opacity: 0;
                        transition: all 0.3s ease;
                        z-index: 1000;
                    }
                    
                    .toast.show {
                        transform: translateY(0);
                        opacity: 1;
                    }""";
        }

        /**
         * Get JavaScript code
         */
        static String getScript() {
            return """
                    function copyToClipboard(text, button) {
                        if (navigator.clipboard && window.isSecureContext) {
                            navigator.clipboard.writeText(text).then(() => {
                                showToast(button);
                            }).catch(err => {
                                console.error('Failed to copy with navigator.clipboard: ', err);
                                fallbackCopyToClipboard(text, button);
                            });
                        } else {
                            fallbackCopyToClipboard(text, button);
                        }
                    }
                    
                    function fallbackCopyToClipboard(text, button) {
                        const textarea = document.createElement('textarea');
                        textarea.value = text;
                        textarea.style.position = 'fixed';
                        textarea.style.opacity = '0';
                        document.body.appendChild(textarea);
                        textarea.select();
                        try {
                            document.execCommand('copy');
                            showToast(button);
                        } catch (err) {
                            console.error('Failed to copy with execCommand: ', err);
                            alert('Failed to copy to clipboard. Please copy manually.');
                        } finally {
                            document.body.removeChild(textarea);
                        }
                    }
                    
                    function showToast(button) {
                        const toast = document.getElementById('toast');
                        toast.classList.add('show');
                        setTimeout(() => {
                            toast.classList.remove('show');
                        }, 2000);
                    
                        if (button) {
                            const originalText = button.innerHTML;
                            button.innerHTML = '<i class="fas fa-check"></i> Copied!';
                            button.classList.add('copied');
                            setTimeout(() => {
                                button.innerHTML = originalText;
                                button.classList.remove('copied');
                            }, 2000);
                        }
                    }
                    
                    document.addEventListener('DOMContentLoaded', () => {
                        const cards = document.querySelectorAll('.card');
                        cards.forEach(card => {
                            card.addEventListener('click', (e) => {
                                if (!e.target.closest('.copy-btn')) {
                                    card.style.transform = 'scale(0.98)';
                                    setTimeout(() => {
                                        card.style.transform = '';
                                    }, 150);
                                }
                            });
                        });
                    });""";
        }

        /**
         * Generate X configuration card
         */
        static String generateXCard(String[] links, String base64) {
            StringBuilder sb = new StringBuilder();
            sb.append("""
                    <div class="card">
                        <div class="card-header">
                            <div class="card-icon">
                                <i class="fas fa-bolt"></i>
                            </div>
                            <div>
                                <div class="card-title">X Protocol</div>
                                <div class="card-subtitle">VLESS Argo protocol</div>
                            </div>
                        </div>
                    
                        <div class="protocol-badge">Connection Link</div>
                    """);
            if (links.length > 0) {
                for (String link : links) {
                    sb.append("""
                            <div class="link-item">
                                <div class="link-content">""").append(link).append("""
                                </div>
                                <button class="copy-btn" onclick="copyToClipboard('""").append(link).append("""
                                ', this)">
                                    <i class="fas fa-copy"></i> Copy
                                </button>
                            </div>
                            """);
                }
            } else {
                sb.append("""
                        <div class="link-item"><div class="link-content">Links not generated yet</div></div>
                        """);
            }

            sb.append("""
                        <div class="base64-section">
                            <div class="base64-title">
                                <i class="fas fa-qrcode"></i> Base64 Configuration
                            </div>
                    """);
            if (!base64.isEmpty()) {
                sb.append("""
                            <div class="base64-content">""").append(base64).append("""
                            </div>
                            <button class="copy-btn" onclick="copyToClipboard('""").append(base64).append("""
                            ', this)">
                                <i class="fas fa-copy"></i> Copy Base64
                            </button>
                            """);
            } else {
                sb.append("""
                            <div class="base64-content">Subscription not generated yet</div>
                            """);
            }
            sb.append("""
                        </div>
                    </div>
                    """);
            return sb.toString();
        }

        /**
         * Generate sb configuration card
         */
        static String generateSboxCard(String[] links, String base64) {
            StringBuilder sb = new StringBuilder();
            sb.append("""
                    <div class="card">
                        <div class="card-header">
                            <div class="card-icon">
                                <i class="fas fa-rocket"></i>
                            </div>
                            <div>
                                <div class="card-title">HY2 Protocol</div>
                                <div class="card-subtitle">High-performance protocol</div>
                            </div>
                        </div>
                    
                        <div class="protocol-badge">Connection Link</div>
                    """);
            if (links.length > 0) {
                for (String link : links) {
                    sb.append("""
                            <div class="link-item">
                                <div class="link-content">""").append(link).append("""
                                </div>
                                <button class="copy-btn" onclick="copyToClipboard('""").append(link).append("""
                                ', this)">
                                    <i class="fas fa-copy"></i> Copy
                                </button>
                            </div>
                            """);
                }
            } else {
                sb.append("""
                        <div class="link-item"><div class="link-content">Links not generated yet</div></div>
                        """);
            }

            sb.append("""
                        <div class="base64-section">
                            <div class="base64-title">
                                <i class="fas fa-qrcode"></i> Base64 Configuration
                            </div>
                    """);
            if (!base64.isEmpty()) {
                sb.append("""
                            <div class="base64-content">""").append(base64).append("""
                            </div>
                            <button class="copy-btn" onclick="copyToClipboard('""").append(base64).append("""
                            ', this)">
                                <i class="fas fa-copy"></i> Copy Base64
                            </button>
                            """);
            } else {
                sb.append("""
                            <div class="base64-content">Configuration not generated yet</div>
                            """);
            }
            sb.append("""
                        </div>
                    </div>
                    """);
            return sb.toString();
        }
    }

    // ========== MAIN APPLICATION ==========
    static class Application {
        /**
         * Initialize application
         */
        static void initialize() throws Exception {
            SystemUtils.ensureDirectory(CONFIG.FILE_PATH);
            SystemUtils.ensureDirectory(PATHS.SB_BASE_DIR);
        }

        /**
         * Start the application
         */
        static void start() throws Exception {
            Logger.header("[START] XRAY-SING STARTUP");

            initialize();

            // Apply system optimizations
            SystemUtils.applySystemOptimizations();

            // Start sb
            Logger.step("Starting sb server...");
            State.sbProcess = SbManager.initialize();

            if (State.sbProcess != null) {
                SbManager.generateLinks();
            } else {
                Logger.warning("sb failed to start, skipping link generation");
            }

            // Start X
            Logger.step("Starting X server...");
            XManager.createConfiguration();
            XManager.downloadAndRun();

            // Wait for services to start
            Logger.step("Waiting for services to start...");
            Thread.sleep(5000);

            // Generate links
            XManager.extractDomains();

            // Start HTTP server
            HttpServer.createServer();
            printAllLinks();
        }

        /**
         * Print all connection links
         */
        static void printAllLinks() {
            Logger.header("[LINKS] CONNECTION LINKS");

            System.out.println("\n" + COLORS.green + COLORS.bright + "[X] X LINKS:" + COLORS.reset);
            for (int i = 0; i < State.xLinks.size(); i++) {
                System.out.println("  " + COLORS.cyan + (i + 1) + "." + COLORS.reset + " " + COLORS.yellow + State.xLinks.get(i) + COLORS.reset);
            }

            System.out.println("\n" + COLORS.blue + COLORS.bright + "[X] X BASE64:" + COLORS.reset);
            System.out.println("  " + COLORS.white + State.xBase64 + COLORS.reset);

            if (!State.sboxLinks.isEmpty()) {
                System.out.println("\n" + COLORS.magenta + COLORS.bright + "[HY2] HY2 LINKS:" + COLORS.reset);
                for (int i = 0; i < State.sboxLinks.size(); i++) {
                    System.out.println("  " + COLORS.cyan + (i + 1) + "." + COLORS.reset + " " + COLORS.yellow + State.sboxLinks.get(i) + COLORS.reset);
                }

                System.out.println("\n" + COLORS.blue + COLORS.bright + "[HY2] HY2 BASE64:" + COLORS.reset);
                System.out.println("  " + COLORS.white + State.sboxBase64 + COLORS.reset);
            } else {
                System.out.println("\n" + COLORS.yellow + COLORS.bright + "[WARN] HY2 LINKS: Not available" + COLORS.reset);
            }

            Logger.divider();
            Logger.success("Services are running! Use the links above to connect.");

            // Auto-clear terminal after 3 minutes
            Logger.step("Terminal will clear in 3 minutes...");
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    System.out.print("\u001B[2J\u001B[0f");
                    System.out.println(COLORS.bright + COLORS.green + "Thank you for using this Xray-Sing!" + COLORS.reset + "\n");
                }
            }, 3 * 60 * 1000);
        }
    }

    public static void main(String[] args) {
        try {
            // Force UTF-8 output
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8.name()));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8.name()));
            Application.start();
        } catch (Exception e) {
            Logger.error("Application failed to start: " + e.getMessage());
            System.exit(1);
        }
    }
}
