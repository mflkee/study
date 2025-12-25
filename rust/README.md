# Rust Learning Workspace

This repo is now a single Cargo workspace so every example can be run with the same commands.

## Layout
- `crates/data_types` — basics: primitives, compound types, loops, conditionals, errors, functions
- `crates/ownership` — ownership and borrowing drills
- `crates/slices` — slicing examples and tests
- `crates/io` — standard input/output examples
- `crates/cmp` — comparisons and ordering
- `crates/temperature-converter` — small Axum + htmx demo
- `crates/guessing_game` — guessing game from the book
- `crates/gen_fibonacci` — Fibonacci practice
- `crates/mathmagic-in-rust` — math/logic experiments

All crates live under `crates/` so they can be run from the workspace root with the same commands.

Add new lessons by creating a new crate inside `crates/` and listing its path in `Cargo.toml` under `[workspace].members`. Only the root `Cargo.lock` is tracked; per-crate locks are removed.

## Usage
- Run any example: `cargo run -p <package-name>`
  - examples: `cargo run -p ownership`, `cargo run -p guessing_game`, `cargo run -p temperature-converter`
  - basics (`data_types` crate) per-topic bins:
    - `cargo run -p data_types --bin primitives`
    - `cargo run -p data_types --bin compound`
    - `cargo run -p data_types --bin ownership`
    - `cargo run -p data_types --bin conditionals`
    - `cargo run -p data_types --bin loops`
    - `cargo run -p data_types --bin func_modules`
    - `cargo run -p data_types --bin errors`
- Run tests for one crate: `cargo test -p <package-name>`
- List workspace packages: `cargo metadata --format-version 1 | jq '.packages[].name'`
