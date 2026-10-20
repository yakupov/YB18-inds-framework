package org.itmo.inds.testdata;

import java.util.Random;

/**
 * The synthetic two-objective test problems: the five of the thesis Chapter 2 (Section 2.3.1), recreated from its
 * description, and three from the 2014–2015 prototype of the 2D algorithm ({@link #STRIPE}, {@link #PARALLEL_FRONTS},
 * {@link #HYPERBOLA_FRONTS}). Each is a sequence of integer points (stored as doubles) to insert one by one into an
 * empty population. Sizes are given as {@code N}; where the description divides N, the division is rounded down.
 * <p>
 * The thesis fixes the sets of points, not always their order; the order of {@link #PARPER} is a reconstruction (see
 * there). Coordinates are non-negative apart from the small offsets of the diagonals and of {@link #PARALLEL_FRONTS}.
 */
public enum SyntheticProblem {
    /**
     * N random points from an N × N square: the beginning of an optimization, before selection has influenced the
     * distribution. Random.
     */
    SQUARE(true) {
        @Override
        public double[][] points(final int n, final long seed) {
            final Random random = new Random(seed);
            final double[][] points = new double[n][];
            for (int i = 0; i < n; ++i) {
                points[i] = point(random.nextInt(n), random.nextInt(n));
            }
            return points;
        }
    },
    /**
     * N random points, alternately on the lines {@code y = N − x} and {@code y = N − x + 1} (N/2 on each): the final
     * stages of an optimization, a small number of levels. Random (the abscissas).
     */
    PARALLEL(true) {
        @Override
        public double[][] points(final int n, final long seed) {
            final Random random = new Random(seed);
            final double[][] points = new double[n][];
            for (int i = 0; i < n; ++i) {
                final int x = random.nextInt(n);
                points[i] = point(x, n - x + i % 2);
            }
            return points;
        }
    },
    /**
     * N points {@code (x, x)} from the biggest x down to 0: the best case of ENLU, every point dominates all previous.
     */
    DIAG1(false) {
        @Override
        public double[][] points(final int n, final long seed) {
            final double[][] points = new double[n][];
            for (int i = 0; i < n; ++i) {
                points[i] = point(n - 1 - i, n - 1 - i);
            }
            return points;
        }
    },
    /**
     * N points {@code (x, x + 5)} and {@code (x + 5, x)} one after another, x from N/2 − 1 down to 0: every insertion
     * rebuilds every level, the worst case of the 2D structure.
     */
    DIAG2(false) {
        @Override
        public double[][] points(final int n, final long seed) {
            final double[][] points = new double[n / 2 * 2][];
            for (int pair = 0; pair < n / 2; ++pair) {
                final int x = n / 2 - 1 - pair;
                points[2 * pair] = point(x, x + 5);
                points[2 * pair + 1] = point(x + 5, x);
            }
            return points;
        }
    },
    /**
     * The "parallel-perpendicular" problem: N/6 points on each of the lines {@code y = x + 5} and {@code y = x − 5}
     * (abscissa below N/6), N/3 points on each of the lines {@code y = N/3 − x − 4} and {@code y = N/3 − x − 6}
     * (abscissa below N/3), built to make ENLU spend quadratic time per insertion.
     * <p>
     * Order (the thesis shows it only in a figure, where the lines come first): the line {@code y = N/3 − x − 4} by
     * growing abscissa, then the line {@code y = N/3 − x − 6} by growing abscissa, then the two diagonals alternately
     * ({@code y = x − 5} first) from the smallest abscissa up: every diagonal point then pushes a range of the two lines
     * down at once, which makes the total work of ENLU cubic. From the biggest abscissa down it is quadratic.
     */
    PARPER(false) {
        @Override
        public double[][] points(final int n, final long seed) {
            final int diagonalLength = n / 6;
            final int perpendicularLength = n / 3;
            final double[][] points = new double[2 * diagonalLength + 2 * perpendicularLength][];
            int next = 0;
            for (final int offset : new int[]{4, 6}) {
                for (int x = 0; x < perpendicularLength; ++x) {
                    points[next++] = point(x, perpendicularLength - x - offset);
                }
            }
            for (int x = 0; x < diagonalLength; ++x) {
                points[next++] = point(x, x - 5);
                points[next++] = point(x, x + 5);
            }
            return points;
        }
    },
    /**
     * N random points {@code (x, y)} from the N × N square with {@code |y − x| ≤ 10}: a diagonal stripe that holds
     * many levels of short fronts. Random.
     * <p>
     * Not in the thesis: from the 2014–2015 prototype of the 2D algorithm ({@code Point2DUniStrireXPlusGen}).
     */
    STRIPE(true) {
        @Override
        public double[][] points(final int n, final long seed) {
            final Random random = new Random(seed);
            final double[][] points = new double[n][];
            for (int i = 0; i < n; ++i) {
                final int x = random.nextInt(n);
                int y = random.nextInt(n);
                while (y < x - STRIPE_RADIUS || y > x + STRIPE_RADIUS) {
                    y = random.nextInt(n);
                }
                points[i] = point(x, y);
            }
            return points;
        }
    },
    /**
     * N random abscissas in the N × N square distributed over ten fronts {@code y = k·N/10 − x}, k = 0 … 9, in
     * ascending order of k: N/10 points per front (N − N mod 10 in total), each on its own anti-diagonal. Random (the
     * abscissas). Unlike {@link #PARALLEL}, which has two adjacent lines, the fronts are far apart and the points of
     * a front have negative ordinates.
     * <p>
     * Not in the thesis: from the 2014–2015 prototype of the 2D algorithm ({@code Point2DParallelFrontsGenerator}).
     */
    PARALLEL_FRONTS(true) {
        @Override
        public double[][] points(final int n, final long seed) {
            final Random random = new Random(seed);
            final int step = n / FRONTS;
            final int frontSize = n / FRONTS;
            final double[][] points = new double[frontSize * FRONTS][];
            for (int front = 0; front < FRONTS; ++front) {
                for (int i = 0; i < frontSize; ++i) {
                    final int x = random.nextInt(n);
                    points[front * frontSize + i] = point(x, front * step - x);
                }
            }
            return points;
        }
    },
    /**
     * Ten groups of N/10 random abscissas {@code x} in the N × N square, group k = 0 … 9 with {@code y = ⌊√(x² − r²)⌋},
     * {@code r = k·N/10} (the points of a group lie on a hyperbola despite the original name; for {@code x < r} the
     * root is undefined and the ordinate is 0, as in the original, so the later groups carry many points on the
     * x-axis, among them equal ones). Groups are in ascending order of k. Random (the abscissas).
     * <p>
     * Not in the thesis: from the 2014–2015 prototype of the 2D algorithm ({@code Point2DCircleFrontsGenerator}).
     */
    HYPERBOLA_FRONTS(true) {
        @Override
        public double[][] points(final int n, final long seed) {
            final Random random = new Random(seed);
            final int step = n / FRONTS;
            final int frontSize = n / FRONTS;
            final double[][] points = new double[frontSize * FRONTS][];
            for (int front = 0; front < FRONTS; ++front) {
                final long radius = (long) front * step;
                for (int i = 0; i < frontSize; ++i) {
                    final int x = random.nextInt(n);
                    final double root = Math.sqrt((double) x * x - (double) radius * radius);
                    points[front * frontSize + i] = point(x, (int) root); // NaN becomes 0
                }
            }
            return points;
        }
    };

    private static final int STRIPE_RADIUS = 10;
    private static final int FRONTS = 10;

    private final boolean random;

    SyntheticProblem(final boolean random) {
        this.random = random;
    }

    /**
     * @return whether the points depend on the seed
     */
    public boolean isRandom() {
        return random;
    }

    /**
     * @param n    problem size N
     * @param seed seed of the random generator; ignored unless {@link #isRandom()}
     * @return the points to insert, in insertion order: N of them (for {@link #PARPER} and odd N slightly fewer)
     */
    public abstract double[][] points(int n, long seed);

    private static double[] point(final int x, final int y) {
        return new double[]{x, y};
    }
}
