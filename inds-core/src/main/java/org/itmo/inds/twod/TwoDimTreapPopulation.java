package org.itmo.inds.twod;

import org.itmo.inds.api.Individual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Incremental non-dominated sorting by the first two objectives of the points (thesis label 2D); further objectives
 * are ignored. Points with equal first two objectives share a level and a treap node ({@link TwoDimBucket}); a point
 * equal ({@code equals}) to a point already in the population is not added again. Insertion only; not thread-safe.
 *
 * @param <T> payload type of the points
 */
public class TwoDimTreapPopulation<T> {
    /** Debug output of every treap of an insertion's cascade: run with {@code -DprintTreaps=Y}. */
    private static final boolean PRINT_TREAPS = "Y".equals(System.getProperty("printTreaps"));

    private final List<TwoDimTreap<T>> ranks;
    private final Random random = new Random();
    /** Every point of the population once, in the order of insertion: random points in O(1). */
    private final List<Individual<T>> points = new ArrayList<>();
    /** The bucket of every coordinate pair in the population (key and value are the same bucket). */
    private final Map<TwoDimBucket<T>, TwoDimBucket<T>> buckets = new HashMap<>();

    /**
     * @return the number of points (each point once by {@code equals})
     */
    public int size() {
        return points.size();
    }

    public TwoDimTreapPopulation() {
        this(new ArrayList<>());
    }

    /**
     * @param ranks the empty list the population keeps its levels in (index = rank), for callers that observe them
     */
    public TwoDimTreapPopulation(List<TwoDimTreap<T>> ranks) {
        this.ranks = ranks;
    }

    protected int determineRank(TwoDimBucket<T> nInd) {
        int currRank = 0;
        int l = 0;
        int r = ranks.size() - 1;
        while (l <= r) {
            int i = (l + r) / 2;
            if (ranks.get(i).dominatedBySomebody(nInd)) {
                currRank = i + 1;
                l = i + 1;
            } else {
                r = i - 1;
            }
        }
        return currRank;
    }

    /**
     * Adds a point unless an equal ({@code equals}) point is in the population.
     *
     * @param point the point; at least two objectives, of which the first two are used
     * @throws IllegalArgumentException if the point has fewer than two objectives
     */
    public void addPoint(Individual<T> point) {
        if (point.getObjectives().length < 2) {
            throw new IllegalArgumentException("2D population: a point needs at least 2 objectives, got "
                    + Arrays.toString(point.getObjectives()));
        }
        final TwoDimBucket<T> nInd = new TwoDimBucket<>(point);
        final TwoDimBucket<T> sameCoordinates = buckets.putIfAbsent(nInd, nInd);
        if (sameCoordinates != null) {
            if (sameCoordinates.add(point)) {
                points.add(point);
            }
            return;
        }
        points.add(point);
        int rank = determineRank(nInd);
        //System.err.println(rank + "_" + nInd.toString() + "_" + ranks.size());
        final TwoDimTreap<T> nTreap = new TwoDimTreap<>(nInd, random.nextInt(), null, null);

        if (rank >= ranks.size()) {
            ranks.add(nTreap);
        } else if (nInd.compareDom(ranks.get(rank).getMinP()) < 0) {
            ranks.add(rank, nTreap);
        } else {
            int i = 0;
            TwoDimBucket<T> minP = nInd;
            TwoDimTreap<T> cNext = nTreap;
            while (minP != null) {
                if (ranks.size() <= rank + i) {
                    ranks.add(cNext);
                    break;
                }

                printTreap(cNext, PRINT_TREAPS);
                printTreap(ranks.get(rank + i), PRINT_TREAPS);

                TwoDimTreap.Treaps<T> t1 = ranks.get(rank + i).splitX(minP);
                TwoDimTreap.Treaps<T> tr = new TwoDimTreap.Treaps<>();
                if (t1.r != null) {
                    tr = t1.r.splitY(minP);
                }

                printTreap(t1.l, PRINT_TREAPS);
                printTreap(t1.r, PRINT_TREAPS);
                printTreap(tr.l, PRINT_TREAPS);
                printTreap(tr.r, PRINT_TREAPS);

                TwoDimTreap<T> res = TwoDimTreap.merge(t1.l, cNext);
                printTreap(res, PRINT_TREAPS);
                res = TwoDimTreap.merge(res, tr.r);
                printTreap(res, PRINT_TREAPS);
                ranks.set(rank + i, res);
                cNext = tr.l;
                printTreap(cNext, PRINT_TREAPS);

                if (cNext == null) {
                    break;
                }
                minP = cNext.getMinP();
                i++;
            }
        }
    }

    /**
     * Local helper for print-based debugging
     */
    private void printTreap(TwoDimTreap<T> cNext, boolean shouldPrint) {
        if (shouldPrint) {
            System.err.println(cNext);
        }
    }

    public static class IndWithRank<T> {
        final Individual<T> ind;
        final int rank;

        public IndWithRank(Individual<T> ind, int rank) {
            super();
            this.ind = ind;
            this.rank = rank;
        }

        public String toString() {
            return "Rank = " + rank + ", value = " + ind;
        }
    }

    public IndWithRank<T> getRandWithRank() {
        if (points.isEmpty()) {
            throw new RuntimeException("Can't get random individual from empty population");
        }
        final Individual<T> randInd = points.get(random.nextInt(points.size()));
        return new IndWithRank<>(randInd, detRankOfExPoint(randInd));
    }

    /**
     * @throws IllegalArgumentException if {@code ind} is {@code null} or not in the population
     */
    protected int detRankOfExPoint(Individual<T> ind) {
        final TwoDimBucket<T> bucket = ind == null ? null : bucketOf(ind);
        if (bucket == null) {
            throw new IllegalArgumentException("Requested rank of existing point, but point is not in " +
                    "the population " + ind);
        }
        return detRankOfExPoint(bucket);
    }

    /**
     * @return the bucket that contains {@code point}, or {@code null}
     */
    private TwoDimBucket<T> bucketOf(Individual<T> point) {
        if (point.getObjectives().length < 2) {
            return null;
        }
        final double[] objectives = point.getObjectives();
        final TwoDimBucket<T> bucket = buckets.get(new TwoDimBucket<T>(objectives[0], objectives[1]));
        return bucket != null && bucket.contains(point) ? bucket : null;
    }

    private int detRankOfExPoint(TwoDimBucket<T> ind) {
        int r = ranks.size() - 1;
        int l = 0;
        int result = -1;
        while (l != r) {
            int curr = (l + r)/2;
            if (r == l + 1) {
                curr = l;
                boolean dominated = ranks.get(curr).dominatedBySomebody(ind);
                boolean dominates = ranks.get(curr).dominatesSomebody(ind);
                if (!dominates && !dominated)
                    return result < 0 ? curr : Math.min(curr, result);

                curr = r;
                dominated = ranks.get(curr).dominatedBySomebody(ind);
                dominates = ranks.get(curr).dominatesSomebody(ind);
                if (!dominates && !dominated)
                    return result < 0 ? curr : Math.min(curr, result);
            } else {
                boolean dominated = ranks.get(curr).dominatedBySomebody(ind);
                boolean dominates = ranks.get(curr).dominatesSomebody(ind);
                if (!dominates && !dominated) {
                    result = result < 0 ? curr : Math.min(curr, result);
                    r = curr;
                } else if (dominates)
                    r = curr;
                else
                    l = curr;
            }
        }
        boolean dominated = ranks.get(l).dominatedBySomebody(ind);
        boolean dominates = ranks.get(l).dominatesSomebody(ind);
        if (!dominates && !dominated) {
            result = result < 0 ? l : Math.min(l, result);
        }

        if (result >= 0) {
            return result;
        }

        throw new RuntimeException("Can't determine rank for " + ind);
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ranks.size(); ++i) {
            sb.append(i);
            sb.append('\n');
            sb.append(ranks.get(i).toString());
            sb.append('\n');
        }
        return sb.toString();
    }
}
