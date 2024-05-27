CREATE TABLE IF NOT EXISTS ddos_attacks (
    id INT AUTO_INCREMENT PRIMARY KEY,
    ip VARCHAR(45) NOT NULL,
    severity INT NOT NULL,
    start_time DATETIME NOT NULL,
    action_taken VARCHAR(100) NOT NULL
);
