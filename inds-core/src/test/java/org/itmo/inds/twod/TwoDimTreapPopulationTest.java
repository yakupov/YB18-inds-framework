package org.itmo.inds.twod;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.api.PopulationSnapshot;
import org.itmo.inds.api.RankedIndividual;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour of the 2D treap population that the oracle cross-checks do not cover: input checks, points with equal
 * coordinates, more than two objectives, crowding distances in special cases, snapshots, levels and {@code clone}.
 */
class TwoDimTreapPopulationTest {
    private static final double INF = Double.POSITIVE_INFINITY;

    private static Individual<Object> point(final double... objectives) {
        return new FitnessAndCdIndividual<>(objectives, null);
    }

    private static List<Double> crowdingDistances(final NonDominationLevel<Object> level) {
        return level.getMembers().stream().map(Individual::getCrowdingDistance).toList();
    }

    @Test
    void rejectsFewerThanTwoObjectives() {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> population.addIndividual(point(0)));
        assertThrows(IllegalArgumentException.class, () -> population.determineRank(point(0)));
        assertEquals(0, population.size());
    }

    @Test
    void equalPointsAreStoredOnce() {
        final List<TwoDimTreap<Object>> levels = new ArrayList<>();
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(levels, Long.MAX_VALUE);
        population.addIndividual(point(0, 0));
        assertEquals(1, population.addIndividual(point(0, 1)));
        assertEquals(1, population.addIndividual(point(0, 1)));
        assertEquals(2, population.size());
        assertEquals(1, TwoDimTreapTraversal.inOrderPoints(levels.get(1)).size());
    }

    @Test
    void differentPointsWithEqualCoordinatesShareALevel() {
        final List<TwoDimTreap<Object>> levels = new ArrayList<>();
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(levels, Long.MAX_VALUE);
        final Individual<Object> first = new IdentityPoint(null, 0, 1);
        final Individual<Object> second = new IdentityPoint(null, 0, 1);
        population.addIndividual(first);
        population.addIndividual(point(1, 0));
        assertEquals(0, population.addIndividual(second));
        population.addIndividual(point(1, 2));
        assertEquals(4, population.size());
        assertEquals(2, levels.size());
        assertEquals(List.of(first, second, point(1, 0)), TwoDimTreapTraversal.inOrderPoints(levels.get(0)));
    }

    @Test
    void pointsAreRankedByTheirFirstTwoObjectives() {
        final List<TwoDimTreap<Object>> levels = new ArrayList<>();
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(levels, Long.MAX_VALUE);
        population.addIndividual(point(0, 1, 5));
        population.addIndividual(point(0, 1, 3));
        assertEquals(1, population.addIndividual(point(1, 2, -100)));
        population.addIndividual(point(2, 0.5, 100));
        assertEquals(4, population.size());
        assertEquals(List.of(point(0, 1, 5), point(0, 1, 3), point(2, 0.5, 100)),
                TwoDimTreapTraversal.inOrderPoints(levels.get(0)));
    }

    /**
     * {@code determineRank} of a point that is not in the population is the rank it would get (the cited
     * {@code detRankOfExPoint} looped forever for this one).
     */
    @Test
    void determineRankOfPointNotInPopulation() {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(Long.MAX_VALUE);
        population.addIndividual(point(0, 0));
        population.addIndividual(point(2, 2));
        assertEquals(1, population.determineRank(point(1, 1)));
        assertEquals(2, population.size());
    }

    /**
     * With one level (the cited {@code detRankOfExPoint} threw here): rank 0, the two extreme points have an infinite
     * crowding distance, the middle one the sum of the normalized neighbour distances.
     */
    @Test
    void oneLevel() {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(Long.MAX_VALUE);
        population.addIndividual(point(0, 4));
        population.addIndividual(point(4, 0));
        population.addIndividual(point(1, 2));
        final PopulationSnapshot<Object> snapshot = population.getSnapshot();
        assertEquals(1, snapshot.getLevels().size());
        assertEquals(List.of(INF, 2.0, INF), crowdingDistances(snapshot.getLevels().get(0)));
        for (final RankedIndividual<Object> solution : population.getRandomSolutions(3)) {
            assertEquals(0, solution.getRank());
        }
    }

    /**
     * Points with equal coordinates: the first and the last member of the level are the extremes, the members in
     * between have neighbours at distance 0; an objective without range contributes 0 (not NaN).
     */
    @Test
    void crowdingDistancesOfPointsWithEqualCoordinates() {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(Long.MAX_VALUE);
        population.addIndividual(new IdentityPoint(0, 1, 1));
        population.addIndividual(new IdentityPoint(1, 1, 1));
        population.addIndividual(new IdentityPoint(2, 1, 1));
        assertEquals(List.of(INF, 0.0, INF), crowdingDistances(population.getSnapshot().getLevels().get(0)));
        population.addIndividual(new IdentityPoint(3, 0, 2));
        assertEquals(List.of(INF, 2.0, 0.0, INF), crowdingDistances(population.getSnapshot().getLevels().get(0)));
    }

    @Test
    void snapshotIsKeptUntilAChange() {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(Long.MAX_VALUE);
        population.addIndividual(point(0, 1));
        final PopulationSnapshot<Object> snapshot = population.getSnapshot();
        assertSame(snapshot, population.getSnapshot());
        population.addIndividual(point(0, 1));
        assertSame(snapshot, population.getSnapshot());
        population.addIndividual(point(1, 0));
        assertNotSame(snapshot, population.getSnapshot());
        assertEquals(1, snapshot.getSize());
        assertEquals(1, snapshot.getLevels().get(0).getMembers().size());
    }

    @Test
    void snapshotLevelIsReadOnly() {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(Long.MAX_VALUE);
        population.addIndividual(point(0, 1));
        final NonDominationLevel<Object> level = population.getSnapshot().getLevels().get(0);
        assertThrows(UnsupportedOperationException.class, () -> level.addMembers(List.of(point(1, 0))));
        assertThrows(UnsupportedOperationException.class, () -> level.getMembers().add(point(1, 0)));
        assertSame(level, level.copy());
    }

    @Test
    void snapshotLevelDominance() {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(Long.MAX_VALUE);
        population.addIndividual(point(0, 2));
        population.addIndividual(point(1, 1));
        population.addIndividual(point(2, 0));
        final NonDominationLevel<Object> level = population.getSnapshot().getLevels().get(0);
        assertTrue(level.dominatedByAnyPointOfThisLayer(point(1.5, 1.5)));
        assertTrue(level.dominatedByAnyPointOfThisLayer(point(3, 0)));
        assertTrue(level.dominatedByAnyPointOfThisLayer(point(1, 1.5)));
        assertFalse(level.dominatedByAnyPointOfThisLayer(point(1, 1)));
        assertFalse(level.dominatedByAnyPointOfThisLayer(point(0.5, 0.5)));
        assertFalse(level.dominatedByAnyPointOfThisLayer(point(-1, 5)));
    }

    @Test
    void cloneIsIndependent() {
        final TwoDimTreapPopulation<Object> original = new TwoDimTreapPopulation<>(Long.MAX_VALUE);
        original.addIndividual(new IdentityPoint(0, 0, 1));
        original.addIndividual(point(1, 0));
        final TwoDimTreapPopulation<Object> copy = original.clone();
        copy.addIndividual(point(0.5, 0.5));
        copy.addIndividual(new IdentityPoint(1, 0, 1));
        original.addIndividual(point(2, 2));
        assertEquals(3, original.size());
        assertEquals(4, copy.size());
        assertEquals(2, original.getSnapshot().getLevels().size());
        assertEquals(2, original.getSnapshot().getLevels().get(0).getMembers().size());
        assertEquals(1, copy.getSnapshot().getLevels().size());
        assertEquals(4, copy.getSnapshot().getLevels().get(0).getMembers().size());
    }

    private static List<Object> payloads(final NonDominationLevel<Object> level) {
        return level.getMembers().stream().map(Individual::getPayload).toList();
    }

    /**
     * Of equal smallest crowding distances, the first point (in increasing {@code x1}) is evicted, as in the other
     * populations: five points on a line all have crowding distance 1 inside.
     */
    @Test
    void evictionTakesTheFirstOfEqualCrowdingDistances() {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(4);
        for (int i = 0; i <= 4; ++i) {
            population.addIndividual(new FitnessAndCdIndividual<>(new double[]{i, 4 - i}, i));
        }
        assertEquals(4, population.size());
        assertEquals(List.of(0, 2, 3, 4), payloads(population.getSnapshot().getLevels().get(0)));
    }

    /**
     * A last level with fewer than three points loses its first one; a last level that becomes empty is removed.
     */
    @Test
    void evictionFromSmallLastLevels() {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(2);
        population.addIndividual(new FitnessAndCdIndividual<>(new double[]{0, 0}, "best"));
        population.addIndividual(new FitnessAndCdIndividual<>(new double[]{2, 1}, "right"));
        assertEquals(1, population.addIndividual(new FitnessAndCdIndividual<>(new double[]{1, 2}, "left")));
        assertEquals(List.of("right"), payloads(population.getSnapshot().getLevels().get(1)));
        assertEquals(2, population.addIndividual(new FitnessAndCdIndividual<>(new double[]{3, 3}, "worst")));
        assertEquals(2, population.getSnapshot().getLevels().size());
        assertEquals(List.of("right"), payloads(population.getSnapshot().getLevels().get(1)));
    }

    /**
     * Evicting one of several points with equal coordinates (the middle one: crowding distances infinite, 0,
     * infinite) keeps the others in their bucket and level, and the random solutions see the remaining points only
     * (the last point of the list of all points moves into the place of the evicted one).
     */
    @Test
    void evictionFromABucket() {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(3);
        population.addIndividual(new IdentityPoint("a", 1, 1));
        population.addIndividual(new IdentityPoint("b", 1, 1));
        population.addIndividual(new IdentityPoint("c", 1, 1));
        population.addIndividual(new IdentityPoint("d", 0, 0));
        assertEquals(List.of("a", "c"), payloads(population.getSnapshot().getLevels().get(1)));
        assertEquals(List.of("a", "d", "c"),
                population.getRandomSolutions(3).stream().map(Individual::getPayload).toList());
    }
}
