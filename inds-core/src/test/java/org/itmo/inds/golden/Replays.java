package org.itmo.inds.golden;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.sorting.JfbSorter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * Replays of the scenarios recorded on the nds2018 builds, for the ported sorters and populations: same operations in
 * the same order, same records. The nds2016 scenario {@link Scenario#TREAP_2D} is replayed in its test class. An
 * exception thrown by the ported code ends the block as
 * {@link RecordBuffer#failed}, as it did when the legacy code was recorded.
 * <p>
 * The few texts written here (scenario parameters such as {@code prefix=} and {@code expected=}, the skip reason, the
 * keys of the additional results) are copied verbatim from the legacy recorder: the golden records contain them, so
 * they cannot change. Each is written in exactly one place, as a constant or next to the value it describes; an enum
 * would add a type with a single use.
 */
final class Replays {
    /**
     * Expected population size of the population runs without eviction.
     */
    static final long UNLIMITED = Long.MAX_VALUE;

    /**
     * Above this size, one-shot scenarios record only the digest, no dump.
     */
    private static final int MAX_DUMPED_SIZE = 1000;

    // keys of the additional results of the population scenarios
    private static final String CLONE_AT = "clone-at";
    private static final String ORIGINAL_UNCHANGED = "original-unchanged";
    private static final String PROBE_RANKS = "probe-ranks";
    private static final String RANKED_POPULATION = "ranked-population";
    private static final String LEVEL_SIZES = "level-sizes";

    private Replays() {
    }

    /**
     * @return a replay of {@link Scenario#DC_SORT}: sorts all points at once with DC; one step record (step 0) with
     * result order and ranks
     */
    static GoldenCheck.Replay dcSort() {
        return Replays::dcSort;
    }

    /**
     * @param sorter creates the incremental sorter of the target ({@code JfbSorter} for DC1,
     *               {@code IncrementalJfbSorter} for DC2), once per input set
     * @return a replay of {@link Scenario#DC1_INSERT} / {@link Scenario#DC2_INSERT} (the target's scenario): sorts the
     * prefix with DC, then inserts the remaining points one by one
     */
    static GoldenCheck.Replay incremental(final Supplier<? extends JfbSorter> sorter) {
        return (target, input, out) -> incremental(target, input, out, sorter.get());
    }

    /**
     * @param sorter creates the sorter of the target, once per input set
     * @return a replay of {@link Scenario#ADD_RANKED_MEMBERS}, recorded for the families {@code UNIFORM} and
     * {@code LAYERS} only: the input's level 1 (given rank 0) receives its level 0 as addends, rank hint 0
     */
    static GoldenCheck.Replay addRankedMembers(final Supplier<? extends JfbSorter> sorter) {
        return (target, input, out) -> addRankedMembers(target, input, out, sorter.get());
    }

    /**
     * @param factory creates an empty population for an expected size ({@link #UNLIMITED} or
     *                {@link InputSet#smallExpected()})
     * @return a replay of {@link Scenario#POPULATION} (with unlimited expected size if the input set has such a run,
     * and with {@link InputSet#smallExpected()}) and of {@link Scenario#POPULATION_CLONE} (input sets with 2 to
     * 1000 points)
     */
    static GoldenCheck.Replay population(final LongFunction<ManagedPopulation<Integer>> factory) {
        return (target, input, out) -> {
            if (input.unlimitedRun()) {
                population(target, input, out, factory, UNLIMITED, false);
            }
            population(target, input, out, factory, input.smallExpected(), false);
            if (input.unlimitedRun() && input.n() >= 2 && input.n() <= 1000) {
                population(target, input, out, factory, UNLIMITED, true);
            }
        };
    }

    // ---------------------------------------------------------------- scenarios

    private static void dcSort(final Target target, final InputSet input, final RecordBuffer out) {
        out.begin(target, Scenario.DC_SORT, input, RecordBuffer.NONE);
        final Individual<Integer>[] all = individuals(input.points());
        try {
            final int[] ranks = new JfbSorter().performNds(all);
            recordSorted(out, 0, all, ranks, input.n() <= MAX_DUMPED_SIZE);
            out.ok();
        } catch (final Throwable thrown) {
            out.failed(thrown, 0);
        }
    }

    @SuppressWarnings("rawtypes")
    private static void incremental(final Target target, final InputSet input, final RecordBuffer out,
                                    final JfbSorter sorter) {
        out.begin(target, target.scenario(), input, "prefix=" + input.prefix());
        final Individual<Integer>[] all = individuals(input.points());
        final int n = all.length;
        int step = input.prefix();
        try {
            Individual[] population = Arrays.copyOf(all, input.prefix(), Individual[].class);
            // the prefix is sorted with DC: IncrementalJfbSorter cannot do full sorts
            int[] ranks = population.length == 0 ? new int[0] : new JfbSorter().performNds(population);
            for (int i = input.prefix(); i < n; ++i) {
                step = i + 1;
                final var result = sorter.performIncrementalNds(population, ranks, all[i]);
                population = result.getPop();
                ranks = result.getRanks();
                if (RecordBuffer.recordAt(step, n)) {
                    recordSorted(out, step, population, ranks, RecordBuffer.dumpAt(step, n));
                }
            }
            out.ok();
        } catch (final Throwable thrown) {
            out.failed(thrown, step);
        }
    }

    private static void addRankedMembers(final Target target, final InputSet input, final RecordBuffer out,
                                         final JfbSorter sorter) {
        if (input.family() != InputSet.Family.UNIFORM && input.family() != InputSet.Family.LAYERS) {
            return;
        }
        out.begin(target, Scenario.ADD_RANKED_MEMBERS, input, "pop=level1 addends=level0 hint=0");
        try {
            final Individual<Integer>[] all = individuals(input.points());
            final int[] ranks = new JfbSorter().performNds(all); // all is now lexicographically sorted
            final List<Individual<Integer>> level = new ArrayList<>();
            final List<Individual<Integer>> addends = new ArrayList<>();
            for (int i = 0; i < all.length; ++i) {
                if (ranks[i] == 1) {
                    level.add(all[i]);
                }
                if (ranks[i] == 0) {
                    addends.add(all[i]);
                }
            }
            if (level.isEmpty() || addends.isEmpty()) {
                out.skippedFewerThanTwoLevels();
                return;
            }
            final var result = sorter.addRankedMembers(level, new int[level.size()], addends, 0);
            recordSorted(out, 0, result.getPop(), result.getRanks(), input.n() <= MAX_DUMPED_SIZE);
            out.ok();
        } catch (final Throwable thrown) {
            out.failed(thrown, 0);
        }
    }

    /**
     * One run of {@link Scenario#POPULATION} or {@link Scenario#POPULATION_CLONE}: inserts all points one by one; in
     * the clone run the population is cloned after the prefix and the remaining points go into the clone only.
     */
    private static void population(final Target target, final InputSet input, final RecordBuffer out,
                                   final LongFunction<ManagedPopulation<Integer>> factory, final long expected,
                                   final boolean cloneRun) {
        final String expectedText = expected == UNLIMITED ? "unlimited" : Long.toString(expected);
        final Scenario scenario = cloneRun ? Scenario.POPULATION_CLONE : Scenario.POPULATION;
        out.begin(target, scenario, input,
                "expected=" + expectedText + (cloneRun ? " cloneAfter=" + input.prefix() : ""));
        final ManagedPopulation<Integer> original;
        try {
            original = factory.apply(expected);
        } catch (final Throwable thrown) {
            out.failed(thrown, 0);
            return;
        }
        final Individual<Integer>[] all = individuals(input.points());
        final int n = all.length;
        int step = 0;
        try {
            ManagedPopulation<Integer> receiving = original;
            String originalBefore = null;
            for (int i = 0; i < n; ++i) {
                step = i + 1;
                if (cloneRun && i == input.prefix()) {
                    originalBefore = snapshot(original, false).digest();
                    receiving = original.clone();
                    out.info(CLONE_AT, Integer.toString(i));
                }
                final int rank = receiving.addIndividual(all[i]);
                if (RecordBuffer.recordAt(step, n)) {
                    final boolean withDump = RecordBuffer.dumpAt(step, n);
                    final StateEncoder encoder = snapshot(receiving, withDump);
                    out.step(step, Integer.toString(rank), receiving.size(), encoder.digest());
                    if (withDump) {
                        out.dump(step, encoder);
                    }
                }
            }
            if (cloneRun && originalBefore != null) {
                final String originalAfter = snapshot(original, false).digest();
                out.info(ORIGINAL_UNCHANGED, Boolean.toString(originalBefore.equals(originalAfter)));
            }
            recordFinalResults(input, out, receiving);
            out.ok();
        } catch (final Throwable thrown) {
            out.failed(thrown, step);
        }
    }

    /**
     * Records the end-of-scenario results of a population: {@code determineRank} of the probe points,
     * {@code toRankedPopulation} and the level sizes.
     */
    private static void recordFinalResults(final InputSet input, final RecordBuffer out,
                                           final ManagedPopulation<Integer> population) {
        final StringBuilder probes = new StringBuilder();
        for (int j = 0; j < input.probes().length; ++j) {
            try {
                final Individual<Integer> probe = new FitnessAndCdIndividual<>(input.probes()[j].clone(), -1 - j);
                final int probeRank = population.determineRank(probe);
                probes.append(j == 0 ? "" : ",").append(probeRank);
            } catch (final Throwable thrown) {
                probes.append(j == 0 ? "" : ",").append("exc:").append(thrown.getClass().getSimpleName());
            }
        }
        out.info(PROBE_RANKS, probes.isEmpty() ? RecordBuffer.NONE : probes.toString());
        try {
            final var result = population.toRankedPopulation();
            final Object[] rankedPop = result.getPop();
            final StateEncoder encoder = sorted(rankedPop, result.getRanks(), false);
            final String count = rankedPop == null ? "null" : Integer.toString(rankedPop.length);
            out.info(RANKED_POPULATION, count + " h=" + encoder.digest());
        } catch (final Throwable thrown) {
            out.info(RANKED_POPULATION, "exc " + thrown.getClass().getName());
        }
        final var levels = population.getSnapshot().getLevels();
        final StringBuilder sizes = new StringBuilder();
        for (int i = 0; i < levels.size(); ++i) {
            sizes.append(i == 0 ? "" : ",").append(levels.get(i).getMembers().size());
        }
        out.info(LEVEL_SIZES, sizes.isEmpty() ? RecordBuffer.NONE : sizes.toString());
    }

    // ---------------------------------------------------------------- encoding

    /**
     * @param points the input points
     * @return individuals with copies of the points, payload = index in the input set
     */
    @SuppressWarnings("unchecked")
    private static Individual<Integer>[] individuals(final double[][] points) {
        final Individual<Integer>[] individuals = new Individual[points.length];
        for (int i = 0; i < points.length; ++i) {
            individuals[i] = new FitnessAndCdIndividual<>(points[i].clone(), i);
        }
        return individuals;
    }

    /**
     * Records a sorter result as a step record and, optionally, a dump.
     */
    private static void recordSorted(final RecordBuffer out, final int step, final Object[] elements, final int[] ranks,
                                     final boolean withDump) {
        final StateEncoder encoder = sorted(elements, ranks, withDump);
        out.step(step, elements == null ? 0 : elements.length, encoder.digest());
        if (withDump) {
            out.dump(step, encoder);
        }
    }

    /**
     * Encodes a sorter result: the elements in result order with their ranks.
     *
     * @param elements the sorted elements, individuals whose payload is their input index; null is encoded as empty
     * @param ranks    their ranks
     * @param withDump whether to build the text dump too
     * @return the encoder holding the digest (and dump)
     */
    private static StateEncoder sorted(final Object[] elements, final int[] ranks, final boolean withDump) {
        final StateEncoder encoder = new StateEncoder(withDump);
        final int n = elements == null ? 0 : elements.length;
        encoder.population(n, -1);
        for (int i = 0; i < n; ++i) {
            @SuppressWarnings("unchecked") final Individual<Integer> element = (Individual<Integer>) elements[i];
            encoder.ranked(element.getPayload(), ranks[i]);
        }
        return encoder;
    }

    /**
     * Encodes a population snapshot: levels in rank order, members in {@code getMembers()} order with objectives and
     * crowding distance.
     *
     * @param population the population
     * @param withDump   whether to build the text dump too
     * @return the encoder holding the digest (and dump)
     */
    private static StateEncoder snapshot(final ManagedPopulation<Integer> population, final boolean withDump) {
        final StateEncoder encoder = new StateEncoder(withDump);
        final var snapshot = population.getSnapshot();
        final var levels = snapshot.getLevels();
        encoder.population(snapshot.getSize(), levels.size());
        for (int i = 0; i < levels.size(); ++i) {
            final NonDominationLevel<Integer> level = levels.get(i);
            final List<Individual<Integer>> members = level.getMembers();
            encoder.level(i, members.size());
            for (final Individual<Integer> member : members) {
                final Integer payload = member.getPayload();
                encoder.member(payload == null ? -1 : payload, member.getObjectives(), member.getCrowdingDistance());
            }
        }
        return encoder;
    }
}
