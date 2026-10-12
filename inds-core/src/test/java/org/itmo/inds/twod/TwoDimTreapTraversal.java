package org.itmo.inds.twod;

import org.itmo.inds.api.Individual;

import java.util.ArrayList;
import java.util.List;

/**
 * Test access to the points of a {@link TwoDimTreap} (one level of a {@link TwoDimTreapPopulation}).
 */
public final class TwoDimTreapTraversal {
    private TwoDimTreapTraversal() {
    }

    /**
     * @param level one level of a population
     * @return its points in in-order (increasing first objective; points with equal coordinates in the order of
     * their bucket), which does not depend on the random treap priorities; iterative, because treaps built from
     * chain inputs can be deep
     */
    public static <T> List<Individual<T>> inOrderPoints(final TwoDimTreap<T> level) {
        final List<Individual<T>> points = new ArrayList<>();
        final List<TwoDimTreap<T>> stack = new ArrayList<>();
        TwoDimTreap<T> current = level;
        while (current != null || !stack.isEmpty()) {
            while (current != null) {
                stack.add(current);
                current = current.left;
            }
            current = stack.remove(stack.size() - 1);
            points.addAll(current.x.getMembers());
            current = current.right;
        }
        return points;
    }
}
