use cursive::traits::*;
use cursive::views::{Dialog, EditView, LinearLayout, SelectView, TextView};
use cursive::Cursive;
use cursive::CursiveExt;

fn main() {
    let mut siv = Cursive::default();

    // Ввод трёх измерений вискозиметра СИКН
    let visco_inputs = LinearLayout::vertical()
        .child(TextView::new("Measurements on SIKN viscometer (3):"))
        .child(EditView::new().with_name("v1").fixed_width(10))
        .child(EditView::new().with_name("v2").fixed_width(10))
        .child(EditView::new().with_name("v3").fixed_width(10));

    // Ввод трёх лабораторных измерений
    let lab_inputs = LinearLayout::vertical()
        .child(TextView::new("Laboratory measurements (3):"))
        .child(EditView::new().with_name("l1").fixed_width(10))
        .child(EditView::new().with_name("l2").fixed_width(10))
        .child(EditView::new().with_name("l3").fixed_width(10));

    // Выбор температуры
    let temp_select = SelectView::new().popup()
        .item("Above 15°C", true)
        .item("Below or equal 15°C", false)
        .with_name("temp15");

    // Сборка формы
    let form = Dialog::new()
        .title("Viscosity Error Calculator")
        .content(
            LinearLayout::vertical()
                .child(visco_inputs)
                .child(lab_inputs)
                .child(TextView::new("Lab temperature:"))
                .child(temp_select),
        )
        .button("Calculate", |s| {
            // Получаем значения вручную
            let v1 = s.call_on_name("v1", |v: &mut EditView| v.get_content())
                .unwrap().parse::<f64>().unwrap_or(0.0);
            let v2 = s.call_on_name("v2", |v: &mut EditView| v.get_content())
                .unwrap().parse::<f64>().unwrap_or(0.0);
            let v3 = s.call_on_name("v3", |v: &mut EditView| v.get_content())
                .unwrap().parse::<f64>().unwrap_or(0.0);

            let visco_avg = (v1 + v2 + v3) / 3.0;

            let l1 = s.call_on_name("l1", |v: &mut EditView| v.get_content())
                .unwrap().parse::<f64>().unwrap_or(0.0);
            let l2 = s.call_on_name("l2", |v: &mut EditView| v.get_content())
                .unwrap().parse::<f64>().unwrap_or(0.0);
            let l3 = s.call_on_name("l3", |v: &mut EditView| v.get_content())
                .unwrap().parse::<f64>().unwrap_or(0.0);

            let lab_avg = (l1 + l2 + l3) / 3.0;

            let temp_above = s.call_on_name("temp15", |view: &mut SelectView<bool>| {
                *view.selection().unwrap()
            }).unwrap();

            // Абсолютная погрешность СИКН
            let err_visco = if (0.5..=10.0).contains(&visco_avg) {
                0.05 * visco_avg + 0.30
            } else if (10.0..=100.0).contains(&visco_avg) {
                0.04 * visco_avg + 1.33
            } else {
                f64::NAN
            };

            // Абсолютная погрешность лаборатории
            let err_lab = if temp_above {
                0.0178 * lab_avg
            } else {
                0.0238 * lab_avg
            };

            let msg = format!(
                "SIKN Viscometer avg: {:.2}\n\
Absolute error (SIKN): {:.4}\n\
Lab avg: {:.2}\n\
Absolute error (Lab): {:.4}\n\
Lab temp >15°C: {}",
                visco_avg, err_visco, lab_avg, err_lab, temp_above
            );

            s.add_layer(Dialog::info(msg));
        })
        .button("Quit", |s| s.quit());

    siv.add_layer(form);
    siv.run();
}
