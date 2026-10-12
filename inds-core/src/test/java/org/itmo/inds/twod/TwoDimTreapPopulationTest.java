package org.itmo.inds.twod;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Behaviour of the 2D treap population that the oracle cross-check does not cover: input checks, points with equal
 * coordinates, more than two objectives, and the defects of the cited code fixed in this library
 * ({@code docs/changes-from-cited-code.md}). Levels are checked through their contents (the oracle cross-check checks
 * ranks on many inputs).
 */
class TwoDimTreapPopulationTest {
    private static Individual<Object> point(final double... objectives) {
        return new FitnessAndCdIndividual<>(objectives, null);
    }

    @Test
    void addPointRejectsFewerThanTwoObjectives() {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>();
        assertThrows(IllegalArgumentException.class, () -> population.addPoint(point(0)));
        assertEquals(0, population.size());
    }

    @Test
    void equalPointsAreStoredOnce() {
        final List<TwoDimTreap<Object>> levels = new ArrayList<>();
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(levels);
        population.addPoint(point(0, 1));
        population.addPoint(point(0, 1));
        assertEquals(1, population.size());
        assertEquals(1, TwoDimTreapTraversal.inOrderPoints(levels.get(0)).size());
    }

    @Test
    void differentPointsWithEqualCoordinatesShareALevel() {
        final List<TwoDimTreap<Object>> levels = new ArrayList<>();
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(levels);
        final Individual<Object> first = new IdentityPoint(0, 1);
        final Individual<Object> second = new IdentityPoint(0, 1);
        population.addPoint(first);
        population.addPoint(point(1, 0));
        population.addPoint(second);
        population.addPoint(point(1, 2));
        assertEquals(4, population.size());
        assertEquals(2, levels.size());
        assertEquals(List.of(first, second, point(1, 0)), TwoDimTreapTraversal.inOrderPoints(levels.get(0)));
        assertEquals(0, population.detRankOfExPoint(second));
    }

    @Test
    void pointsAreRankedByTheirFirstTwoObjectives() {
        final List<TwoDimTreap<Object>> levels = new ArrayList<>();
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>(levels);
        population.addPoint(point(0, 1, 5));
        population.addPoint(point(0, 1, 3));
        population.addPoint(point(1, 2, -100));
        population.addPoint(point(2, 0.5, 100));
        assertEquals(4, population.size());
        assertEquals(List.of(point(0, 1, 5), point(0, 1, 3), point(2, 0.5, 100)),
                TwoDimTreapTraversal.inOrderPoints(levels.get(0)));
        assertEquals(1, population.detRankOfExPoint(point(1, 2, -100)));
    }

    /**
     * {@code detRankOfExPoint} and {@code getRandWithRank} work while there is only one level; the cited code
     * rejected rank 0 after the binary search, which is skipped with one level, and threw for every point.
     */
    @Test
    void detRankOfExPointWithOneLevel() {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>();
        final Individual<Object> first = point(0, 1);
        final Individual<Object> second = point(1, 0);
        population.addPoint(first);
        population.addPoint(second);
        assertEquals(0, population.detRankOfExPoint(first));
        assertEquals(0, population.detRankOfExPoint(second));
        assertEquals(0, population.getRandWithRank().rank);
    }

    /**
     * {@code detRankOfExPoint} rejects a point that is not in the population; the cited code looped forever for some
     * of them (here: dominated by the first level, dominating a point of the second).
     */
    @Test
    void detRankOfExPointRejectsPointNotInPopulation() {
        final TwoDimTreapPopulation<Object> population = new TwoDimTreapPopulation<>();
        population.addPoint(point(0, 0));
        population.addPoint(point(2, 2));
        assertThrows(IllegalArgumentException.class, () -> population.detRankOfExPoint(point(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> population.detRankOfExPoint(null));
        assertThrows(IllegalArgumentException.class,
                () -> population.detRankOfExPoint(new IdentityPoint(0, 0)));
    }
}
