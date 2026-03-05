use common::ipc::OmeCommand;
use std::fs::{self, File, OpenOptions};
use std::io::{BufRead, BufReader, BufWriter, Write};
use std::path::Path;

/// Append-only event journal for WAL (Write-Ahead Log) pattern.
/// All commands are journaled before being forwarded to ME,
/// enabling future crash recovery by replaying the journal.
pub struct EventJournal {
    writer: BufWriter<File>,
    path: String,
}

impl EventJournal {
    pub fn new(path: &str) -> Self {
        // Ensure parent directory exists
        if let Some(parent) = Path::new(path).parent() {
            let _ = fs::create_dir_all(parent);
        }

        let file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(path)
            .unwrap_or_else(|e| panic!("Failed to open journal at {}: {}", path, e));

        eprintln!("EventJournal: Opened at {}", path);

        Self {
            writer: BufWriter::new(file),
            path: path.to_string(),
        }
    }

    /// Write a command to the journal (JSONL format, flushed immediately)
    pub fn write(&mut self, cmd: &OmeCommand) {
        let mut data = serde_json::to_vec(cmd).unwrap();
        data.push(b'\n');
        self.writer.write_all(&data).unwrap();
        self.writer.flush().unwrap();
    }

    /// Read all journaled commands (for future recovery use)
    pub fn read_all(&self) -> Vec<OmeCommand> {
        let file = match File::open(&self.path) {
            Ok(f) => f,
            Err(_) => return Vec::new(),
        };

        let reader = BufReader::new(file);
        let mut commands = Vec::new();

        for line in reader.lines() {
            if let Ok(l) = line {
                if let Ok(cmd) = serde_json::from_str::<OmeCommand>(&l) {
                    commands.push(cmd);
                }
            }
        }

        commands
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use common::{Order, Side, OrderType, TimeInForce};

    #[test]
    fn should_write_and_read_journal() {
        let path = "/tmp/exchange-journal-test";
        let _ = std::fs::remove_file(path);

        let mut journal = EventJournal::new(path);

        // Write an order command
        let order = Order {
            order_id: 1,
            user_id: 101,
            symbol_id: 1,
            price: 50_000,
            qty: 100_000_000,
            side: Side::Buy,
            timestamp: 0,
            order_type: OrderType::Limit,
            time_in_force: TimeInForce::GTC,
            trigger_price: 0,
        };
        journal.write(&OmeCommand::Order(order));

        // Write a deposit command
        journal.write(&OmeCommand::Deposit {
            user_id: 101,
            currency_id: 2,
            amount: 1_000_000,
            seq_id: 2,
        });

        // Write a cancel command
        journal.write(&OmeCommand::Cancel {
            user_id: 101,
            order_id: 1,
            symbol_id: 1,
            seq_id: 3,
        });

        // Read all back
        let commands = journal.read_all();
        assert_eq!(commands.len(), 3);

        // Verify first command is an Order
        match &commands[0] {
            OmeCommand::Order(o) => {
                assert_eq!(o.order_id, 1);
                assert_eq!(o.user_id, 101);
            }
            _ => panic!("Expected Order command"),
        }

        // Verify second command is a Deposit
        match &commands[1] {
            OmeCommand::Deposit { user_id, amount, .. } => {
                assert_eq!(*user_id, 101);
                assert_eq!(*amount, 1_000_000);
            }
            _ => panic!("Expected Deposit command"),
        }

        // Verify third command is a Cancel
        match &commands[2] {
            OmeCommand::Cancel { order_id, .. } => {
                assert_eq!(*order_id, 1);
            }
            _ => panic!("Expected Cancel command"),
        }

        let _ = std::fs::remove_file(path);
    }
}
