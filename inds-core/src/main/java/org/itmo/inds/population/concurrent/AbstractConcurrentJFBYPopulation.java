package org.itmo.inds.population.concurrent;

import org.itmo.inds.api.IIndividual;
import org.itmo.inds.api.IManagedPopulation;

import javax.annotation.Nullable;

abstract class AbstractConcurrentJFBYPopulation implements IManagedPopulation {
    @Nullable
    abstract IIndividual intRemoveWorst();

    @Override
    abstract public AbstractConcurrentJFBYPopulation clone();
}
