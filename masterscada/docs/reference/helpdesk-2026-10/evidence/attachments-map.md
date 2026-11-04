# Карта вложений по заявкам

> Стенд для всех заявок — **Astra Linux SE 1.8.6 в KVM**. Отчёты об ошибках и логи берём **в госте**
> Astra (`Сервис → Отчет об ошибках` и `~/.config/MPSSoft/…/sys_log`), видео пишем с окна VM.
> Arch + niri — только упоминание в тексте, вложения оттуда не идут. Порядок работ — `../protocol-astra.md`.

## Что прикладываем к какой заявке

| Заявка | Вложение | Откуда берётся | Размер |
|---|---|---|---|
| 1. Astra, падение | `ErrorReports.zip` — отчёт, снятый **в гостя Astra** после падения | `Сервис → Отчет об ошибках` в среде внутри VM | ~3–5 МБ |
| 1 | `sys_log/<дата>.log` за день падений (из гостя) | `~/.config/MPSSoft/MasterSCADA4D_DT_2.0_RC/sys_log/` | ~0,5–13 МБ |
| 1 | `libvirt-ms4d-astra.xml` | `virsh -c qemu:///system dumpxml ms4d-astra > libvirt-ms4d-astra.xml` | ~5 КБ |
| 1 | `01-astra-crash.mp4` | см. `../video-scenario.md` | ~10–30 МБ |
| 2. Размер окна | `ErrorReports.zip` (**внутри — архив проекта** «Быстрый старт») | там же, **из гостя Astra** | 3,4 МБ |
| 2 | `sql-project-state.sql` + `.out` + `sql-snimok-2026-10-16-polnaya-vypiska.out` | `evidence/`, воспроизводится командой | 45 КБ |
| 2 | `02-okno-razmer.mp4` | см. раскадровку (окно VM) | ~5 МБ |
| 3. Ctrl+X/V | `ErrorReports.zip` (тот же, из Astra) | — | 3,4 МБ |
| 3 | `sql-project-state.sql` + `.out` + `sql-snimok-2026-10-16-polnaya-vypiska.out` | `evidence/` | 45 КБ |
| 3 | `03-ctrl-x-v.mp4` | см. раскадровку (окно VM) | ~5 МБ |
| 4. `systeminfo` | `sys_log_full.log` — журнал среды за 2026-10-13 **с Astra** (пути `/usr/share/MPSSoft/…`) | уже есть — `attachments/sys_log_full.log` (письмо-первоисточник) | 2,1 МБ |
| 4 | `ErrorReports.zip` — **`sysinfo.txt` внутри = та самая ошибка** | — | 3,4 МБ |

> Большие файлы (лог 2,1 МБ, отчёт 3,4 МБ, видео) в git **не коммитим** —
> см. `attachments/.gitignore`. HelpDesk может не принять крупный файл: тогда выкладываем
> на файлообменник со сроком хранения **>3 месяцев** (требование их инструкции) — Яндекс.Диск/Nextcloud.

## Письмо-первоисточник (откуда взяты файлы)

| Что | Значение |
|---|---|
| Тема | `MasterSCADA 4D DT 2.0 (2.0.4 RC, 1.3.10.33235): 4 воспроизводимых дефекта, включая самопроизвольное завершение среды на Astra Linux SE 1.8.6` |
| Дата | 2026-10-13 12:11 UTC, от `makeevgleb86rus@gmail.com` (копия `study@masterscada.ru`) |
| Вложения письма | `sys_log_full.log` (2,96 МБ), `ErrorReports.zip` (4,59 МБ по MIME, на диске 3,4 МБ) |
| Ответ вендора | 2026-10-13 13:15 UTC, `study@masterscada.ru` (Соколова А.А.): регистрация в HelpDesk + заявки; правила — в приложении `Предрелизное тестирование DT 2.0 (1) (1).docx` |

Перекачать вложения из Gmail в этот каталог (uid письма — `34656`, папка `[Gmail]/Вся почта`):

```bash
mkdir -p evidence/attachments
node evidence/fetch-gmail-attachment.js 34656 "[Gmail]/Вся почта" evidence/attachments
```

Скрипт берёт логин/пароль из того же места, что MCP-сервер Gmail
(`~/Documents/finance_manager/config.yaml` + `~/.config/zsh/secrets.zsh`), и сохраняет
**все** вложения письма (штатный MCP-инструмент выкачивает только «чековые» расширения).

## Что внутри `ErrorReports.zip` (проверено вскрытием)

```
_2026_10_13.log                         1,0 МБ   журнал среды за день
sysinfo.txt                               891 Б   вместо сведений о системе — исключение Win32Exception 'systeminfo'
Быстрый старт.zip                       3,6 МБ   АРХИВ САМОГО ПРОЕКТА (18 файлов, 28 МБ распакованного)
ProjectsServiceData/Ide/IdeProjectState.json   рабочие панели редактора
```

Вывод для заявок: **архив проекта прикладывать отдельно не нужно** — он уже внутри отчёта об
ошибках, то есть требование «приложите проект» закрывается одним файлом. И второй вывод:
`sysinfo.txt` в Linux-сборке нерабочий (это дефект 4), версию ОС надо писать в тексте заявки руками.
