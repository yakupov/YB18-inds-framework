package org.itmo.inds.twod;

import org.itmo.inds.api.Individual;

import javax.annotation.Nonnull;

/**
 * A test point without {@code equals} / {@code hashCode}: equal only to itself, so two of them with the same
 * coordinates are different points to the population.
 */
final class IdentityPoint implements Individual<Object> {
    private final double[] objectives;

    IdentityPoint(final double... objectives) {
        this.objectives = objectives;
    }

    @Nonnull
    @Override
    public double[] getObjectives() {
        return objectives;
    }

    @Override
    public double getCrowdingDistance() {
        return 0;
    }

    @Override
    public Object getPayload() {
        return null;
    }
}
