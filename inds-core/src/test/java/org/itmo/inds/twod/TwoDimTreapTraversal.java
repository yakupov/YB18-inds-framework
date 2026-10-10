package org.itmo.inds.twod;

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
     * @return its points in in-order (increasing first objective), which does not depend on the random treap
     * priorities; iterative, because treaps built from chain inputs can be deep
     */
    public static List<TwoDimPoint> inOrderPoints(final TwoDimTreap level) {
        final List<TwoDimPoint> points = new ArrayList<>();
        final List<TwoDimTreap> stack = new ArrayList<>();
        TwoDimTreap current = level;
        while (current != null || !stack.isEmpty()) {
            while (current != null) {
                stack.add(current);
                current = current.left;
            }
            current = stack.remove(stack.size() - 1);
            points.add(current.x);
            current = current.right;
        }
        return points;
    }
}
