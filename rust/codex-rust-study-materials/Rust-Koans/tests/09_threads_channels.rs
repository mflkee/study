fn todo<T>() -> T {
    std::todo!("Замените todo() правильным значением");
}

use std::sync::{Arc, Mutex, mpsc};
use std::thread;

#[test]
fn thread_join_returns_result() {
    let handle = thread::spawn(|| 21 * 2);
    let expected: i32 = todo();

    assert_eq!(handle.join().unwrap(), expected);
}

#[test]
fn channel_transfers_value_between_threads() {
    let (tx, rx) = mpsc::channel();

    thread::spawn(move || {
        tx.send("done").expect("message should be sent");
    });
    let expected: &str = todo();

    assert_eq!(rx.recv().unwrap(), expected);
}

#[test]
fn arc_mutex_can_share_mutable_state() {
    let counter = Arc::new(Mutex::new(0_i32));
    let mut handles = Vec::new();

    for _ in 0..3 {
        let counter = Arc::clone(&counter);
        handles.push(thread::spawn(move || {
            let mut guard = counter.lock().expect("mutex should not be poisoned");
            *guard += 1;
        }));
    }

    for handle in handles {
        handle.join().expect("thread should finish");
    }
    let expected: i32 = todo();

    assert_eq!(*counter.lock().unwrap(), expected);
}
