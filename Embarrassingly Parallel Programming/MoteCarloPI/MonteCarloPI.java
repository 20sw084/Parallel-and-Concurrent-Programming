package MoteCarloPI;

import java.util.concurrent.*;
import java.util.concurrent.ThreadLocalRandom;

public class MonteCarloPI {

    private static final long TOTAL_DARTS = 10_000_000L;
    private static final int THREADS = 4;
    private static final int REPETITIONS = 10;

    private static final int[] CHUNK_SIZES = {
        1_000, 5_000, 10_000, 25_000, 50_000,
        100_000, 250_000, 500_000, 1_000_000
    };

    // Sequential Monte Carlo simulation
    private static long sequential(long totalDarts) {
        long inside = 0;

        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (long i = 0; i < totalDarts; i++) {
            double x = random.nextDouble(-1.0, 1.0);
            double y = random.nextDouble(-1.0, 1.0);

            if (x * x + y * y <= 1.0) {
                inside++;
            }
        }

        return inside;
    }

    // Count darts inside the circle for one chunk
    private static long countChunk(long darts) {
        long inside = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (long i = 0; i < darts; i++) {
            double x = random.nextDouble(-1.0, 1.0);
            double y = random.nextDouble(-1.0, 1.0);

            if (x * x + y * y <= 1.0) {
                inside++;
            }
        }

        return inside;
    }

    // Parallel Monte Carlo simulation with a fixed thread pool
    private static long parallel(
            long totalDarts,
            int chunkSize,
            ExecutorService executor) throws Exception {

        CompletionService<Long> completion =
                new ExecutorCompletionService<>(executor);

        long submittedTasks = 0;

        for (long start = 0; start < totalDarts;
             start += chunkSize) {

            final long darts =
                    Math.min((long) chunkSize, totalDarts - start);

            completion.submit(() -> countChunk(darts));
            submittedTasks++;
        }

        long inside = 0;

        for (long i = 0; i < submittedTasks; i++) {
            inside += completion.take().get();
        }

        return inside;
    }

    private static double estimatePi(long inside, long total) {
        return 4.0 * inside / total;
    }

    public static void main(String[] args) throws Exception {

        System.out.println("Monte Carlo Estimation of PI");
        System.out.println("Total darts: " + TOTAL_DARTS);
        System.out.println("Worker threads: " + THREADS);
        System.out.println();

        ExecutorService executor =
                Executors.newFixedThreadPool(THREADS);

        try {
            // Warm-up runs
            sequential(TOTAL_DARTS / 10);
            parallel(TOTAL_DARTS / 10, 10_000, executor);

            // Sequential benchmark
            long sequentialTotal = 0;
            long sequentialInside = 0;

            for (int r = 0; r < REPETITIONS; r++) {
                long start = System.nanoTime();

                long inside = sequential(TOTAL_DARTS);

                long end = System.nanoTime();

                sequentialTotal += end - start;
                sequentialInside = inside;
            }

            double sequentialMs =
                    sequentialTotal / (double) REPETITIONS / 1_000_000.0;

            System.out.printf("Sequential PI: %.10f%n",
                    estimatePi(sequentialInside, TOTAL_DARTS));
            System.out.printf("Sequential average: %.3f ms%n%n",
                    sequentialMs);

            System.out.printf(
                "%-12s | %-12s | %-10s | %-12s%n",
                "Chunk size", "Parallel ms", "Speedup", "PI estimate");

            double bestTime = Double.MAX_VALUE;
            int bestChunk = -1;

            for (int chunkSize : CHUNK_SIZES) {

                // Warm up this chunk configuration
                parallel(TOTAL_DARTS / 10, chunkSize, executor);

                long parallelTotal = 0;
                long parallelInside = 0;

                for (int r = 0; r < REPETITIONS; r++) {
                    long start = System.nanoTime();

                    long inside = parallel(
                            TOTAL_DARTS, chunkSize, executor);

                    long end = System.nanoTime();

                    parallelTotal += end - start;
                    parallelInside = inside;
                }

                double parallelMs =
                    parallelTotal / (double) REPETITIONS / 1_000_000.0;

                double speedup = sequentialMs / parallelMs;

                System.out.printf(
                    "%-12d | %12.3f | %8.2fx | %.10f%n",
                    chunkSize,
                    parallelMs,
                    speedup,
                    estimatePi(parallelInside, TOTAL_DARTS));

                if (parallelMs < bestTime) {
                    bestTime = parallelMs;
                    bestChunk = chunkSize;
                }
            }

            System.out.println();
            System.out.println("Best tested chunk size: " + bestChunk);
            System.out.printf("Best average parallel time: %.3f ms%n",
                    bestTime);

        } finally {
            executor.shutdown();
        }
    }
}
