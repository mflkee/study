//! Эмулятор Modbus TCP-устройства (СИКН): 2 регистра (масса, плотность).
//!
//! Запуск: `cargo run --bin emulator [127.0.0.1:15504]`

use std::{
    future,
    net::SocketAddr,
    sync::{Arc, RwLock},
    time::Duration,
};
use tokio::net::{TcpListener, TcpStream};
use tokio_modbus::{
    prelude::*,
    server::tcp::{accept_tcp_connection, Server},
};

#[derive(Debug, Default)]
struct Bank {
    mass: u32,
    density: u32,
}

impl Bank {
    fn snap(&self) -> Vec<u16> {
        vec![self.mass as u16, self.density as u16]
    }
}

struct Emulator {
    bank: Arc<RwLock<Bank>>,
}

impl tokio_modbus::server::Service for Emulator {
    type Request = Request<'static>;
    type Response = Response;
    type Exception = ExceptionCode;
    type Future = future::Ready<Result<Self::Response, Self::Exception>>;

    fn call(&self, req: Self::Request) -> Self::Future {
        use tokio_modbus::{Request as R, Response as Resp};
        let res = match req {
            R::ReadHoldingRegisters(0, _) => {
                let b = self.bank.read().unwrap().snap();
                Resp::ReadHoldingRegisters(b)
            }
            _ => return future::ready(Err(ExceptionCode::IllegalFunction)),
        };
        future::ready(Ok(res))
    }
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let addr: SocketAddr = std::env::args()
        .nth(1)
        .unwrap_or_else(|| "127.0.0.1:15504".into())
        .parse()?;
    let bank = Arc::new(RwLock::new(Bank {
        mass: 1000,
        density: 850,
    }));

    // Регистры «живут»: масса растёт (модель расхода), плотность колеблется.
    {
        let bank = bank.clone();
        tokio::spawn(async move {
            let mut i = 0u32;
            loop {
                tokio::time::sleep(Duration::from_millis(200)).await;
                i += 1;
                let mut b = bank.write().unwrap();
                b.mass += 1;
                b.density = 850 + (i % 20);
            }
        });
    }

    let listener = TcpListener::bind(addr).await?;
    println!("эмулятор на {addr}");
    let server = Server::new(listener);
    let new_service = move |_a: SocketAddr| -> std::io::Result<Option<Emulator>> {
        Ok(Some(Emulator { bank: bank.clone() }))
    };
    let on_connected = |stream: TcpStream, sa: SocketAddr| {
        let ns = new_service.clone();
        async move { accept_tcp_connection(stream, sa, ns) }
    };
    server.serve(&on_connected, |e| eprintln!("{e}")).await?;
    Ok(())
}
