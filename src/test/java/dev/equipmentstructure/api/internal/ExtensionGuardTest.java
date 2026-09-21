package dev.equipmentstructure.api.internal;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtensionGuardTest {
    @Test void failureUsesFallbackAndDoesNotDisableFutureCalls() {
        var guard = new ExtensionGuard<String>("Test extension");
        assertEquals(-1, guard.call("id", () -> { throw new IllegalStateException("deliberate"); }, -1));
        assertEquals(2, guard.call("id", () -> 2, -1));
    }

    @Test void reentryUsesFallbackAndReleasesItsThreadState() {
        var guard = new ExtensionGuard<String>("Test extension");
        AtomicInteger calls = new AtomicInteger();
        assertEquals(-1, guard.call("id", () -> guard.call("id", calls::incrementAndGet, -1), -2));
        assertEquals(0, calls.get());
        assertEquals(1, guard.call("id", calls::incrementAndGet, -1));
    }
}
