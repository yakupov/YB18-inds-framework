package org.itmo.inds.oracle;

import org.itmo.inds.api.Individual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Reference model of the populations with crowding distances (Level, Coarse, Fine, CAS, ENLU): the set of points,
 * everything else derived from it, ranks by the independent {@link Oracle}. Written to be read, not to be fast.
 * <ul>
 * <li>A point equal (by objectives) to a member is not added.</li>
 * <li>Level <i>r</i> holds the members of rank <i>r</i>, in lexicographic order.</li>
 * <li>Crowding distance: per objective, the level sorted by it; first and last get infinity, the others add
 *     {@code (next - previous) * (1 / (max - min))}; an objective without range adds nothing.</li>
 * <li>Eviction above the expected size: {@link Eviction}.</li>
 * </ul>
 * The order of members that share a value in an objective is unspecified; their crowding distances are
 * {@linkplain #crowdingDistances undetermined}, and an eviction depending on them may take any of the candidates.
 */
public final class ReferencePopulation {
    /**
     * The eviction rule of a population.
     */
    public enum Eviction {
        /**
         * Level, Coarse, CAS, ENLU: when the size exceeds the expected size, one member of the last level is evicted:
         * the only one if the level has one member, the first if it has two, otherwise the first one with the smallest
         * crowding distance.
         */
        ONE_WORST,
        /**
         * Fine: when the size exceeds 1.2 times the expected size, size minus expected size members are evicted:
         * whole last levels while they fit; then, if {@code k} members are still to go, the first {@code k} members of
         * the last level whose crowding distance is at most the {@code k}-th smallest one (counted from 1).
         */
        BATCH
    }

    /**
     * What an insertion evicts: all of {@code certain}, and {@code count} of {@code candidates}.
     *
     * @param certain    payloads of the members that are evicted for sure
     * @param candidates payloads of the members of which {@code count} are evicted (empty if {@code count} is 0)
     * @param count      how many of {@code candidates} are evicted
     */
    public record EvictionOutcome(Set<Integer> certain, Set<Integer> candidates, int count) {
        static final EvictionOutcome NONE = new EvictionOutcome(Set.of(), Set.of(), 0);

        public boolean isNone() {
            return certain.isEmpty() && count == 0;
        }
    }

    private static final double BATCH_THRESHOLD = 1.2;

    private final long expectedSize;
    private final Eviction eviction;
    /**
     * The members by objectives, in insertion order.
     */
    private final Map<List<Double>, Individual<Integer>> members = new LinkedHashMap<>();
    private List<List<Individual<Integer>>> levels;

    /**
     * @param expectedSize the expected size of the population
     * @param eviction     its eviction rule
     */
    public ReferencePopulation(final long expectedSize, final Eviction eviction) {
        this.expectedSize = expectedSize;
        this.eviction = eviction;
    }

    public int size() {
        return members.size();
    }

    public boolean contains(final double[] point) {
        return members.containsKey(key(point));
    }

    /**
     * @return the rank {@code point} gets when it is inserted: the first level none of whose members dominates it
     */
    public int rankOf(final double[] point) {
        final List<List<Individual<Integer>>> currentLevels = levels();
        for (int rank = 0; rank < currentLevels.size(); ++rank) {
            if (currentLevels.get(rank).stream().noneMatch(member -> dominates(member.getObjectives(), point))) {
                return rank;
            }
        }
        return currentLevels.size();
    }

    /**
     * Adds a point that is not a member yet; does not evict.
     */
    public void add(final Individual<Integer> point) {
        if (members.putIfAbsent(key(point.getObjectives()), point) != null) {
            throw new IllegalArgumentException("already a member: " + Arrays.toString(point.getObjectives()));
        }
        levels = null;
    }

    /**
     * Removes members by payload; does not check the eviction rule.
     */
    public void remove(final Set<Integer> payloads) {
        members.values().removeIf(member -> payloads.contains(member.getPayload()));
        levels = null;
    }

    /**
     * @return what the population must evict now, after an insertion, by its eviction rule
     */
    public EvictionOutcome eviction() {
        return switch (eviction) {
            case ONE_WORST -> size() > expectedSize ? oneWorst(last(levels())) : EvictionOutcome.NONE;
            case BATCH -> size() > expectedSize * BATCH_THRESHOLD ? batch(size() - expectedSize) : EvictionOutcome.NONE;
        };
    }

    private static EvictionOutcome oneWorst(final List<Individual<Integer>> lastLevel) {
        if (lastLevel.size() <= 2) {
            return new EvictionOutcome(Set.of(lastLevel.get(0).getPayload()), Set.of(), 0);
        }
        final List<OptionalDouble> crowdingDistances = crowdingDistances(lastLevel);
        // Stream.min keeps the first of equal elements
        final Optional<Integer> worstDetermined = IntStream.range(0, lastLevel.size())
                .filter(i -> crowdingDistances.get(i).isPresent()).boxed()
                .min(Comparator.comparingDouble(i -> crowdingDistances.get(i).getAsDouble()))
                .map(i -> lastLevel.get(i).getPayload());
        final Set<Integer> candidates = new HashSet<>();
        for (int i = 0; i < lastLevel.size(); ++i) {
            if (crowdingDistances.get(i).isEmpty()) {
                candidates.add(lastLevel.get(i).getPayload());
            }
        }
        if (candidates.isEmpty()) {
            return new EvictionOutcome(Set.of(worstDetermined.orElseThrow()), Set.of(), 0);
        }
        worstDetermined.ifPresent(candidates::add);
        return new EvictionOutcome(Set.of(), candidates, 1);
    }

    private EvictionOutcome batch(final long toEvict) {
        final List<List<Individual<Integer>>> remainingLevels = new ArrayList<>(levels());
        final Set<Integer> certain = new HashSet<>();
        long remaining = toEvict;
        while (remaining > 0 && last(remainingLevels).size() <= remaining) {
            final List<Individual<Integer>> lastLevel = remainingLevels.remove(remainingLevels.size() - 1);
            lastLevel.forEach(member -> certain.add(member.getPayload()));
            remaining -= lastLevel.size();
        }
        if (remaining == 0) {
            return new EvictionOutcome(certain, Set.of(), 0);
        }
        final List<Individual<Integer>> lastLevel = last(remainingLevels);
        final List<OptionalDouble> crowdingDistances = crowdingDistances(lastLevel);
        if (crowdingDistances.stream().anyMatch(OptionalDouble::isEmpty)) {
            final Set<Integer> candidates = new HashSet<>();
            lastLevel.forEach(member -> candidates.add(member.getPayload()));
            return new EvictionOutcome(certain, candidates, (int) remaining);
        }
        final double threshold = crowdingDistances.stream().mapToDouble(OptionalDouble::getAsDouble).sorted()
                .toArray()[(int) remaining - 1];
        for (int i = 0; i < lastLevel.size() && remaining > 0; ++i) {
            if (crowdingDistances.get(i).getAsDouble() <= threshold) {
                certain.add(lastLevel.get(i).getPayload());
                --remaining;
            }
        }
        return new EvictionOutcome(certain, Set.of(), 0);
    }

    /**
     * @return the levels, rank 0 first, each in lexicographic order
     */
    public List<List<Individual<Integer>>> levels() {
        if (levels == null) {
            final List<Individual<Integer>> all = new ArrayList<>(members.values());
            final int[] ranks = Oracle.ranks(all.stream().map(Individual::getObjectives).toArray(double[][]::new));
            final List<List<Individual<Integer>>> computed = new ArrayList<>();
            for (int i = 0; i < all.size(); ++i) {
                while (computed.size() <= ranks[i]) {
                    computed.add(new ArrayList<>());
                }
                computed.get(ranks[i]).add(all.get(i));
            }
            computed.forEach(level -> level.sort(Comparator.comparing(Individual::getObjectives,
                    ReferencePopulation::lexicographic)));
            levels = computed;
        }
        return levels;
    }

    /**
     * @param level the members of a level, in lexicographic order
     * @return their crowding distances, in the same order; empty where the distance depends on the history (the
     * member shares a value in some objective with another member)
     */
    public static List<OptionalDouble> crowdingDistances(final List<Individual<Integer>> level) {
        final int n = level.size();
        final int k = level.get(0).getObjectives().length;
        final double[] distances = new double[n];
        final boolean[] determined = new boolean[n];
        Arrays.fill(determined, true);
        for (int objective = 0; objective < k; ++objective) {
            final int o = objective;
            final Integer[] order = new Integer[n];
            for (int i = 0; i < n; ++i) {
                order[i] = i;
            }
            Arrays.sort(order, Comparator.comparingDouble(i -> level.get(i).getObjectives()[o]));
            final double[] values = new double[n];
            for (int j = 0; j < n; ++j) {
                values[j] = level.get(order[j]).getObjectives()[o];
            }
            for (int j = 0; j < n; ++j) {
                if ((j > 0 && values[j - 1] == values[j]) || (j < n - 1 && values[j + 1] == values[j])) {
                    determined[order[j]] = false;
                }
            }
            distances[order[0]] = Double.POSITIVE_INFINITY;
            distances[order[n - 1]] = Double.POSITIVE_INFINITY;
            if (values[n - 1] != values[0]) {
                final double inverseRange = 1 / (values[n - 1] - values[0]);
                for (int j = 1; j < n - 1; ++j) {
                    distances[order[j]] += (values[j + 1] - values[j - 1]) * inverseRange;
                }
            }
        }
        final List<OptionalDouble> result = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            result.add(determined[i] ? OptionalDouble.of(distances[i]) : OptionalDouble.empty());
        }
        return result;
    }

    /**
     * Pareto dominance, minimization, naive and independent of the code under test: {@code a} is nowhere worse than
     * {@code b} and somewhere better.
     */
    public static boolean dominates(final double[] a, final double[] b) {
        boolean better = false;
        for (int i = 0; i < a.length; ++i) {
            if (a[i] > b[i]) {
                return false;
            }
            better |= a[i] < b[i];
        }
        return better;
    }

    private static int lexicographic(final double[] a, final double[] b) {
        for (int i = 0; i < a.length; ++i) {
            if (a[i] < b[i]) {
                return -1;
            }
            if (a[i] > b[i]) {
                return 1;
            }
        }
        return 0;
    }

    private static <E> E last(final List<E> list) {
        return list.get(list.size() - 1);
    }

    private static List<Double> key(final double[] point) {
        return Arrays.stream(point).boxed().toList();
    }
}
