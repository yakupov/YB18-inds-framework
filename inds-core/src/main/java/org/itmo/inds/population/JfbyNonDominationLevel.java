package org.itmo.inds.population;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.sorting.JfbSorter;
import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.util.SortedObjectives;
import org.itmo.inds.util.RankedPopulation;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.itmo.inds.util.Utils.dominates;

@ThreadSafe
@Immutable
public class JfbyNonDominationLevel<T> implements NonDominationLevel<T> {
    @Nonnull
    private final JfbSorter sorter;

    @Nonnull
    private final List<Individual<T>> members;

    @Nonnull
    private final SortedObjectives<Individual<T>, T> sortedObjectives;

    /**
     * Inefficient (O(NlogN) CD recalc) new level construction
     * @param sorter Sorter impl
     * @param members Level members
     */
    public JfbyNonDominationLevel(@Nonnull JfbSorter sorter,
                                  @Nonnull List<Individual<T>> members) {
        this.sorter = sorter;
        if (!members.isEmpty()) {
            final int dim = members.get(0).getObjectives().length;
            this.sortedObjectives = SortedObjectives.create(dim, members, (i, d) -> new FitnessAndCdIndividual<>(i.getObjectives(), d, i.getPayload()));
            this.members = sortedObjectives.getLexSortedPop();
        } else {
            this.members = Collections.emptyList();
            this.sortedObjectives = SortedObjectives.empty(0);
        }
    }

    public JfbyNonDominationLevel(@Nonnull JfbSorter sorter,
                                  @Nonnull List<Individual<T>> members,
                                  @Nonnull SortedObjectives<Individual<T>, T> sortedObjectives) {
        this.sorter = sorter;
        this.members = Collections.unmodifiableList(members);
        this.sortedObjectives = sortedObjectives;
    }

    @Override
    @Nonnull
    public List<Individual<T>> getMembers() {
        return members;
    }

    @Nonnull
    public SortedObjectives<Individual<T>, T> getSortedObjectives() {
        return sortedObjectives;
    }

    @Override
    public MemberAdditionResult<T, JfbyNonDominationLevel<T>> addMembers(@Nonnull List<Individual<T>> addends) {
        final int[] ranks = new int[members.size()];
        final RankedPopulation<Individual<T>> rp = sorter.addRankedMembers(members, ranks, addends, 0);
        final ArrayList<Individual<T>> nextLevel = new ArrayList<>(ranks.length);

        for (int i = 0; i < rp.getPop().length; ++i) {
            if (rp.getRanks()[i] != 0) {
                nextLevel.add(rp.getPop()[i]);
            }
        }

        final SortedObjectives<Individual<T>, T> nso = sortedObjectives.update(
                addends,
                nextLevel,
                (i, d) -> new FitnessAndCdIndividual<>(i.getObjectives(), d, i.getPayload())
        );

        return new MemberAdditionResult<>(
                nextLevel,
                new JfbyNonDominationLevel<>(sorter, nso.getLexSortedPop(), nso)
        );
    }

    @Override
    public boolean dominatedByAnyPointOfThisLayer(@Nonnull Individual<T> point) {
        final double[] pointObj = point.getObjectives();
        for (Individual<T> member : members) {
            final double[] memberObj = member.getObjectives();
            if (memberObj[0] > pointObj[0])
                break;
            if (dominates(memberObj, pointObj, pointObj.length) < 0)
                return true;
        }
        return false;
    }

    @Override
    public JfbyNonDominationLevel<T> copy() {
        final List<Individual<T>> newMembers = new ArrayList<>(members.size());
        newMembers.addAll(members);
        return new JfbyNonDominationLevel<>(sorter, newMembers, sortedObjectives);
    }

    @Override
    public String toString() {
        return "members=" + members.stream()
                .map(Individual::getObjectives)
                .map(Arrays::toString)
                .toList();
    }
}
