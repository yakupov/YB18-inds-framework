package org.itmo.inds.population.jfby;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.api.PopulationSnapshot;
import org.itmo.inds.sorting.IncrementalJfbSorter;
import org.itmo.inds.sorting.JfbSorter;
import org.itmo.inds.util.SortedObjectives;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.*;

import static org.itmo.inds.util.Utils.getWorstCDIndividual;
import static org.itmo.inds.util.Utils.removeIndividualFromLevel;

@SuppressWarnings("WeakerAccess")
@NotThreadSafe
public class JfbyPopulation<T> implements ManagedPopulation<T> {
    @Nonnull
    private final List<JfbyNonDominationLevel<T>> nonDominationLevels;

    private final Map<Individual<T>, Boolean> presentIndividuals = new HashMap<>();

    private final JfbSorter sorter;

    private int lastNumberOfMovements = 0;
    private int lastSumOfMovements = 0;
    private int size = 0;

    private final long expectedPopSize;

    public JfbyPopulation(long expectedPopSize) {
        this(new IncrementalJfbSorter(), expectedPopSize);
    }

    public JfbyPopulation(@Nonnull final List<JfbyNonDominationLevel<T>> nonDominationLevels, long expectedPopSize) {
        this(nonDominationLevels, new IncrementalJfbSorter(), expectedPopSize);
    }

    public JfbyPopulation(@Nonnull final JfbSorter sorter, long expectedPopSize) {
        this(new ArrayList<>(), sorter, expectedPopSize);
    }

    public JfbyPopulation(@Nonnull final List<JfbyNonDominationLevel<T>> nonDominationLevels,
                          @Nonnull final JfbSorter sorter, long expectedPopSize) {
        this.nonDominationLevels = nonDominationLevels;
        this.sorter = sorter;
        this.expectedPopSize = expectedPopSize;

        for (NonDominationLevel<T> level : nonDominationLevels) {
            size += level.getMembers().size();
            for (Individual<T> individual : level.getMembers()) {
                presentIndividuals.put(individual, true);
            }
        }
    }

    @Override
    @Nonnull
    public PopulationSnapshot<T> getSnapshot() {
        return new PopulationSnapshot<>(Collections.unmodifiableList(nonDominationLevels), size);
    }

    @Nullable
    Individual<T> intRemoveWorst() {
        final int lastLevelIndex = nonDominationLevels.size() - 1;
        final JfbyNonDominationLevel<T> lastLevel = nonDominationLevels.get(lastLevelIndex);
        if (lastLevel.getMembers().size() <= 1) {
            nonDominationLevels.remove(lastLevelIndex);
            if (lastLevel.getMembers().isEmpty()) {
                System.err.println("Empty last ND level! Levels = " + nonDominationLevels);
                return null;
            } else {
                --size;
                final Individual<T> individual = lastLevel.getMembers().get(0);
                presentIndividuals.remove(individual);
                return individual;
            }
        } else {
            final Individual<T> removedIndividual = getWorstCDIndividual(lastLevel);
            if (removedIndividual == null) {
                return null;
            }
            final JfbyNonDominationLevel<T> newLevel = removeIndividualFromLevel(lastLevel, removedIndividual, sorter);
            nonDominationLevels.set(lastLevelIndex, newLevel);
            presentIndividuals.remove(removedIndividual);
            --size;
            return removedIndividual;
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int addIndividual(@Nonnull Individual<T> addend) {
        lastNumberOfMovements = 0;
        lastSumOfMovements = 0;

        final int rank = determineRank(addend);

        if (presentIndividuals.putIfAbsent(addend, true) != null) {
            return rank;
        } else if (rank >= nonDominationLevels.size()) {
            final List<Individual<T>> individuals = Collections.singletonList(addend);
            final JfbyNonDominationLevel<T> level = new JfbyNonDominationLevel<>(
                    sorter,
                    individuals
            );
            nonDominationLevels.add(level);
        } else {
            List<Individual<T>> addends = Collections.singletonList(addend);
            int i = rank;
            int prevSize = -1;
            SortedObjectives<Individual<T>, T> prevSortedObjectives = null;
            while (!addends.isEmpty() && i < nonDominationLevels.size()) {
                ++lastNumberOfMovements;
                lastSumOfMovements += addends.size();

                if (prevSize == addends.size()) { //Whole level was pushed
                    final JfbyNonDominationLevel<T> level = new JfbyNonDominationLevel<>(sorter, addends, prevSortedObjectives);
                    nonDominationLevels.add(i, level);
                    return rank;
                }

                final JfbyNonDominationLevel<T> level = nonDominationLevels.get(i);
                prevSize = level.getMembers().size();
                prevSortedObjectives = level.getSortedObjectives();
                final NonDominationLevel.MemberAdditionResult<T, JfbyNonDominationLevel<T>> memberAdditionResult = level.addMembers(addends);
                nonDominationLevels.set(i, memberAdditionResult.getModifiedLevel());
                addends = memberAdditionResult.getEvictedMembers();
                i++;
            }
            if (!addends.isEmpty()) {
                final JfbyNonDominationLevel<T> level = new JfbyNonDominationLevel<>(sorter, addends);
                nonDominationLevels.add(level);
            }
        }

        if (++size > expectedPopSize) {
            intRemoveWorst();
        }
        return rank;
    }

    /**
     * @return A copy of this population. All layers are also copied.
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public JfbyPopulation<T> clone() {
        final JfbyPopulation<T> copy = new JfbyPopulation<>(nonDominationLevels, sorter, expectedPopSize);
        for (NonDominationLevel<T> level : nonDominationLevels) {
            copy.getSnapshot().getLevels().add(level.copy());
        }
        return copy;
    }

    @Override
    public String toString() {
        return "JFBYPopulation{" + "nonDominationLevels=" + nonDominationLevels +
                ", lastNumberOfMovements=" + lastNumberOfMovements +
                ", lastSumOfMovements=" + lastSumOfMovements +
                '}';
    }
}
