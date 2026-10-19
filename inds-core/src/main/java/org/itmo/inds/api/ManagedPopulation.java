package org.itmo.inds.api;

import org.itmo.inds.util.AscLexSortComparator;
import org.itmo.inds.util.RankedPopulation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

@SuppressWarnings("unused")
public interface ManagedPopulation<T> extends Cloneable {
    /**
     * @param individual Individual to add
     * @return Added individual's rank (starting from zero)
     */
    int addIndividual(@Nonnull Individual<T> individual);

    /**
     * @return Some valid state of non-domination levels. The returned value is guaranteed to persist, no other thread
     * should alter it. Index in list equals to layer's rank.
     */
    @Nonnull
    PopulationSnapshot<T> getSnapshot();

    /**
     * @return Working set of non-domination levels. The returned value cannot be altered directly, but may be
     * modified by some other thread at any time. Index in list equals to layer's rank.
     */
    @Nonnull
    default List<? extends NonDominationLevel<T>> getLevelsUnsafe() {
        return getSnapshot().getLevels();
    }

    int size();

    ManagedPopulation<T> clone();

    /**
     * @param count max. number of solutions to return
     * @return list with min(population size, count) random solutions
     */
    @Nonnull
    default List<RankedIndividual<T>> getRandomSolutions(int count) {
        return getRandomSolutions(count, ThreadLocalRandom.current());
    }

    /**
     * Draws distinct members uniformly at random. The order of the result is implementation-specific: this default
     * implementation returns the members by rank, and within a level in level order.
     *
     * @param count  max. number of solutions to return
     * @param random the source of the random indices; used by the calling thread only
     * @return list with min(population size, count) distinct random solutions
     * @throws IllegalArgumentException if {@code count} is negative
     */
    @Nonnull
    default List<RankedIndividual<T>> getRandomSolutions(int count, @Nonnull RandomGenerator random) {
        if (count < 0) {
            throw new IllegalArgumentException("Negative number of random solutions requested");
        }

        final PopulationSnapshot<T> popSnap = getSnapshot();

        final int actualCount = Math.min(count, popSnap.getSize());
        final TreeSet<Integer> indices = new TreeSet<>();
        while (indices.size() < actualCount) {
            indices.add(random.nextInt(0, popSnap.getSize()));
        }

        final List<RankedIndividual<T>> res = new ArrayList<>();
        final Iterator<Integer> it = indices.iterator();
        Integer next = getNext(it);
        int prevLevelsSizeSum = 0;
        int rank = 0;
        for (NonDominationLevel<T> level : popSnap.getLevels()) {
            final int levelSize = level.getMembers().size();
            while (next != null && next - prevLevelsSizeSum < levelSize) {
                final Individual<T> ind = level.getMembers().get(next - prevLevelsSizeSum);
                res.add(new RankedIndividual<>(ind.getObjectives(), ind.getCrowdingDistance(), rank, ind.getPayload()));
                next = getNext(it);
            }
            prevLevelsSizeSum += levelSize;
            rank++;
        }

        return res;
    }

    default Integer getNext(Iterator<Integer> it) {
        return it.hasNext() ? it.next() : null;
    }

    default int determineRank(Individual<T> point) {
        final List<? extends NonDominationLevel<T>> ndLayers = getSnapshot().getLevels();

        int l = 0;
        int r = ndLayers.size() - 1;
        int lastNonDominating = r + 1;
        while (l <= r) {
            final int test = (l + r) / 2;
            if (!ndLayers.get(test).dominatedByAnyPointOfThisLayer(point)) {
                lastNonDominating = test;
                r = test - 1;
            } else {
                l = test + 1;
            }
        }

        return lastNonDominating;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    default RankedPopulation<Individual<T>> toRankedPopulation() {
        final PopulationSnapshot<T> popSnap = getSnapshot();

        final Individual[] pop;
        final int[] sortedRanks;
        if (popSnap.getSize() > 0) {
            pop = new Individual[popSnap.getSize()];
            final int[] ranks = new int[popSnap.getSize()];
            int j = 0;
            for (int i = 0; i < popSnap.getLevels().size(); ++i) {
                for (Individual<T> d : popSnap.getLevels().get(i).getMembers()) {
                    pop[j] = d;
                    ranks[j] = i;
                    j++;
                }
            }

            sortedRanks = org.itmo.inds.util.RankSorting.sortRanksForLexSortedPopulation(ranks, pop, Individual::getObjectives);

            Arrays.sort(pop, AscLexSortComparator.getInstance());
        } else {
            pop = null;
            sortedRanks = null;
        }
        return new RankedPopulation<Individual<T>>(pop, sortedRanks);
    }
}
