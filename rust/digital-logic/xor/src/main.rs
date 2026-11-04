fn main() {
    // === гейты ===

    fn nand(a: bool, b: bool) -> bool {
        !(a & b)
    }

    fn xor(a: bool, b: bool) -> bool {
        let n1 = nand(a, b); // NOT (a AND b)
        let n2 = nand(n1, a); // a AND ... из одних NAND
        let n3 = nand(n1, b);
        nand(n2, n3)
    }

    // bool -> 1/0: таблица истинности так читается в разы легче
    fn bit(v: bool) -> u8 {
        u8::from(v)
    }

    // === таблица ===
    // Ширина колонок: a, b, nand, xor — задана ОДИН раз,
    // и строки, и рамка берут её отсюда, поэтому разъехаться не могут.
    const A: usize = 3;
    const B: usize = 3;
    const N: usize = 6;
    const X: usize = 5;

    // {:^w$} — «отцентрировать в w символов»; w берём из константы

    /// одна ячейка: значение, отцентрированное в колонке шириной `w`
    fn cell(v: impl std::fmt::Display, w: usize) -> String {
        format!("{:^w$}", v, w = w)
    }

    fn row(c: [impl std::fmt::Display; 4]) {
        let [a, b, n, x] = c;
        let cells = [cell(a, A), cell(b, B), cell(n, N), cell(x, X)];
        println!("│{}│", cells.join("│"));
    }

    /// горизонтальная линия рамки той же ширины, что и колонки
    fn line(left: char, mid: char, right: char) {
        let bars: Vec<String> = [A, B, N, X].iter().map(|w| "─".repeat(*w)).collect();
        println!("{left}{}{right}", bars.join(&mid.to_string()));
    }

    line('┌', '┬', '┐');
    row(["a", "b", "nand", "xor"]);
    line('├', '┼', '┤');
    for a in [false, true] {
        for b in [false, true] {
            row([bit(a), bit(b), bit(nand(a, b)), bit(xor(a, b))]);
        }
    }
    line('└', '┴', '┘');
}
