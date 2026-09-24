//! Пример 06: CLI «Каталог оборудования» — интерфейс упражнения 01.
//!
//! Запуск (после решения упражнения или применения решения из solutions/):
//!   cargo run --example 06-katalog-cli -- list-lines
//!   cargo run --example 06-katalog-cli -- list-devices
//!   cargo run --example 06-katalog-cli -- show M-01-001
//!   cargo run --example 06-katalog-cli -- add-device 1 density_meter D-01-099 MVD-9
//!
//! Пока в заготовке упражнения стоят todo!() — команды падают с паникой
//! «not yet implemented». Это нормально: интерфейс CLI уже готов,
//! ученик дописывает только SQL-слой в exercises/src/ex01_katalog.rs.
//!
//! add-device добавляет строку в справочник (не в сид) — проверяется глазами.

use anyhow::Result;
use pg_course_module_04::ex01_katalog::{
    format_device_row, format_line, insert_device, last_measurement, list_devices, list_lines,
    DeviceKind,
};
use pg_course_module_04::new_pool;

fn usage() -> ! {
    eprintln!(
        "использование:\n  {prog} list-lines\n  {prog} list-devices\n  \
         {prog} show <tag>\n  {prog} add-device <line_id> <type> <tag> [model]\n  \
         (type: mass_meter | density_meter | moisture_meter)",
        prog = "cargo run --example 06-katalog-cli --"
    );
    std::process::exit(2);
}

#[tokio::main]
async fn main() -> Result<()> {
    let pool = new_pool(5).await?;
    let args: Vec<String> = std::env::args().skip(1).collect();

    match args.as_slice() {
        [cmd] if cmd == "list-lines" => {
            for line in list_lines(&pool).await? {
                println!("{}", format_line(&line));
            }
        }
        [cmd] if cmd == "list-devices" => {
            for dev in list_devices(&pool).await? {
                println!("{}", format_device_row(&dev));
            }
        }
        [cmd, tag] if cmd == "show" => match last_measurement(&pool, tag).await? {
            Some(m) => println!(
                "{}: ts={} value={} quality={}",
                tag, m.ts, m.value, m.quality
            ),
            None => println!("{tag}: измерений нет"),
        },
        [cmd, line_id, kind, tag] if cmd == "add-device" => {
            let kind = DeviceKind::parse(kind)?;
            let dev = insert_device(&pool, line_id.parse()?, kind, tag, None).await?;
            println!("добавлено: {dev:?}");
        }
        [cmd, line_id, kind, tag, model] if cmd == "add-device" => {
            let kind = DeviceKind::parse(kind)?;
            let dev = insert_device(&pool, line_id.parse()?, kind, tag, Some(model)).await?;
            println!("добавлено: {dev:?}");
        }
        _ => usage(),
    }

    Ok(())
}
