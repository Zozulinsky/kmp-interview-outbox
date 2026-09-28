# OutboxSDK — KMP SDK исходящих сообщений

Kotlin Multiplatform SDK очереди исходящих сообщений: приложение добавляет короткие сообщения, SDK надёжно сохраняет их через `OutboxStorage` и отправляет по одному, в порядке добавления, через `OutboxTransport`. Состояние очереди — снимок в `StateFlow`: список ожидающих сообщений, признак отправки и ошибка. Для iOS модуль собирается в framework `OutboxSDK`, Swift-код работает с ним через фасад `OutboxFacade`.

## Состав

```text
src/commonMain/kotlin/outbox/Outbox.kt        Message, OutboxState, OutboxStorage, OutboxTransport, Outbox
src/iosMain/kotlin/outbox/OutboxFacade.kt     OutboxFacade, OutboxSubscription, OutboxSnapshot — фасад для Swift
src/commonTest/kotlin/outbox/OutboxTest.kt    MemoryStorage, ControlledTransport и тест очереди
ios/OutboxViewModel.swift                     Пример потребителя framework: владелец экрана на Swift
```

Требования: Kotlin 2.4.0, Gradle 9.5.0, kotlinx.coroutines 1.10.2, JDK 21; для iOS — Xcode с iOS SDK.

## Использование

Очередь одна на сессию и живёт дольше экрана:

```kotlin
val outbox = Outbox(storage, transport)
outbox.enqueue(id = "m1", text = "hello")   // сохраняет сообщение в очередь
outbox.flush()                             // suspend: отправляет сохранённые сообщения по одному
```

Экран на Swift при каждом открытии получает новый фасад над той же очередью:

```swift
let facade = OutboxFacade(outbox: outbox)
let subscription = facade.observe { snapshot in
    // главный поток: snapshot.pendingCount, snapshot.sending, snapshot.error
}
facade.sendPending()
// при окончательном закрытии экрана
subscription.close()
facade.close()
```

Полный пример — [ios/OutboxViewModel.swift](ios/OutboxViewModel.swift).

## Контракт

SDK хранит короткие сообщения и отправляет их по одному, в порядке добавления. Записи неизменяемы; пользователь может добавить следующую во время отправки предыдущей. Одна очередь обслуживает одну неизменную сессию. Все вызовы её API идут последовательно на главном диспетчере; `send` приостанавливается и не блокирует поток.

- **Хранилище.** `OutboxStorage.write` синхронно и надёжно сохраняет список и не бросает ошибок; `read` возвращает независимый снимок. Неподтверждённая операция должна пережить перезапуск процесса.
- **Транспорт.** `OutboxTransport.send` может приостановиться. Нормальный возврат подтверждает сохранение на сервере. При timeout или отмене неизвестно, принял ли сервер сообщение. Отмена вызывающей coroutine должна оставаться отменой, не превращаясь в обычное завершение `flush` или сетевую ошибку для UI.
- **Сервер.** Сервер атомарно сохраняет операцию и её ключ. Повтор одинаковых ключа и содержимого создаёт одно сообщение; новый ключ создаёт новую операцию. Иное содержимое со старым ключом сервер отвергает. Ответ может потеряться после принятия сообщения сервером. `enqueue` получает уникальный ID новой логической операции.
- **Состояние.** `StateFlow` представляет текущий снимок очереди, признак отправки и ошибку. Это не журнал всех переходов: медленный подписчик вправе пропустить промежуточные состояния. Данные хранилища остаются источником для восстановления после перезапуска.
- **Владение и закрытие.** `flush` принадлежит вызывающей coroutine. `OutboxFacade` владеет своим scope и запускаемыми им работами на `Dispatchers.Main.immediate`. Закрытие `OutboxSubscription` отменяет только наблюдение; закрытие фасада — его наблюдения и отправку. Сохранённая очередь живёт дольше экрана. При повторном открытии создаётся новый фасад над той же очередью/хранилищем. Swift-владелец явно вызывает `close` при окончательном закрытии экрана.
- **Вне этой версии.** SwiftUI, HTTP, дисковое хранилище, фоновая доставка iOS и авторизация.

## Тесты

В `commonTest` есть `MemoryStorage`, `ControlledTransport` — управляемый транспорт для тестов — и тест `successfulSend`: обычная успешная отправка. Их можно расширять или добавить свой фейк. Тесты общие и выполняются на JVM и на iOS Simulator.

## Сборка и проверка

Команды из корня проекта, нужны JDK 21 и Xcode:

```sh
./gradlew jvmTest
./gradlew iosSimulatorArm64Test
./gradlew linkDebugFrameworkIosSimulatorArm64
```

`jvmTest` и `iosSimulatorArm64Test` выполняют commonTest на JVM и на iOS Simulator. Framework появляется в `build/bin/iosSimulatorArm64/debugFramework/OutboxSDK.framework`.

После изменений Kotlin сначала пересоберите framework из текущих исходников, затем проверьте Swift-потребителя против него:

```sh
./gradlew linkDebugFrameworkIosSimulatorArm64
swiftc -swift-version 6 -typecheck -parse-as-library \
  -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
  -target arm64-apple-ios16.0-simulator \
  -F build/bin/iosSimulatorArm64/debugFramework \
  ios/OutboxViewModel.swift
```

`typecheck` проверяет типы и импорт именно собранного framework; вызовы Swift → Kotlin и отмена через мост им не исполняются.
