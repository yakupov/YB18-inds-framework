package org.itmo.inds.population;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;

import javax.annotation.Nullable;

abstract class AbstractConcurrentJfbyPopulation implements ManagedPopulation {
    @Nullable
    abstract Individual intRemoveWorst();

    @Override
    abstract public AbstractConcurrentJfbyPopulation clone();
}
