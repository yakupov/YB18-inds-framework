package org.itmo.inds.twod;

import org.itmo.inds.api.Individual;
import org.itmo.inds.oracle.Oracle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A deliberately simple model of {@link TwoDimTreapPopulation} for points that are equal only to themselves: the
 * specification, without any of the population's data structures. Ranks come from {@link Oracle}; a level is the
 * points of a rank in increasing {@code x1} (points with equal coordinates in the order of insertion); crowding
 * distances follow the NSGA-II definition over the first two objectives, with the same operations as the population
 * so that equal distances compare equal; above the expected size, the first point of the last level with the smallest
 * crowding distance is evicted (the first point if the level has fewer than three).
 */
final class ReferenceTwoDimPopulation {
    private final long expectedSize;
    private final List<Individual<Object>> points = new ArrayList<>();

    ReferenceTwoDimPopulation(final long expectedSize) {
        this.expectedSize = expectedSize;
    }

    /**
     * @return the rank of {@code point} if it were added now
     */
    int rankOf(final Individual<Object> point) {
        final List<Individual<Object>> withPoint = new ArrayList<>(points);
        withPoint.add(point);
        return ranks(withPoint)[points.size()];
    }

    /**
     * @return the rank of {@code point} at its insertion
     */
    int add(final Individual<Object> point) {
        final int rank = rankOf(point);
        points.add(point);
        if (points.size() > expectedSize) {
            final List<List<Individual<Object>>> levels = levels();
            final List<Individual<Object>> lastLevel = levels.get(levels.size() - 1);
            final double[] crowdingDistances = crowdingDistances(lastLevel);
            int worst = 0;
            if (lastLevel.size() >= 3) {
                for (int i = 1; i < lastLevel.size(); ++i) {
                    if (crowdingDistances[i] < crowdingDistances[worst]) {
                        worst = i;
                    }
                }
            }
            points.remove(lastLevel.get(worst));
        }
        return rank;
    }

    int size() {
        return points.size();
    }

    List<List<Individual<Object>>> levels() {
        final int[] ranks = ranks(points);
        final List<List<Individual<Object>>> levels = new ArrayList<>();
        for (int i = 0; i < points.size(); ++i) {
            while (levels.size() <= ranks[i]) {
                levels.add(new ArrayList<>());
            }
            levels.get(ranks[i]).add(points.get(i));
        }
        for (final List<Individual<Object>> level : levels) {
            level.sort(Comparator.comparingDouble(point -> point.getObjectives()[0]));
        }
        return levels;
    }

    /**
     * @param level a level as {@link #levels()} returns it
     */
    static double[] crowdingDistances(final List<Individual<Object>> level) {
        final int last = level.size() - 1;
        final double[] result = new double[level.size()];
        result[0] = Double.POSITIVE_INFINITY;
        result[last] = Double.POSITIVE_INFINITY;
        for (int i = 1; i < last; ++i) {
            for (int objective = 0; objective < 2; ++objective) {
                final double min = Math.min(x(level, 0, objective), x(level, last, objective));
                final double max = Math.max(x(level, 0, objective), x(level, last, objective));
                if (max != min) {
                    final double distance = Math.abs(x(level, i + 1, objective) - x(level, i - 1, objective));
                    result[i] += distance * (1 / (max - min));
                }
            }
        }
        return result;
    }

    private static double x(final List<Individual<Object>> level, final int index, final int objective) {
        return level.get(index).getObjectives()[objective];
    }

    private static int[] ranks(final List<Individual<Object>> points) {
        return Oracle.ranks(points.stream().map(Individual::getObjectives).toArray(double[][]::new));
    }
}
