package util;

import java.util.concurrent.atomic.AtomicInteger;

public class IdGenerator {
    private static final AtomicInteger counter = new AtomicInteger(0);

    public static String next(String prefix) {
        return prefix + "-" + counter.incrementAndGet();
    }
}
