//! Пример 01: эмулятор Modbus TCP-устройства (сервер).
//!
//! Запуск: `cargo run --example 01-modbus-emulator [127.0.0.1:15502]`
//!
//! Симулирует СИКН-устройство: 4 holding-регистра (масса, плотность, вода,
//! примеси). Регистры «живые» — фоновая задача тикает раз в 100 мс и
//! увеличивает массу, как настоящее измерительное устройство.
//! API tokio-modbus 0.17 потыкано автором (спайк): сервер = `Server::new` +
//! `on_connected`-замыкание с `accept_tcp_connection` (см. design Risks).

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

/// «Память» устройства: 4 регистра.
#[derive(Debug)]
struct RegisterBank {
    mass: u32,
    density: u32,
    water: u32,
    sediment: u32,
}

impl RegisterBank {
    fn snapshot(&self) -> Vec<u16> {
        vec![
            self.mass as u16,
            self.density as u16,
            self.water as u16,
            self.sediment as u16,
        ]
    }
}

/// Modbus-сервис: умеет только читать holding-регистры (D6: TCP, без RTU).
struct Emulator {
    bank: Arc<RwLock<RegisterBank>>,
}

impl tokio_modbus::server::Service for Emulator {
    type Request = Request<'static>;
    type Response = Response;
    type Exception = ExceptionCode;
    type Future = future::Ready<Result<Self::Response, Self::Exception>>;

    fn call(&self, req: Self::Request) -> Self::Future {
        use tokio_modbus::{Request as R, Response as Resp};
        let res = match req {
            R::ReadHoldingRegisters(addr, cnt) => {
                if addr > 3 || addr + cnt > 4 {
                    return future::ready(Err(ExceptionCode::IllegalDataAddress));
                }
                let bank = self.bank.read().unwrap();
                let snap = bank.snapshot();
                Resp::ReadHoldingRegisters(snap[addr as usize..(addr + cnt) as usize].to_vec())
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
        .unwrap_or_else(|| "127.0.0.1:15502".to_owned())
        .parse()?;

    let bank = Arc::new(RwLock::new(RegisterBank {
        mass: 1000,
        density: 850,
        water: 50,
        sediment: 5,
    }));

    // Регистры «меняются сами»: тик каждые 100 мс.
    {
        let bank = bank.clone();
        tokio::spawn(async move {
            loop {
                tokio::time::sleep(Duration::from_millis(100)).await;
                bank.write().unwrap().mass += 1;
            }
        });
    }

    let listener = TcpListener::bind(addr).await?;
    println!("эмулятор Modbus TCP на {addr} (масса растёт каждые 100 мс)");

    let server = Server::new(listener);
    // Фабрика сервиса: на каждое подключение — сервис с тем же банком.
    // Клонируем фабрику ДО async-блока: замыкание on_connected должно быть
    // Fn (serve вызывает его много раз), а async move захватил бы её по move.
    let new_service = move |_addr: SocketAddr| -> std::io::Result<Option<Emulator>> {
        Ok(Some(Emulator { bank: bank.clone() }))
    };
    let on_connected = |stream: TcpStream, socket_addr: SocketAddr| {
        let new_service = new_service.clone();
        async move { accept_tcp_connection(stream, socket_addr, new_service) }
    };
    let on_process_error = |err| eprintln!("ошибка соединения: {err}");

    server.serve(&on_connected, on_process_error).await?;
    Ok(())
}
