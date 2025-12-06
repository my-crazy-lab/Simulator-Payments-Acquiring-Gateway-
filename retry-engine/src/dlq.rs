use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DLQEntry {
    pub transaction_id: String,
    pub psp_name: String,
    pub payload: Vec<u8>,
    pub attempt_count: u32,
    pub last_error: String,
    pub timestamp_ms: u64,
}

pub struct DeadLetterQueue {
    entries: Arc<Mutex<HashMap<String, DLQEntry>>>,
}

impl DeadLetterQueue {
    pub fn new() -> Self {
        Self {
            entries: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    /// Add an entry to the DLQ
    pub fn add_entry(&self, entry: DLQEntry) {
        let mut entries = self.entries.lock().unwrap();
        entries.insert(entry.transaction_id.clone(), entry);
    }

    /// Check if a transaction is in the DLQ
    pub fn contains(&self, transaction_id: &str) -> bool {
        let entries = self.entries.lock().unwrap();
        entries.contains_key(transaction_id)
    }

    /// Get an entry from the DLQ
    pub fn get_entry(&self, transaction_id: &str) -> Option<DLQEntry> {
        let entries = self.entries.lock().unwrap();
        entries.get(transaction_id).cloned()
    }

    /// Get all entries
    pub fn get_all_entries(&self) -> Vec<DLQEntry> {
        let entries = self.entries.lock().unwrap();
        entries.values().cloned().collect()
    }

    /// Remove an entry from the DLQ
    pub fn remove_entry(&self, transaction_id: &str) -> Option<DLQEntry> {
        let mut entries = self.entries.lock().unwrap();
        entries.remove(transaction_id)
    }

    /// Get the count of entries
    pub fn count(&self) -> usize {
        let entries = self.entries.lock().unwrap();
        entries.len()
    }
}

impl Default for DeadLetterQueue {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_add_and_retrieve_entry() {
        let dlq = DeadLetterQueue::new();
        let entry = DLQEntry {
            transaction_id: "txn_123".to_string(),
            psp_name: "stripe".to_string(),
            payload: vec![1, 2, 3],
            attempt_count: 5,
            last_error: "Connection timeout".to_string(),
            timestamp_ms: 1000,
        };

        dlq.add_entry(entry.clone());

        assert!(dlq.contains("txn_123"));
        let retrieved = dlq.get_entry("txn_123").unwrap();
        assert_eq!(retrieved.transaction_id, "txn_123");
        assert_eq!(retrieved.attempt_count, 5);
    }

    #[test]
    fn test_remove_entry() {
        let dlq = DeadLetterQueue::new();
        let entry = DLQEntry {
            transaction_id: "txn_456".to_string(),
            psp_name: "adyen".to_string(),
            payload: vec![],
            attempt_count: 3,
            last_error: "PSP error".to_string(),
            timestamp_ms: 2000,
        };

        dlq.add_entry(entry);
        assert_eq!(dlq.count(), 1);

        let removed = dlq.remove_entry("txn_456");
        assert!(removed.is_some());
        assert_eq!(dlq.count(), 0);
        assert!(!dlq.contains("txn_456"));
    }
}
