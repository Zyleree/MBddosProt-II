package main

import (
    "bytes"
    "database/sql"
    "encoding/json"
    "log"
    "net/http"
    "time"

    _ "github.com/go-sql-driver/mysql"
)

type DDoSAttack struct {
    IP        string `json:"ip"`
    Severity  int    `json:"severity"`
    StartTime string `json:"start_time"`
    Action    string `json:"action"`
}

func main() {
    db, err := sql.Open("mysql", "user:password@tcp(localhost:3306)/ddos")
    if err != nil {
        log.Fatal(err)
    }
    defer db.Close()

    http.HandleFunc("/log_ddos", func(w http.ResponseWriter, r *http.Request) {
        var attack DDoSAttack
        if err := json.NewDecoder(r.Body).Decode(&attack); err != nil {
            http.Error(w, err.Error(), http.StatusBadRequest)
            return
        }

        stmt, err := db.Prepare("INSERT INTO ddos_attacks(ip, severity, start_time, action_taken) VALUES (?, ?, ?, ?)")
        if err != nil {
            http.Error(w, err.Error(), http.StatusInternalServerError)
            return
        }
        defer stmt.Close()

        _, err = stmt.Exec(attack.IP, attack.Severity, attack.StartTime, attack.Action)
        if err != nil {
            http.Error(w, err.Error(), http.StatusInternalServerError)
            return
        }

        sendDiscordAlert(attack)

        w.WriteHeader(http.StatusOK)
    })

    log.Fatal(http.ListenAndServe(":8080", nil))
}

func sendDiscordAlert(attack DDoSAttack) {
    webhookURL := "https://discord.com/api/webhooks/YOUR_WEBHOOK_URL"
    payload := map[string]interface{}{
        "content": "DDoS Alert",
        "embeds": []map[string]interface{}{
            {
                "title":       "DDoS Attack Detected",
                "description": "Details of the attack",
                "fields": []map[string]interface{}{
                    {"name": "IP", "value": attack.IP},
                    {"name": "Severity", "value": attack.Severity},
                    {"name": "Action", "value": attack.Action},
                },
                "timestamp": time.Now().Format(time.RFC3339),
            },
        },
    }

    payloadBytes, err := json.Marshal(payload)
    if err != nil {
        log.Println("Error marshaling JSON:", err)
        return
    }

    resp, err := http.Post(webhookURL, "application/json", bytes.NewBuffer(payloadBytes))
    if err != nil {
        log.Println("Error sending Discord alert:", err)
        return
    }
    defer resp.Body.Close()

    if resp.StatusCode != http.StatusOK {
        log.Println("Failed to send Discord alert, status code:", resp.StatusCode)
    }
}
