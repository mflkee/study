//! Build-скрипт (запускается cargo до компиляции крейта).
//!
//! Что делает embuild (см. его документацию и `.cargo/config.toml`):
//! - `CfgArgs::output_propagated` — «проталкивает» флаги конфигурации ESP-IDF
//!   в сборку: например, `--cfg espidf_time64` (time_t 64 бита) и `--cfg
//!   esp_idf_*`, которые esp-idf-sys требует для своих биндингов;
//! - `LinkArgs::output_propagated` — передаёт линковщику пути к библиотекам
//!   IDF (ldproxy, скрипты линковки) и порядок, в котором они связываются.
//!
//! Без этого скрипта ни `build-std` для target `xtensa-esp32s3-espidf`, ни
//! линковка прошивки просто не сработают.
fn main() {
    embuild::build::CfgArgs::output_propagated("ESP_IDF").unwrap();
    embuild::build::LinkArgs::output_propagated("ESP_IDF").unwrap();
    // Пересобираем build-скрипт при его изменении.
    println!("cargo:rerun-if-changed=build.rs");
}