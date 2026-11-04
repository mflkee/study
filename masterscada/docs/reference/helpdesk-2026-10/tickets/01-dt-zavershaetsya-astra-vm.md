# Заявка 1. Среда разработки самопроизвольно завершается (Astra Linux SE 1.8.6, KVM)

> Черновик для HelpDesk: <https://support.iek-digital.ru/>. Одна проблема — одна заявка.
> Продукт: **MasterSCADA 4D**, галочка **«БЕТА-ТЕСТ MS4D DT2.0»**, тип обращения — Ошибка.

**Тема (формат ТП «место — источник — краткое описание»):**

```
DT – Linux (Astra SE 1.8.6, KVM/virtio-vga) – среда сама завершается через 1,5–4 минуты – FATAL: GPU process isn't usable. Goodbye
```

**Версия ПО:** `1.3.10.33235 (2.0.4_RC_20260622.1)`, установка `/opt/MasterSCADA 4D RC/MasterSCADA4D_2.0_RC`
(Linux, пакет `.deb` от вендора).

**ОС:** `Astra Linux Special Edition 1.8.6` (установочный образ `installation-1.8.6.39-17.06.2026_11.39.iso`,
режим защищённости **«Воронеж»**, минимальный — МКЦ/«замок» не включали). Гость KVM в libvirt,
машина `ms4d-astra`.

**Стенд (всё, что нужно для воспроизведения):**

| Параметр | Значение |
|---|---|
| Хост | Arch Linux, i7-1255U (12 потоков), 23 ГБ RAM, `/dev/kvm` |
| qemu/libvirt | `qemu-base`, `libvirt`, `virt-manager`, `spice-gtk`, `edk2-ovmf` |
| Гость | 6 vCPU, 8 ГБ RAM, `--cpu host-passthrough`, `--machine q35` |
| Диск | 80 ГБ qcow2, шина `virtio`, `cache=none, io=native` |
| Сеть | сеть libvirt `default`, модель `virtio` |
| Графика | **`--video virtio`** (virtio-vga) + **`spice,listen=127.0.0.1`**, консоль `virt-viewer` |
| GPU-passthrough | невозможен: у хоста только Intel iGPU |
| БД проекта | PostgreSQL 16 на хосте (`192.168.122.1:15444`), проект «Быстрый старт» |

Команда создания VM (готова к повторению):

```bash
virt-install --connect qemu:///system \
  --name ms4d-astra --memory 8192 --vcpus 6 --cpu host-passthrough --machine q35 \
  --disk path=/var/lib/libvirt/images/ms4d-astra.qcow2,size=80,format=qcow2,bus=virtio,cache=none,io=native \
  --cdrom ~/Downloads/astra/installation-1.8.6.39-17.06.2026_11.39.iso \
  --network network=default,model=virtio \
  --graphics spice,listen=127.0.0.1 --video virtio --sound none --channel spicevmc \
  --osinfo detect=on,require=off --noautoconsole --wait 0
```

**Описание задачи.** Проверяем бета-сборку DT 2.0 на официально поддерживаемой ОС (Astra SE 1.8.6)
на учебном проекте «Быстрый старт»: открываем проект, работаем в редакторе окон и запускаем
проект на внутреннем RT. Нужно убедиться, что среда стабильна при продолжительной работе.

**Шаги воспроизведения (важен способ запуска).**

1. Запустить графическую сессию Astra, войти пользователем (не root).
2. **Вариант A (падает стабильно):** запустить среду **по SSH**, без графической сессии:
   ```bash
   ssh ms4d@<ip-гостя>
   setsid /opt/MasterSCADA\ 4D\ RC/MasterSCADA4D_2.0_RC > /tmp/ms4d.log 2>&1 &
   ```
   (на стенде — `ssh ms4d@192.168.122.199` с хоста; вывод в `/tmp/ms4d.log`, по нему видно FATAL)
3. Далее либо работать в редакторе, либо не выполнять никаких действий — при закрытом окне проекта
   падение всё равно наступает.
4. **Вариант B (для сравнения):** то же самое, но запуск **из меню графической сессии** — в наших
   наблюдениях от 2026-10-13 среда так прожила ~70 минут.

**Ожидаемый результат.** Среда работает стабильно при любом штатном способе запуска; при
невозможности поднять GPU-процесс — продолжает работу с программным рендерингом, а не завершается.

**Фактический результат.** При запуске по SSH среда **завершается сама** через 1,5–4 минуты, без
действий пользователя, без диалога и без отчёта об ошибке. В stdout (`/tmp/ms4d.log`):

```
ERROR:zygote_communication_linux.cc(290)] Failed to send GetTerminationStatus message to zygote
ERROR:network_service_instance_impl.cc(601)] Network service crashed, restarting service.
ERROR:gpu_process_host.cc(987)] GPU process launch failed: error_code=1002
FATAL:gpu_data_manager_impl_private.cc(448)] GPU process isn't usable. Goodbye.
```

Также при старте в stdout: `libva error: /usr/lib/x86_64-linux-gnu/dri/virtio_gpu_drv_video.so init failed`.

**Результаты замеров (Astra SE 1.8.6, гост KVM, 2026-10-13):**

| Способ запуска | Прожила | Результат |
|---|---|---|
| из меню графической сессии | ~70 мин | среда работала, проект открывался |
| по SSH, без флагов | ~100–160 с | FATAL: GPU process isn't usable |
| по SSH, `--disable-gpu` | ~100 с | то же (флаг не помог) |
| по SSH, `--no-sandbox --disable-gpu` | ~240 с | дольше, но падение осталось |

**Важно для локализации.** Наблюдение от 2026-10-13: NAT-доступ в интернет в госте заработал в 14:35,
падения начались в 14:38 — то есть помимо способа запуска (без графической сессии) возможная причина
связана с сетью гостя. Мы отдельно проверяем обе версии и приложим результат. Также отмечаем, что на
втором стенде (Arch, XWayland) среда не падает вовсе.

**Дополнительно (для локализации дефекта).** На втором стенде — Arch Linux + niri (Wayland),
среда запускается как X11-клиент через XWayland, **падения нет** (среда живёт часами). Там же
воспроизводятся дефекты 2 и 3 (см. отдельные заявки). То есть падение похоже связано именно со
связкой **virtio-vga + Spice** в виртуализации, а не с Linux-сборкой вообще.

**Вложения.**

📎 `/tmp/ms4d.log` — вывод среды при падении (полная цепочка `zygote` → `Network service crashed` →
`GPU process launch failed: error_code=1002` → `FATAL: GPU process isn't usable. Goodbye`).
📎 `ErrorReports.zip` — «Сервис → Отчет об ошибках», снятый **внутри гостя Astra** (внутри лог среды,
`sysinfo.txt` и архив проекта; сам факт жёсткого падения оболочки в отчёте не отражается — в журнале
среды будет только `Start application`).
📎 `sys_log/<дата>.log` — журнал среды из `~/.config/MPSSoft/MasterSCADA4D_DT_2.0_RC/sys_log/` за день падений.
📎 `libvirt-ms4d-astra.xml` — `virsh dumpxml ms4d-astra` (точная конфигурация гостя: virtio-vga + Spice).
📎 Таблица замеров «прожила / результат» по способам запуска (в тексте заявки выше).
📎 Видео `01-astra-crash.mp4` (~3 мин, окно VM) — среда закрывается сама, кнопки «выход» никто не нажимал.

**Что уже проверяли и что просим уточнить.** Права `chrome-sandbox` выставлены в 4755 `root:root`
(иначе среда не стартует вовсе). Флаги `--disable-gpu` и `--no-sandbox --disable-gpu` падение не
устраняют. Просим подтвердить, известна ли проблема с virtio-vga/Spice в KVM и какие именно данные
нужны от нас, чтобы её локализовать.

**Внутренние заметки по стенду** (для нас, не для вендора): `~/obs_main/🔧 work/masterscada-astra/`
(`01-ustanovka-masterscada-v-astra.md` — замеры, `00-astra-v-kvm.md` — сам стенд).

**Дополнительно можем** предоставить доступ к стенду (гость Astra в KVM) и повторить на созвоне
в удобное время.
