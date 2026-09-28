import OutboxSDK

@MainActor
final class OutboxViewModel {
    private let facade: OutboxFacade
    private var subscription: OutboxSubscription?
    private(set) var pendingCount = 0

    init(facade: OutboxFacade) { self.facade = facade }

    func start() {
        subscription?.close()
        subscription = facade.observe { [weak self] snapshot in
            MainActor.assumeIsolated {
                self?.pendingCount = Int(snapshot.pendingCount)
            }
        }
    }

    func send() { facade.sendPending() }

    func close() {
        subscription?.close()
        subscription = nil
        facade.close()
    }
}
