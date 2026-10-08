package org.itmo.inds.api;

import javax.annotation.Nonnull;

public interface Individual<T> {
    @Nonnull
    double[] getObjectives();

    double getCrowdingDistance();

    T getPayload();
}
