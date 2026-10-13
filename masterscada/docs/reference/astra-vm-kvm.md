# Astra Linux SE 1.8.6 в KVM (стенд для MasterSCADA 4D DT 2.0)

> Зачем: DT 2.0 официально поддерживает **Astra Linux SE 1.7x/1.8x**, а Linux-редактор — это
> первая кроссплатформенная среда вендора, поэтому она сырая (см. `ustanovka-arch-niri.md`).
> VM на Astra позволяет отделить **баги сборки** от **багов окружения** (Wayland, неподдерживаемая ОС).

## Что нужно (проверено на archlinux-notebook, 2026-10-13)

| Что | Значение |
|---|---|
| Хост | Arch Linux + niri; CPU i7-1255U (12 потоков), 23 ГБ RAM, 213 ГБ свободно |
| KVM | `/dev/kvm` есть; стоят `qemu-base`, `libvirt`, `virt-manager`, `virt-viewer`, `spice-gtk`, `virtiofsd`, `edk2-ovmf` |
| Образ | `~/Downloads/astra/installation-1.8.6.39-17.06.2026_11.39.iso` (8 ГБ), sha256 сверен ✅ |
| Пул libvirt `astra` | `~/Downloads/astra` (там же лежит ISO) |
| Графика | только Intel iGPU → **GPU passthrough невозможен**; используем `virtio-vga` + Spice |

## Создание VM (одна команда, без GUI)

```bash
virt-install --connect qemu:///system \
  --name ms4d-astra \
  --description "Astra Linux SE 1.8.6 для MasterSCADA 4D DT 2.0" \
  --memory 8192 --vcpus 6 \
  --cpu host-passthrough \
  --machine q35 \
  --disk path=/var/lib/libvirt/images/ms4d-astra.qcow2,size=80,format=qcow2,bus=virtio,cache=none,io=native \
  --cdrom ~/Downloads/astra/installation-1.8.6.39-17.06.2026_11.39.iso \
  --network network=default,model=virtio \
  --graphics spice,listen=127.0.0.1 \
  --video virtio --sound none --channel spicevmc \
  --osinfo detect=on,require=off \
  --noautoconsole --wait 0
```

Консоль: `virt-viewer --connect qemu:///system --wait ms4d-astra` (или virt-manager → «Открыть консоль»).

Полезные команды:

```bash
virsh -c qemu:///system list --all                 # состояние
virsh -c qemu:///system domblklist ms4d-astra      # диски (vda = система, sda = ISO)
virsh -c qemu:///system change-media ms4d-astra sda --eject   # вынуть ISO после установки
virsh -c qemu:///system shutdown ms4d-astra        # корректно выключить
```

## Установка Astra (выбор на шагах мастера)

| Шаг | Что выбрать | Почему |
|---|---|---|
| Язык / раскладка | Русский; раскладка с переключением (например, `Alt+Shift`) | привычно |
| **Режим защищённости** | минимальный (**«Воронеж»**), МКЦ/«замок» — **не включать** | нужна совместимость, а не режим секретности |
| Разметка диска | **автоматическая, весь диск** (`vda`, 80 ГБ), без LVM/шифрования | простой стенд |
| Пользователь | пользователь + пароль (например `ms4d`), root-пароль задать | DT запускается от обычного пользователя |
| Имя компьютера | `ms4d-astra` | порядок |
| Загрузчик | по умолчанию (GRUB) | — |

После установки: вынуть ISO (`change-media … --eject`) и перезагрузить — грузимся с диска.

## Дальше: DT 2.0 внутри VM

1. Передать `.deb` в VM (проще всего — раздать с хоста по HTTP в сети libvirt `default`,
   хост для гостя — `192.168.122.1`):

   ```bash
   # на хосте:
   cd ~/Downloads && python3 -m http.server 8000 --bind 192.168.122.1
   # в VM:
   wget http://192.168.122.1:8000/MasterSCADA4D_2.0.4_Install.deb
   sudo apt-get install ./MasterSCADA4D_2.0.4_Install.deb
   ```

2. Проверка установки (по справке `ustanovka_dt_linux.html`): в меню «Разработка» появляется
   **MasterSCADA 4D 2.0**, из терминала — `/opt/MasterSCADA 4D 2.0/MasterSCADA4D_2.0`.
3. PostgreSQL: либо **внутри VM** (`sudo apt-get install postgresql`, см. справку
   `ust_postgresql_linux.html`), либо подключить VM к учебному стенду на хосте — тогда стенд
   поднимается с публикацией на адрес моста:

   ```bash
   PG_BIND=192.168.122.1 docker compose -f course/00-orientation/infra/docker-compose.yml up -d
   # в DT: сервер 192.168.122.1, порт 15444, БД/пользователь/пароль — course
   ```

## Что проверяем в VM (сравнение с Arch-стендом)

- сохраняется ли **размер окна** («Расположение → Ширина/Высота» → есть ли значение в модели);
- работает ли **ПКМ-меню** и «Открыть в панели веток»;
- ищет ли **фильтр дерева**;
- не теряются ли **размеры элементов** при `Ctrl+X`/`Ctrl+V`;
- воспроизводится ли `Fatal error` → белый экран.

Если в VM (поддерживаемая Astra) всё это работает — значит дефекты были связаны с **окружением**;
если воспроизводится — это **баги самой сборки** (их и отправлять в поддержку).
