fn placeholder<T>() -> T {
    panic!("Замените placeholder() правильным значением");
}

use std::sync::{Arc, Mutex, mpsc};
use std::thread;

#[test]
fn thread_join_returns_result() {
    let handle = thread::spawn(|| 21 * 2);

    assert_eq!(handle.join().unwrap(), placeholder::<i32>());
}

#[test]
fn channel_transfers_value_between_threads() {
    let (tx, rx) = mpsc::channel();

    thread::spawn(move || {
        tx.send("done").expect("message should be sent");
    });

    assert_eq!(rx.recv().unwrap(), placeholder::<&str>());
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

    assert_eq!(*counter.lock().unwrap(), placeholder::<i32>());
}
