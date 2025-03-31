# simple.tcl - Простой пример сценария для ns

# Создание объектов
set ns [new Simulator]

# Создание узлов
set n0 [$ns node]
set n1 [$ns node]

# Создание соединения между узлами
$ns duplex-link $n0 $n1 1Mb 10ms DropTail

# Создание TCP-агента и привязка его к узлу n0
set tcp [new Agent/TCP]
$ns attach-agent $n0 $tcp

# Создание приемника для TCP-соединения
set sink [new Agent/TCPSink]
$ns attach-agent $n1 $sink

# Создание приложения для передачи данных
set ftp [new Application/FTP]
$ftp attach-agent $tcp

# Соединение агентов
$ns connect $tcp $sink

# Настройка начала передачи данных (например, начало передачи через 1 секунду)
$ns at 1.0 "$ftp start"

# Запуск симуляции
$ns run
