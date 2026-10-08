package org.itmo.inds.baselines.enlu;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.util.AscLexSortComparator;
import org.itmo.inds.util.SortedObjectives;
import org.itmo.inds.util.Utils;

import javax.annotation.Nonnull;
import java.util.*;

import static org.itmo.inds.util.Utils.dominates;

public class EnluNonDominationLevel<T> implements NonDominationLevel<T> {
    @Nonnull
    private final Set<Individual<T>> members;

    @Nonnull
    private final SortedObjectives<Individual<T>, T> sortedObjectives;

    public EnluNonDominationLevel(@Nonnull Set<Individual<T>> members,
                                  @Nonnull SortedObjectives<Individual<T>, T> sortedObjectives) {
        this.members = members;
        this.sortedObjectives = sortedObjectives;
    }

    @Override
    public List<Individual<T>> getMembers() {
        return sortedObjectives.getLexSortedPop();
    }

    public Set<Individual<T>> getMembersSet() {
        return members;
    }

    @Nonnull
    public SortedObjectives<Individual<T>, T> getSortedObjectives() {
        return sortedObjectives;
    }

    @Override
    public MemberAdditionResult<T, EnluNonDominationLevel<T>> addMembers(@Nonnull List<Individual<T>> addends) {
        final Set<Individual<T>> nextAddends = new HashSet<>();
        final Set<Individual<T>> newMembers = new HashSet<>(members);
        for (Individual<T> addend : addends) {
            final Set<Individual<T>> lastDom = new HashSet<>();
            for (Individual<T> oldTenant : newMembers) {
                if (Utils.dominates(addend.getObjectives(), oldTenant.getObjectives(), addend.getObjectives().length) < 0) {
                    nextAddends.add(oldTenant);
                    lastDom.add(oldTenant);
                }
            }
            newMembers.removeAll(lastDom);
        }
        newMembers.addAll(addends);

        final List<Individual<T>> nextAddendList = new ArrayList<>(nextAddends);
        nextAddendList.sort(AscLexSortComparator.getInstance());
        final SortedObjectives<Individual<T>, T> nso = sortedObjectives.update(
                addends,
                nextAddendList,
                (i, d) -> new FitnessAndCdIndividual<>(i.getObjectives(), d, i.getPayload())
        );
        return new MemberAdditionResult<>(new ArrayList<>(nextAddendList), new EnluNonDominationLevel<>(newMembers, nso));

    }

    @Override
    public boolean dominatedByAnyPointOfThisLayer(@Nonnull Individual<T> point) {
        for (Individual<T> member : members) {
            if (dominates(member.getObjectives(), point.getObjectives(), point.getObjectives().length) < 0)
                return true;
        }
        return false;
    }

    @Override
    public EnluNonDominationLevel<T> copy() {
        return new EnluNonDominationLevel<>(new HashSet<>(members), sortedObjectives);
    }
}
