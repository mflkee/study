package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверка того, что защита сделана именно монитором метода, а не блоком внутри.
 *
 * <p>Модификатор {@code synchronized} в байт-коде — это то же самое, что показывает
 * {@code javap -p}: он относится ко всему телу метода, поэтому «забыть захватить» или не отпустить
 * монитор в {@code finally} физически невозможно. Проверка идёт по модификатору, а не по
 * договорённости.
 */
class DataCollectorSynchronizationTest {

    private static final List<String> REQUIRED_METHODS =
            List.of("collectItem", "incrementProcessed", "isAlreadyProcessed");

    @Test
    @DisplayName("Три метода из текста задания объявлены synchronized")
    void requiredMethodsAreSynchronized() {
        for (String name : REQUIRED_METHODS) {
            Method method = findMethod(name);

            assertThat(Modifier.isSynchronized(method.getModifiers()))
                    .as("метод %s должен быть synchronized — иначе защита держится на дисциплине, "
                            + "а не на модификаторе в байт-коде", name)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Каждый публичный метод сборщика синхронизирован — обойти защиту нечем")
    void everyPublicMethodIsSynchronized() {
        List<String> unsynchronized = Stream.of(DataCollector.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !Modifier.isSynchronized(method.getModifiers()))
                .map(Method::getName)
                .toList();

        assertThat(unsynchronized)
                .as("публичный несинхронизированный метод — это незакрытая дверь к разделяемому состоянию")
                .isEmpty();
    }

    @Test
    @DisplayName("Защита не построена на ReentrantLock: это зона ДЗ 5")
    void protectionUsesMonitorOnly() {
        List<String> fieldTypes = Stream.of(DataCollector.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .toList();

        assertThat(fieldTypes)
                .as("в ДЗ 4 защита обязана быть монитором; ReentrantLock/Condition — предмет ДЗ 5")
                .noneSatisfy(type -> assertThat(type).contains("ReentrantLock"))
                .noneSatisfy(type -> assertThat(type).contains("Condition"));
    }

    private static Method findMethod(String name) {
        return Stream.of(DataCollector.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("метод не найден: " + name));
    }
}
