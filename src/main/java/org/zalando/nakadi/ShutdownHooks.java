package org.zalando.nakadi;

import java.io.Closeable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShutdownHooks {

    private static final KeySetView<Runnable, Boolean> HOOKS = ConcurrentHashMap.newKeySet();
    private static final KeySetView<Runnable, Boolean> DONE_HOOKS = ConcurrentHashMap.newKeySet();
    private static final Logger LOG = LoggerFactory.getLogger(ShutdownHooks.class);

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ShutdownHooks::onNodeShutdown));
    }
    
    private static void onNodeShutdown() {
        HOOKS.forEach(ShutdownHooks::runSafely);
    }

    private static void runSafely(final Runnable hook) {
        final boolean done = DONE_HOOKS.contains(hook);
        if (done) {
            return;
        }

        final boolean claimed = DONE_HOOKS.add(hook);
        if (!claimed) {
            return;
        }

        runWithExceptionLogging(hook);
    }

    private static void runWithExceptionLogging(Runnable hook) {
        try {
            hook.run();
        } catch (final RuntimeException ex) {
            LOG.warn("Failed to call on shutdown hook for {}", hook, ex);
        }
    }

    public static Closeable addHook(final Runnable runnable) {
        HOOKS.add(runnable);
        return () -> removeHook(runnable);
    }

    private static void removeHook(final Runnable runnable) {
        HOOKS.remove(runnable);
    }


}
