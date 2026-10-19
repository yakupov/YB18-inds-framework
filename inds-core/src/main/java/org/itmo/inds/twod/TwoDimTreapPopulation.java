package org.itmo.inds.twod;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.api.PopulationSnapshot;
import org.itmo.inds.api.RankedIndividual;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.random.RandomGenerator;

/**
 * Incremental non-dominated sorting by the first two objectives of the points (thesis label 2D); further objectives
 * are ignored, also by the crowding distances. Points with equal first two objectives share a level and a treap node
 * ({@link TwoDimBucket}); a point equal ({@code equals}) to one in the population is not added again.
 *
 * <p>Above the expected size, an insertion evicts the worst point as Level does: of the last level, the first point
 * (in increasing {@code x1}) with the smallest crowding distance, or the first point if the level has fewer than three.
 * Crowding distances are computed on demand: {@link #getRandomSolutions(int)} in O(log<sup>2</sup> N) per point,
 * {@link #getSnapshot()} in O(N) after a change, an eviction in O(size of the last level).
 *
 * @param <T> payload type of the points
 */
@NotThreadSafe
public class TwoDimTreapPopulation<T> implements ManagedPopulation<T> {
    private record Coordinates(double x1, double x2) {
    }

    private final Random random = new Random();

    /*
     * The state is stored redundantly on purpose; every point is in all of these:
     * ranks - one treap per level, ordered by x1; a node holds a TwoDimBucket (the points with the same first two
     *         objectives); split / merge / node removal in expected O(log N) per level
     * bucketsByCoordinates - the bucket of each coordinate pair, O(1): duplicates on insertion, lookups by point
     * points - all points, O(1) random choice
     * pointIndices - the index of each point in points, O(1) removal from points (eviction)
     */
    private final List<TwoDimTreap<T>> ranks;
    private final long expectedPopSize;
    private final Map<Coordinates, TwoDimBucket<T>> bucketsByCoordinates = new HashMap<>();
    private final List<Individual<T>> points = new ArrayList<>();
    private final Map<Individual<T>, Integer> pointIndices = new IdentityHashMap<>();

    /** {@code null} after a change. */
    private PopulationSnapshot<T> snapshot;

    /**
     * @param expectedPopSize the size above which an insertion evicts the worst point ({@link Long#MAX_VALUE}: never)
     */
    public TwoDimTreapPopulation(long expectedPopSize) {
        this(new ArrayList<>(), expectedPopSize);
    }

    /**
     * @param ranks           the empty list the population keeps its levels in, for callers that observe them
     * @param expectedPopSize the size above which an insertion evicts the worst point ({@link Long#MAX_VALUE}: never)
     */
    public TwoDimTreapPopulation(List<TwoDimTreap<T>> ranks, long expectedPopSize) {
        this.ranks = ranks;
        this.expectedPopSize = expectedPopSize;
    }

    @Override
    public int size() {
        return points.size();
    }

    private int rankOf(TwoDimBucket<T> nInd) {
        int currRank = 0;
        int l = 0;
        int r = ranks.size() - 1;
        while (l <= r) {
            final int i = (l + r) / 2;
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
     * @param point at least two objectives
     * @return the rank the point has or would have in this population
     */
    @Override
    public int determineRank(Individual<T> point) {
        final double[] objectives = objectivesOf(point);
        return rankOf(new TwoDimBucket<>(objectives[0], objectives[1]));
    }

    /**
     * @param individual at least two objectives
     * @return the rank of the point at its insertion, also if an equal point was there already or the point is evicted
     * right away
     */
    @Override
    public int addIndividual(@Nonnull Individual<T> individual) {
        final double[] objectives = objectivesOf(individual);
        final TwoDimBucket<T> nInd = new TwoDimBucket<>(individual);
        final TwoDimBucket<T> sameCoordinates =
                bucketsByCoordinates.putIfAbsent(new Coordinates(objectives[0], objectives[1]), nInd);
        if (sameCoordinates != null) {
            final int rank = rankOf(sameCoordinates);
            if (sameCoordinates.add(individual)) {
                added(individual);
            }
            return rank;
        }
        final int rank = insert(nInd);
        added(individual);
        return rank;
    }

    private void added(Individual<T> individual) {
        pointIndices.put(individual, points.size());
        points.add(individual);
        snapshot = null;
        if (points.size() > expectedPopSize) {
            removeWorst();
        }
    }

    /**
     * The cited insertion of a bucket with new coordinates.
     */
    private int insert(TwoDimBucket<T> nInd) {
        final int rank = rankOf(nInd);
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

                final TwoDimTreap.Treaps<T> t1 = ranks.get(rank + i).splitX(minP);
                TwoDimTreap.Treaps<T> tr = new TwoDimTreap.Treaps<>();
                if (t1.r != null) {
                    tr = t1.r.splitY(minP);
                }

                TwoDimTreap<T> res = TwoDimTreap.merge(t1.l, cNext);
                res = TwoDimTreap.merge(res, tr.r);
                ranks.set(rank + i, res);
                cNext = tr.l;

                if (cNext == null) {
                    break;
                }
                minP = cNext.getMinP();
                i++;
            }
        }
        return rank;
    }

    private static double[] objectivesOf(Individual<?> point) {
        final double[] objectives = point.getObjectives();
        if (objectives.length < 2) {
            throw new IllegalArgumentException("2D population: a point needs at least 2 objectives, got "
                    + Arrays.toString(objectives));
        }
        return objectives;
    }

    private TwoDimBucket<T> bucketOf(Individual<T> point) {
        final double[] objectives = point.getObjectives();
        return bucketsByCoordinates.get(new Coordinates(objectives[0], objectives[1]));
    }

    private void removeWorst() {
        final List<Individual<T>> members = members(ranks.get(ranks.size() - 1));
        int worst = 0;
        if (members.size() >= 3) {
            final double[] crowdingDistances = crowdingDistances(members);
            for (int i = 1; i < members.size(); ++i) {
                if (crowdingDistances[worst] > crowdingDistances[i]) {
                    worst = i;
                }
            }
        }
        removeFromLastLevel(members.get(worst));
    }

    /**
     * Removing a point of the last level changes no other point's level.
     */
    private void removeFromLastLevel(Individual<T> point) {
        final TwoDimBucket<T> bucket = bucketOf(point);
        final int index = pointIndices.remove(point);
        final int lastIndex = points.size() - 1;
        if (index != lastIndex) {
            final Individual<T> moved = points.get(lastIndex);
            points.set(index, moved);
            pointIndices.put(moved, index);
        }
        points.remove(lastIndex);
        if (bucket.remove(point)) {
            bucketsByCoordinates.remove(new Coordinates(bucket.getX1(), bucket.getX2()));
            final int lastRank = ranks.size() - 1;
            final TwoDimTreap<T> level = ranks.get(lastRank).remove(bucket);
            if (level == null) {
                ranks.remove(lastRank);
            } else {
                ranks.set(lastRank, level);
            }
        }
        snapshot = null;
    }

    /**
     * @return the levels with their members in increasing {@code x1} and their crowding distances; kept until the next
     * change
     */
    @Override
    @Nonnull
    public PopulationSnapshot<T> getSnapshot() {
        if (snapshot == null) {
            final List<NonDominationLevel<T>> levels = new ArrayList<>(ranks.size());
            for (final TwoDimTreap<T> level : ranks) {
                final List<Individual<T>> members = members(level);
                final double[] crowdingDistances = crowdingDistances(members);
                final List<Individual<T>> withCrowdingDistances = new ArrayList<>(members.size());
                for (int i = 0; i < members.size(); ++i) {
                    final Individual<T> member = members.get(i);
                    withCrowdingDistances.add(new FitnessAndCdIndividual<>(member.getObjectives(),
                            crowdingDistances[i], member.getPayload()));
                }
                levels.add(new TwoDimLevel<>(withCrowdingDistances));
            }
            snapshot = new PopulationSnapshot<>(levels, points.size());
        }
        return snapshot;
    }

    /**
     * @return the points of a level in increasing {@code x1}; iterative, because a treap can be deep
     */
    private static <T> List<Individual<T>> members(TwoDimTreap<T> level) {
        final List<Individual<T>> result = new ArrayList<>();
        final List<TwoDimTreap<T>> stack = new ArrayList<>();
        TwoDimTreap<T> current = level;
        while (current != null || !stack.isEmpty()) {
            while (current != null) {
                stack.add(current);
                current = current.left;
            }
            current = stack.remove(stack.size() - 1);
            result.addAll(current.x.getMembers());
            current = current.right;
        }
        return result;
    }

    /**
     * @param members the members of a level in increasing {@code x1}
     */
    private static double[] crowdingDistances(List<? extends Individual<?>> members) {
        final int last = members.size() - 1;
        final double[] first = members.get(0).getObjectives();
        final double[] end = members.get(last).getObjectives();
        final double[] result = new double[members.size()];
        result[0] = Double.POSITIVE_INFINITY;
        result[last] = Double.POSITIVE_INFINITY;
        for (int i = 1; i < last; ++i) {
            result[i] = crowdingDistance(members.get(i - 1).getObjectives(), members.get(i + 1).getObjectives(),
                    first, end);
        }
        return result;
    }

    /**
     * The crowding distance of an inner member of a level, with the operations of {@code SortedObjectives.calculateCD}
     * (bit-identical results); an objective without range in the level (all members with equal coordinates)
     * contributes 0, not NaN.
     *
     * @param first the objectives of the level's first member (smallest {@code x1}, largest {@code x2})
     * @param last  the objectives of its last member
     */
    private static double crowdingDistance(double[] previous, double[] next, double[] first, double[] last) {
        double crowdingDistance = 0;
        if (first[0] != last[0]) {
            crowdingDistance += (next[0] - previous[0]) * (1 / (last[0] - first[0]));
        }
        if (first[1] != last[1]) {
            crowdingDistance += (previous[1] - next[1]) * (1 / (first[1] - last[1]));
        }
        return crowdingDistance;
    }

    /**
     * Distinct random points with the ranks and crowding distances of {@link #getSnapshot()}, without building one.
     * The result is in the order of an internal list of all points, which is neither by rank nor by insertion.
     */
    @Override
    @Nonnull
    public List<RankedIndividual<T>> getRandomSolutions(int count, @Nonnull RandomGenerator random) {
        if (count < 0) {
            throw new IllegalArgumentException("Negative number of random solutions requested");
        }
        final int actualCount = Math.min(count, points.size());
        final TreeSet<Integer> indices = new TreeSet<>();
        while (indices.size() < actualCount) {
            indices.add(random.nextInt(0, points.size()));
        }
        final List<RankedIndividual<T>> result = new ArrayList<>(actualCount);
        for (final int index : indices) {
            final Individual<T> point = points.get(index);
            final TwoDimBucket<T> bucket = bucketOf(point);
            final int rank = rankOf(bucket);
            final double crowdingDistance = crowdingDistance(ranks.get(rank), bucket, bucket.indexOf(point));
            result.add(new RankedIndividual<>(point.getObjectives(), crowdingDistance, rank, point.getPayload()));
        }
        return result;
    }

    /**
     * @return the crowding distance of member {@code index} of {@code bucket}, from the neighbours in {@code level}
     */
    private double crowdingDistance(TwoDimTreap<T> level, TwoDimBucket<T> bucket, int index) {
        final TwoDimBucket<T> firstBucket = level.getMin();
        final TwoDimBucket<T> lastBucket = level.getMax();
        final int lastIndex = bucket.size() - 1;
        if (index == 0 && bucket == firstBucket || index == lastIndex && bucket == lastBucket) {
            return Double.POSITIVE_INFINITY;
        }
        final TwoDimBucket<T> previous = index > 0 ? bucket : level.predecessor(bucket);
        final TwoDimBucket<T> next = index < lastIndex ? bucket : level.successor(bucket);
        return crowdingDistance(coordinates(previous), coordinates(next), coordinates(firstBucket),
                coordinates(lastBucket));
    }

    private static double[] coordinates(TwoDimBucket<?> bucket) {
        return new double[]{bucket.getX1(), bucket.getX2()};
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public TwoDimTreapPopulation<T> clone() {
        final TwoDimTreapPopulation<T> copy = new TwoDimTreapPopulation<>(expectedPopSize);
        final Map<TwoDimBucket<T>, TwoDimBucket<T>> bucketCopies = new IdentityHashMap<>();
        for (final Map.Entry<Coordinates, TwoDimBucket<T>> entry : bucketsByCoordinates.entrySet()) {
            final TwoDimBucket<T> bucketCopy = entry.getValue().copy();
            bucketCopies.put(entry.getValue(), bucketCopy);
            copy.bucketsByCoordinates.put(entry.getKey(), bucketCopy);
        }
        for (final TwoDimTreap<T> level : ranks) {
            copy.ranks.add(level.copy(bucketCopies));
        }
        copy.points.addAll(points);
        copy.pointIndices.putAll(pointIndices);
        return copy;
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
