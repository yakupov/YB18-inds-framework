package org.itmo.inds.oracle;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.api.PopulationSnapshot;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Inserts the same points into a population and the {@link ReferencePopulation}, single-threaded, and requires them to
 * agree: the returned ranks and the evicted points after every insertion, the whole state (levels, members and their
 * order, crowding distances, sizes) at the {@linkplain PopulationOracle#isCheckpoint checkpoints}.
 */
public final class ModelCrossCheck {
    /**
     * A population under test.
     *
     * @param name     name in test reports
     * @param factory  creates an empty population with the given expected size
     * @param eviction its eviction rule
     */
    public record PopulationUnderTest(String name, LongFunction<ManagedPopulation<Integer>> factory,
                                      ReferencePopulation.Eviction eviction) {
        @Override
        @Nonnull
        public String toString() {
            return name;
        }
    }

    private ModelCrossCheck() {
    }

    /**
     * @param n the number of points
     * @return the expected sizes to check a population with: {@link Long#MAX_VALUE} (unlimited), then 1, 10 and
     * {@code n / 2} where below {@code n}
     */
    public static LongStream expectedSizes(final int n) {
        return LongStream.concat(LongStream.of(Long.MAX_VALUE),
                LongStream.of(1, 10, n / 2).filter(size -> size > 0 && size < n).distinct());
    }

    /**
     * @param population   the population under test
     * @param points       the points to insert, in order; not modified
     * @param expectedSize the expected size to create the population with
     * @return a description of the first mismatch, or empty
     */
    public static Optional<String> check(final PopulationUnderTest population, final double[][] points,
                                         final long expectedSize) {
        final ManagedPopulation<Integer> tested = population.factory().apply(expectedSize);
        final ReferencePopulation model = new ReferencePopulation(expectedSize, population.eviction());
        final int n = points.length;
        for (int i = 0; i < n; ++i) {
            final int step = i + 1;
            final int expectedRank = model.rankOf(points[i]);
            final boolean present = model.contains(points[i]);
            final Individual<Integer> point = new FitnessAndCdIndividual<>(points[i].clone(), i);
            final int rank = tested.addIndividual(point);
            if (rank != expectedRank) {
                return Optional.of("step " + step + ": returned rank " + rank + ", model " + expectedRank);
            }
            if (!present) {
                model.add(point);
                final ReferencePopulation.EvictionOutcome outcome = model.eviction();
                if (!outcome.isNone()) {
                    final Optional<String> violation = followEviction(tested, model, outcome);
                    if (violation.isPresent()) {
                        return violation.map(description -> "step " + step + ": " + description);
                    }
                }
            }
            if (PopulationOracle.isCheckpoint(step, n)) {
                final Optional<String> violation = stateViolation(tested, model, points);
                if (violation.isPresent()) {
                    return violation.map(description -> "step " + step + ": " + description);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Checks that the population evicted what the model's rule allows (where the rule depends on the order of tied
     * values, any of the candidates) and removes the same members from the model.
     */
    private static Optional<String> followEviction(final ManagedPopulation<Integer> tested,
                                                   final ReferencePopulation model,
                                                   final ReferencePopulation.EvictionOutcome outcome) {
        final Set<Integer> present = payloads(tested.getSnapshot().getLevels());
        final Set<Integer> modelMembers = model.levels().stream().flatMap(List::stream)
                .map(Individual::getPayload).collect(Collectors.toSet());
        if (!modelMembers.containsAll(present)) {
            return Optional.of("members that the model does not have");
        }
        final Set<Integer> evicted = new HashSet<>(modelMembers);
        evicted.removeAll(present);
        final Set<Integer> chosen = new HashSet<>(evicted);
        chosen.removeAll(outcome.certain());
        if (!evicted.containsAll(outcome.certain()) || !outcome.candidates().containsAll(chosen)
                || chosen.size() != outcome.count()) {
            return Optional.of("evicted " + evicted + ", model: " + outcome);
        }
        model.remove(evicted);
        return Optional.empty();
    }

    private static Optional<String> stateViolation(final ManagedPopulation<Integer> tested,
                                                   final ReferencePopulation model, final double[][] points) {
        final PopulationSnapshot<Integer> snapshot = tested.getSnapshot();
        final List<? extends NonDominationLevel<Integer>> levels = snapshot.getLevels();
        final List<List<Individual<Integer>>> modelLevels = model.levels();
        if (levels.size() != modelLevels.size()) {
            return Optional.of(levels.size() + " levels, model " + modelLevels.size());
        }
        for (int r = 0; r < levels.size(); ++r) {
            final Optional<String> violation = levelViolation(r, levels.get(r).getMembers(), modelLevels.get(r),
                    points);
            if (violation.isPresent()) {
                return violation;
            }
        }
        if (snapshot.getSize() != model.size() || tested.size() != model.size()) {
            return Optional.of("size() " + tested.size() + ", snapshot size " + snapshot.getSize() + ", model "
                    + model.size());
        }
        return Optional.empty();
    }

    private static Optional<String> levelViolation(final int rank, final List<Individual<Integer>> members,
                                                   final List<Individual<Integer>> modelMembers,
                                                   final double[][] points) {
        final List<Integer> payloads = members.stream().map(Individual::getPayload).toList();
        final List<Integer> modelPayloads = modelMembers.stream().map(Individual::getPayload).toList();
        if (!payloads.equals(modelPayloads)) {
            return Optional.of("level " + rank + " has members " + payloads + ", model " + modelPayloads);
        }
        final List<OptionalDouble> modelDistances = ReferencePopulation.crowdingDistances(modelMembers);
        for (int j = 0; j < members.size(); ++j) {
            final Individual<Integer> member = members.get(j);
            if (!Arrays.equals(points[member.getPayload()], member.getObjectives())) {
                return Optional.of("objectives of point " + member.getPayload() + " changed");
            }
            final double distance = member.getCrowdingDistance();
            final OptionalDouble modelDistance = modelDistances.get(j);
            if (modelDistance.isPresent()) {
                if (Double.compare(distance, modelDistance.getAsDouble()) != 0) {
                    return Optional.of("point " + member.getPayload() + " has crowding distance " + distance
                            + ", model " + modelDistance.getAsDouble());
                }
            } else if (distance < 0 || Double.isNaN(distance)) { // depends on the order of tied values
                return Optional.of("point " + member.getPayload() + " has crowding distance " + distance);
            }
        }
        return Optional.empty();
    }

    private static Set<Integer> payloads(final List<? extends NonDominationLevel<Integer>> levels) {
        return levels.stream().flatMap(level -> level.getMembers().stream())
                .map(Individual::getPayload).collect(Collectors.toSet());
    }
}
