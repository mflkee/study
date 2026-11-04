# Заявка 4. При экспорте проекта в Linux вызывается Windows-команда `systeminfo`

> Черновик для HelpDesk. Продукт: **MasterSCADA 4D**, галочка **«БЕТА-ТЕСТ MS4D DT2.0»**, тип — Ошибка.

**Тема:**

```
DT – Linux – экспорт проекта (SaveCompressed) – вызывается Windows-команда systeminfo – Win32Exception (2)
```

**Версия ПО:** `1.3.10.33235 (2.0.4_RC_20260622.1)`.
**ОС:** `Astra Linux Special Edition 1.8.6` («Воронеж», KVM/QEMU) — по логу видно, что работа шла
именно в Astra (пути проекта `/usr/share/MPSSoft/MasterSCADA4D_DT_2.0_RC/PostgreSqlProjects/…`).
Ошибка не зависит от «железа»: команды `systeminfo` в Linux нет ни в Astra, ни в других сборках.

**Описание задачи.** Выгружаем учебный проект во внешний файл, чтобы передать его в поддержку
(`Проект → Экспорт проекта`). Экспорт нужен по требованиям самого бетта-теста («приложите проект
на файлообменник»).

**Шаги воспроизведения.**

1. Открыть проект «Быстрый старт» (хранилище — PostgreSQL).
2. `Проект → Экспорт проекта` (сжатый экспорт, `SaveCompressed`).
3. Посмотреть журнал среды: `~/.config/MPSSoft/MasterSCADA4D_DT_2.0_RC/sys_log/<дата>.log`.

**Ожидаемый результат.** Экспорт проходит без ошибок в журнале; сбор сведений о системе
кроссплатформенный (`uname` / `lsb_release` / чтение `/etc/os-release`) либо не выполняется на Linux.

**Фактический результат.** Файл экспорта создаётся, но при экспорте среда пытается выполнить
Windows-команду `systeminfo`, получает исключение и пишет ошибку в журнал среды:

```
10-13 14:14:19.454 [5] . [Error] System.ComponentModel.Win32Exception (2): An error occurred trying to
start process 'systeminfo' with working directory '/opt/MasterSCADA 4D RC/resources/MasterSCADA4D_Server'.
No such file or directory
   at System.Diagnostics.Process.ForkAndExecProcess(ProcessStartInfo startInfo, String resolvedFilename,
      String[] argv, String[] envp, String cwd, Boolean setCredentials, UInt32 userId, UInt32 groupId,
      UInt32[] groups, Int32& stdinFd, Int32& stdoutFd, Int32& stderrFd, Boolean usesTerminal,
      Boolean throwOnNoExec)
   at System.Diagnostics.Process.StartCore(ProcessStartInfo startInfo)
   at System.Diagnostics.Process.Start(ProcessStartInfo startInfo)
   at InSAT.Library.Helpers.ProcessHlp.StartProcessWithoutOutput(ProcessStartInfo startInfo, String& output,
      String& errors) in F:\azure-agents\MS4\SRV-BUILD-1\_work\268\s\ms4-src\Source\InSAT\InSAT.Library\Helpers\ProcessHlp.cs:line 48
10-13 14:14:19.457 [5] . ExportProject SaveCompressed
      (Name=Быстрый старт TargetName= oldPath=/usr/share/MPSSoft/MasterSCADA4D_DT_2.0_RC/PostgreSqlProjects/...)
```

**Побочный эффект, который нас беспокоит больше самой ошибки в журнале.** Этот же сбор сведений
о системе наполняет **отчёт об ошибках** (`Сервис → Отчет об ошибках`). В нашей сборке файл
`sysinfo.txt` внутри `ErrorReports.zip` вместо информации о системе содержит ровно это исключение
(то же сообщение `Win32Exception (2) … 'systeminfo' … No such file or directory`). То есть
в Linux-сборке отчёт об ошибках приходит в поддержку **без сведений о системе**, и версию ОС
приходится писать в тексте заявки вручную.

**Вложения.**

📎 `sys_log_full.log` — журнал среды за 2026-10-13, **снят на Astra**: в нём пути
`/usr/share/MPSSoft/MasterSCADA4D_DT_2.0_RC/PostgreSqlProjects/…`, строки `ExportProject SaveCompressed`
и полный стек `Win32Exception`.
📎 `ErrorReports.zip` — отчёт, сформированный средой (`sysinfo.txt` внутри = это исключение,
а не сведения о системе).
📎 Скриншот результата экспорта (файл создан) — по запросу. Видео не прикладываем: дефект
полностью виден в журнале.
