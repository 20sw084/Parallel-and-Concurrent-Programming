
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.DoubleUnaryOperator;

public class trapezoidIntegration {

    static final int THREADS = 4;

    // Sequential trapezoidal integration
    static double integrateSequential(
            DoubleUnaryOperator f,
            double a, double b, double resolution) {

        long n = (long) Math.ceil((b - a) / resolution);
        double h = (b - a) / n;
        double sum = 0.0;

        for (long i = 0; i < n; i++) {
            double x0 = a + i * h;
            double x1 = a + (i + 1) * h;

            sum += (f.applyAsDouble(x0)
                  + f.applyAsDouble(x1)) / 2.0;
        }

        return sum * h;
    }

    // Parallel trapezoidal integration
    static double integrateParallel(
            DoubleUnaryOperator f,
            double a, double b, double resolution,
            ExecutorService pool, long chunkSize)
            throws InterruptedException, ExecutionException {

        long n = (long) Math.ceil((b - a) / resolution);
        double h = (b - a) / n;

        List<Future<Double>> tasks = new ArrayList<>();

        for (long start = 0; start < n; start += chunkSize) {
            final long from = start;
            final long to = Math.min(start + chunkSize, n);

            tasks.add(pool.submit(() -> {
                double partialSum = 0.0;

                for (long i = from; i < to; i++) {
                    double x0 = a + i * h;
                    double x1 = a + (i + 1) * h;

                    partialSum += (
                        f.applyAsDouble(x0)
                        + f.applyAsDouble(x1)
                    ) / 2.0;
                }

                return partialSum * h;
            }));
        }

        double result = 0.0;

        for (Future<Double> task : tasks) {
            result += task.get();
        }

        return result;
    }

    static double benchmarkSequential(
            DoubleUnaryOperator f,
            double a, double b, double resolution,
            int repetitions) {

        long totalNanos = 0;
        double result = 0.0;

        for (int i = 0; i < repetitions; i++) {
            long start = System.nanoTime();

            result = integrateSequential(f, a, b, resolution);

            totalNanos += System.nanoTime() - start;
        }

        // Prevent the benchmark result from being unused.
        if (Double.isNaN(result)) {
            throw new ArithmeticException("Invalid result");
        }

        return totalNanos / (repetitions * 1_000_000.0);
    }

    static double benchmarkParallel(
            DoubleUnaryOperator f,
            double a, double b, double resolution,
            ExecutorService pool, long chunkSize,
            int repetitions)
            throws InterruptedException, ExecutionException {

        long totalNanos = 0;
        double result = 0.0;

        for (int i = 0; i < repetitions; i++) {
            long start = System.nanoTime();

            result = integrateParallel(
                f, a, b, resolution, pool, chunkSize
            );

            totalNanos += System.nanoTime() - start;
        }

        if (Double.isNaN(result)) {
            throw new ArithmeticException("Invalid result");
        }

        return totalNanos / (repetitions * 1_000_000.0);
    }

    public static void main(String[] args) throws Exception {

        // Example function: f(x) = x * (x - 1)
        DoubleUnaryOperator f = x -> x * (x - 1.0);

        double a = 0.0;
        double b = 1.0;
        double resolution = 1e-7;
        int repetitions = 3;

        long intervals =
            (long) Math.ceil((b - a) / resolution);

        System.out.println("Function: f(x) = x * (x - 1)");
        System.out.println("Interval: [" + a + ", " + b + "]");
        System.out.println("Resolution: " + resolution);
        System.out.println("Subintervals: " + intervals);
        System.out.println("Worker threads: " + THREADS);

        // Warm up the JVM before measuring.
        integrateSequential(f, a, b, resolution);

        ExecutorService pool =
            Executors.newFixedThreadPool(THREADS);

        try {
            integrateParallel(
                f, a, b, resolution, pool, 100_000
            );

            double sequentialMs = benchmarkSequential(
                f, a, b, resolution, repetitions
            );

            double reference =
                integrateSequential(f, a, b, resolution);

            System.out.printf(
                "%nSequential result: %.12f%n", reference
            );
            System.out.printf(
                "Sequential average: %.3f ms%n", sequentialMs
            );

            // Chunk sizes are numbers of subintervals per task.
            long[] chunkSizes = {
                1_000, 5_000, 10_000, 25_000,
                50_000, 100_000, 250_000, 500_000,
                1_000_000
            };

            double bestTime = Double.MAX_VALUE;
            long bestChunk = -1;

            System.out.println(
                "%nChunk size | Parallel ms | Speedup | Result"
            );

            for (long chunk : chunkSizes) {
                double result = integrateParallel(
                    f, a, b, resolution, pool, chunk
                );

                if (Math.abs(result - reference) > 1e-9) {
                    throw new IllegalStateException(
                        "Incorrect result for chunk " + chunk
                    );
                }

                double parallelMs = benchmarkParallel(
                    f, a, b, resolution,
                    pool, chunk, repetitions
                );

                double speedup = sequentialMs / parallelMs;

                System.out.printf(
                    "%10d | %11.3f | %6.2fx | %.10f%n",
                    chunk, parallelMs, speedup, result
                );

                if (parallelMs < bestTime) {
                    bestTime = parallelMs;
                    bestChunk = chunk;
                }
            }

            System.out.println(
                "%nBest tested chunk size: " + bestChunk
            );
        } finally {
            pool.shutdown();
        }
    }
}
 
 
 
 
 
 
 
 
 
