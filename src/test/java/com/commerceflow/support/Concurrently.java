package com.commerceflow.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Runs the same action on many threads that all start at the same instant, so requests really overlap. */
public final class Concurrently {

    private Concurrently() {
    }

    @FunctionalInterface
    public interface Task<T> {
        T run(int index) throws Exception;
    }

    /** Returns one result per thread, in thread order. */
    public static <T> List<T> run(int threads, Task<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch allReady = new CountDownLatch(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    allReady.countDown();
                    startGun.await();
                    return task.run(index);
                }));
            }
            if (!allReady.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("threads did not all start");
            }
            startGun.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
