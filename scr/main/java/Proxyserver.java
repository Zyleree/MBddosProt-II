package com.example;

import org.json.JSONObject;

import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProxyServer {
    private static final String CONFIG_FILE = "config/config.ini";
    private static final Logger logger = Logger.getLogger(ProxyServer.class.getName());

    private String proxyIP;
    private int proxyPort;
    private String mainServerIP;
    private int mainServerPort;
    private int rateLimit;
    private int blockThreshold;
    private String discordWebhook;
    private Connection dbConnection;
    private Set<String> whitelist;
    private Set<String> blacklist;

    public void start() {
        loadConfig();
        runProxy();
    }

    private void loadConfig() {
        Properties config = new Properties();
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            config.load(reader);
            proxyIP = config.getProperty("ProxyServer.ip");
            proxyPort = Integer.parseInt(config.getProperty("ProxyServer.port"));
            mainServerIP = config.getProperty("MainServer.ip");
            mainServerPort = Integer.parseInt(config.getProperty("MainServer.port"));
            rateLimit = Integer.parseInt(config.getProperty("RateLimit.initial_limit"));
            blockThreshold = Integer.parseInt(config.getProperty("RateLimit.block_threshold"));
            discordWebhook = config.getProperty("DiscordWebhook.url");
            String dbUrl = String.format("jdbc:mysql://%s:%s/%s",
                    config.getProperty("MySQL.host"),
                    config.getProperty("MySQL.port"),
                    config.getProperty("MySQL.dbname"));
            String dbUser = config.getProperty("MySQL.user");
            String dbPassword = config.getProperty("MySQL.password");
            dbConnection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);

            // Load whitelist and blacklist from config
            String[] whitelistIPs = config.getProperty("Whitelist.ips").split(",");
            whitelist = new HashSet<>(Set.of(whitelistIPs));

            String[] blacklistIPs = config.getProperty("Blacklist.ips").split(",");
            blacklist = new HashSet<>(Set.of(blacklistIPs));

        } catch (IOException | SQLException e) {
            logger.log(Level.SEVERE, "Failed to load configuration", e);
        }
    }

    private void runProxy() {
        try (DatagramSocket socket = new DatagramSocket(proxyPort, InetAddress.getByName(proxyIP))) {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (true) {
                socket.receive(packet);
                String ip = packet.getAddress().getHostAddress();

                if (whitelist.contains(ip)) {
                    forwardTraffic(packet, mainServerIP, mainServerPort);
                    continue;
                } else if (blacklist.contains(ip)) {
                    logger.log(Level.INFO, "Blocked traffic from blacklisted IP: " + ip);
                    continue;
                }

                int severity = detectDDoS(ip);
                String action = mitigateDDoS(ip, severity);

                logDDoSAttack(ip, severity, action);
                sendDiscordAlert(ip, severity, action);

                forwardTraffic(packet, mainServerIP, mainServerPort);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to run proxy server", e);
        }
    }

    private int detectDDoS(String ip) {
        int requestCount = getRequestCount(ip);
        if (requestCount > blockThreshold) {
            return 3;
        } else if (requestCount > rateLimit) {
            return 2; 
        } else {
            return 1; 
        }
    }

    private String mitigateDDoS(String ip, int severity) {
        switch (severity) {
            case 3:
                blacklist.add(ip);
                return "Blocked IP";
            case 2:
                return "Rate limited";
            default:
                return "No action";
        }
    }

    private void logDDoSAttack(String ip, int severity, String action) {
        JSONObject json = new JSONObject();
        json.put("ip", ip);
        json.put("severity", severity);
        json.put("start_time", new java.util.Date().toString());
        json.put("action", action);

        try {
            URL url = new URL("http://localhost:8080/log_ddos");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
                writer.write(json.toString());
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                logger.log(Level.SEVERE, "Failed to log DDoS attack, response code: " + responseCode);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to send DDoS log to Go server", e);
        }
    }

    private void sendDiscordAlert(String ip, int severity, String action) {
        JSONObject json = new JSONObject();
        json.put("content", "DDoS Alert");
        json.put("embeds", new JSONObject[]{
                new JSONObject()
                        .put("title", "DDoS Attack Detected")
                        .put("description", "Details of the attack")
                        .put("fields", new JSONObject[]{
                                new JSONObject().put("name", "IP").put("value", ip),
                                new JSONObject().put("name", "Severity").put("value", severity),
                                new JSONObject().put("name", "Action").put("value", action)
                        })
        });

        try {
            URL url = new URL(discordWebhook);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
                writer.write(json.toString());
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                logger.log(Level.SEVERE, "Failed to send Discord alert, response code: " + responseCode);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to send Discord alert", e);
        }
    }

    private void forwardTraffic(DatagramPacket packet, String mainServerIP, int mainServerPort) {
        try (DatagramSocket mainSocket = new DatagramSocket()) {
            DatagramPacket forwardPacket = new DatagramPacket(
                    packet.getData(), packet.getLength(), InetAddress.getByName(mainServerIP), mainServerPort);
            mainSocket.send(forwardPacket);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to forward traffic", e);
        }
    }

    private int getRequestCount(String ip) {
        return 100; 
    }

    public void addToWhitelist(String ip) {
        whitelist.add(ip);
        logger.log(Level.INFO, "Added IP " + ip + " to whitelist");
    }

    public void removeFromWhitelist(String ip) {
        whitelist.remove(ip);
        logger.log(Level.INFO, "Removed IP " + ip + " from whitelist");
    }

    public void addToBlacklist(String ip) {
        blacklist.add(ip);
        logger.log(Level.INFO, "Added IP " + ip + " to blacklist");
    }

    public void removeFromBlacklist(String ip) {
        blacklist.remove(ip);
        logger.log(Level.INFO, "Removed IP " + ip + " from blacklist");
    }

    public void printWhitelist() {
        logger.log(Level.INFO, "Whitelist: " + whitelist);
    }

    public void printBlacklist() {
        logger.log(Level.INFO, "Blacklist: " + blacklist);
    }

    public void printConfig() {
        logger.log(Level.INFO, "Proxy IP: " + proxyIP);
        logger.log(Level.INFO, "Proxy Port: " + proxyPort);
        logger.log(Level.INFO, "Main Server IP: " + mainServerIP);
        logger.log(Level.INFO, "Main Server Port: " + mainServerPort);
        logger.log(Level.INFO, "Rate Limit: " + rateLimit);
        logger.log(Level.INFO, "Block Threshold: " + blockThreshold);
        logger.log(Level.INFO, "Discord Webhook: " + discordWebhook);
    }

    public void printHelp() {
        logger.log(Level.INFO, "Available commands:");
        logger.log(Level.INFO, "addwhitelist <ip>: Add an IP to the whitelist");
        logger.log(Level.INFO, "removewhitelist <ip>: Remove an IP from the whitelist");
        logger.log(Level.INFO, "addblacklist <ip>: Add an IP to the blacklist");
        logger.log(Level.INFO, "removeblacklist <ip>: Remove an IP from the blacklist");
        logger.log(Level.INFO, "printwhitelist: Print all whitelisted IPs");
        logger.log(Level.INFO, "printblacklist: Print all blacklisted IPs");
        logger.log(Level.INFO, "printconfig: Print the current configuration");
        logger.log(Level.INFO, "help: Print this help message");
    }
}
