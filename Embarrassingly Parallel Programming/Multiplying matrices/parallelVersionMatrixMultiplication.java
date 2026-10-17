import java.util.*;
import java.util.concurrent.*;

public class parallelVersionMatrixMultiplication {

    static final int THREADS = 4;

    // Sequential matrix multiplication
    static int[][] sequentialMultiply(int[][] a, int[][] b) {
        int rows = a.length;
        int inner = b.length;
        int cols = b[0].length;

        int[][] res = new int[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                for (int k = 0; k < inner; k++) {
                    res[i][j] += a[i][k] * b[k][j];
                }
            }
        }

        return res;
    }

    // Parallel multiplication using a fixed thread pool
    static int[][] parallelMultiply(
            int[][] a, int[][] b,
            ExecutorService pool, int chunkSize)
            throws InterruptedException, ExecutionException {

        int rows = a.length;
        int inner = b.length;
        int cols = b[0].length;

        int[][] res = new int[rows][cols];

        // Treat the result matrix as a flat array of cells.
        int totalCells = rows * cols;

        List<Future<?>> tasks = new ArrayList<>();

        for (int start = 0; start < totalCells;
             start += chunkSize) {

            final int from = start;
            final int to = Math.min(start + chunkSize, totalCells);

            Future<?> task = pool.submit(() -> {

                for (int index = from; index < to; index++) {

                    int i = index / cols;
                    int j = index % cols;

                    int sum = 0;

                    for (int k = 0; k < inner; k++) {
                        sum += a[i][k] * b[k][j];
                    }

                    res[i][j] = sum;
                }
            });

            tasks.add(task);
        }

        // Wait for all submitted tasks to finish.
        for (Future<?> task : tasks) {
            task.get();
        }

        return res;
    }

    static int[][] randomMatrix(
            int rows, int cols, Random random) {

        int[][] matrix = new int[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = random.nextInt(10);
            }
        }

        return matrix;
    }

    static boolean sameMatrix(int[][] a, int[][] b) {
        return Arrays.deepEquals(a, b);
    }

    static double benchmarkSequential(
            int[][] a, int[][] b, int repetitions) {

        long total = 0;

        for (int r = 0; r < repetitions; r++) {
            long start = System.nanoTime();
            sequentialMultiply(a, b);
            total += System.nanoTime() - start;
        }

        return total / (repetitions * 1_000_000.0);
    }

    static double benchmarkParallel(
            int[][] a, int[][] b, ExecutorService pool,
            int chunkSize, int repetitions)
            throws InterruptedException, ExecutionException {

        long total = 0;

        for (int r = 0; r < repetitions; r++) {
            long start = System.nanoTime();

            parallelMultiply(a, b, pool, chunkSize);

            total += System.nanoTime() - start;
        }

        return total / (repetitions * 1_000_000.0);
    }

    public static void main(String[] args) throws Exception {

        // Larger matrices make performance differences easier to see.
        int size = 200;
        Random random = new Random(42);

        int[][] A = randomMatrix(size, size, random);
        int[][] B = randomMatrix(size, size, random);

        System.out.println("Matrix size: " + size + " x " + size);
        System.out.println("Worker threads: " + THREADS);

        // Calculate the reference result.
        int[][] expected = sequentialMultiply(A, B);

        ExecutorService pool =
                Executors.newFixedThreadPool(THREADS);

        try {
            // Warm-up: helps reduce JVM startup effects.
            sequentialMultiply(A, B);
            parallelMultiply(A, B, pool, 256);

            // Measure the sequential baseline.
            double seqMs = benchmarkSequential(A, B, 3);

            System.out.printf(
                "Sequential average: %.3f ms%n", seqMs);

            // Try different chunk sizes.
            int[] chunkSizes = {1, 4, 16, 64, 256, 1024};

            double bestTime = Double.MAX_VALUE;
            int bestChunk = -1;

            System.out.println("\nChunk size | Parallel ms | Speedup");

            for (int chunk : chunkSizes) {

                int[][] result =
                    parallelMultiply(A, B, pool, chunk);

                if (!sameMatrix(expected, result)) {
                    throw new IllegalStateException(
                        "Incorrect result for chunk " + chunk);
                }

                double parallelMs =
                    benchmarkParallel(A, B, pool, chunk, 3);

                double speedup = seqMs / parallelMs;

                System.out.printf(
                    "%10d | %11.3f | %6.2fx%n",
                    chunk, parallelMs, speedup);

                if (parallelMs < bestTime) {
                    bestTime = parallelMs;
                    bestChunk = chunk;
                }
            }

            System.out.println(
                "\nBest tested chunk size: " + bestChunk);

        } finally {
            pool.shutdown();
        }
    }
}
