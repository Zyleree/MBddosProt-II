
## Features

- Proxy server functionality to forward traffic to the main server
- DDoS attack detection based on configurable rate limits and thresholds
- Mitigation actions: blocking IPs, rate-limiting, and whitelisting/blacklisting
- Logging of DDoS attacks to a MySQL database
- Discord webhook integration for sending attack alerts
- Console commands for managing whitelists and blacklists
- Configuration file for easy setup and customization

## Requirements

- Java 8 or higher
- MySQL database

## Setup

1. Clone the repository or download the source code.
2. Configure the `config/config.ini` file with your desired settings.
3. Set up a MySQL database and configure the connection details in the config file.
4. Obtain a Discord webhook URL and update the config file with the URL.
5. Build and run the Java application.

## Configuration

The `config/config.ini` file contains various settings for the proxy server. Here's an overview of the available options:

- `ProxyServer.ip`: The IP address to bind the proxy server to.
- `ProxyServer.port`: The port number for the proxy server.
- `MainServer.ip`: The IP address of the main server.
- `MainServer.port`: The port number of the main server.
- `RateLimit.initial_limit`: The initial rate limit for traffic before considering it a potential attack.
- `RateLimit.block_threshold`: The threshold for blocking an IP address due to excessive requests.
- `DiscordWebhook.url`: The URL of the Discord webhook to send attack alerts.
- `MySQL.host`: The hostname or IP address of the MySQL server.
- `MySQL.port`: The port number of the MySQL server.
- `MySQL.dbname`: The name of the MySQL database.
- `MySQL.user`: The username for the MySQL database.
- `MySQL.password`: The password for the MySQL database.
- `Whitelist.ips`: A comma-separated list of IP addresses to whitelist.
- `Blacklist.ips`: A comma-separated list of IP addresses to blacklist.

## Console Commands

The proxy server provides several console commands for managing whitelists, blacklists, and viewing configuration details:

- `addwhitelist <ip>`: Add an IP address to the whitelist.
- `removewhitelist <ip>`: Remove an IP address from the whitelist.
- `addblacklist <ip>`: Add an IP address to the blacklist.
- `removeblacklist <ip>`: Remove an IP address from the blacklist.
- `printwhitelist`: Print all whitelisted IP addresses.
- `printblacklist`: Print all blacklisted IP addresses.
- `printconfig`: Print the current configuration.
- `help`: Print the list of available commands.

## Contributing

Contributions are welcome! If you find any issues or have suggestions for improvements, please open an issue or submit a pull request.

## License

This project is licensed under the [ GNU GENERAL PUBLIC LICENSE](LICENSE).
