package org.itmo.inds.population;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.api.PopulationSnapshot;
import org.itmo.inds.sorting.JfbSorter;
import org.itmo.inds.util.RankedPopulation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class CoarseLockJfbyPopulation<T> extends JfbyPopulation<T> {
    public CoarseLockJfbyPopulation(long expectedPopSize) {
        super(expectedPopSize);
    }

    public CoarseLockJfbyPopulation(@Nonnull List<JfbyNonDominationLevel<T>> nonDominationLevels, long expectedPopSize) {
        super(nonDominationLevels, expectedPopSize);
    }

    public CoarseLockJfbyPopulation(@Nonnull JfbSorter sorter, long expectedPopSize) {
        super(sorter, expectedPopSize);
    }

    public CoarseLockJfbyPopulation(@Nonnull List<JfbyNonDominationLevel<T>> nonDominationLevels, @Nonnull JfbSorter sorter, long expectedPopSize) {
        super(nonDominationLevels, sorter, expectedPopSize);
    }

    @Nonnull
    @Override
    public synchronized PopulationSnapshot<T> getSnapshot() {
        final List<NonDominationLevel<T>> levels = new ArrayList<>();
        int size = 0;
        for (NonDominationLevel<T> level : super.getSnapshot().getLevels()) {
            levels.add(level.copy());
            size += level.getMembers().size();
        }
        return new PopulationSnapshot<>(levels, size);
    }

    @Nullable
    @Override
    synchronized Individual<T> intRemoveWorst() {
        return super.intRemoveWorst();
    }

    @Override
    public synchronized int size() {
        return super.size();
    }

    @Override
    public synchronized int addIndividual(@Nonnull Individual<T> addend) {
        return super.addIndividual(addend);
    }

    @Override
    public synchronized JfbyPopulation<T> clone() {
        return super.clone();
    }

    @Override
    public synchronized RankedPopulation<Individual<T>> toRankedPopulation() {
        return super.toRankedPopulation();
    }
}
