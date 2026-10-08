package org.itmo.inds.baselines.enlu;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.api.PopulationSnapshot;
import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.util.SortedObjectives;
import org.itmo.inds.util.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static org.itmo.inds.util.Utils.getWorstCDIndividual;

public class EnluPopulation<T> implements ManagedPopulation<T> {
    private final Set<Individual<T>> individuals;
    private final List<EnluNonDominationLevel<T>> ranks;
    private final long maxSize;

    public EnluPopulation(long maxSize) {
        this(new HashSet<>(), new ArrayList<>(), maxSize);
    }

    public EnluPopulation(Set<Individual<T>> individuals, List<EnluNonDominationLevel<T>> ranks, long maxSize) {
        this.individuals = individuals;
        this.ranks = ranks;
        this.maxSize = maxSize;
    }

    @Override
    public synchronized int addIndividual(@Nonnull Individual<T> nInd) {
        final int rg = doAddIndividual(nInd);
        if (individuals.size() > maxSize) {
            intRemoveWorst();
        }
        return rg;
    }


    private static <T> EnluNonDominationLevel<T> removeIndividualFromLevel(@Nonnull final EnluNonDominationLevel<T> lastLevel,
                                                                          @Nonnull final Individual<T> removedIndividual) {
        final SortedObjectives<Individual<T>, T> nso = lastLevel.getSortedObjectives().update(
                Collections.emptyList(),
                Collections.singletonList(removedIndividual),
                (i, d) -> new FitnessAndCdIndividual<>(i.getObjectives(), d, i.getPayload())
        );

        return new EnluNonDominationLevel<>(new HashSet<>(nso.getLexSortedPop()), nso);
    }

    @Nullable
    Individual<T> intRemoveWorst() {
        final int lastLevelIndex = ranks.size() - 1;
        final EnluNonDominationLevel<T> lastLevel = ranks.get(lastLevelIndex);
        if (lastLevel.getMembers().size() <= 1) {
            ranks.remove(lastLevelIndex);
            if (lastLevel.getMembers().isEmpty()) {
                System.err.println("Empty last ND level! Levels = " + ranks);
                return null;
            } else {
                final Individual<T> individual = lastLevel.getMembers().get(0);
                individuals.remove(individual);
                return individual;
            }
        } else {
            final Individual<T> removedIndividual = getWorstCDIndividual(lastLevel);
            if (removedIndividual == null) {
                return null;
            }
            final EnluNonDominationLevel<T> newLevel = removeIndividualFromLevel(lastLevel, removedIndividual);
            ranks.set(lastLevelIndex, newLevel);
            individuals.remove(removedIndividual);
            return removedIndividual;
        }
    }

    private int doAddIndividual(@Nonnull Individual<T> nInd) {
        if (individuals.contains(nInd)) {
            return detRankOfExPoint(nInd);
        } else {
            individuals.add(nInd);
        }

        for (int i = 0; i < ranks.size(); ++i) {
            boolean dominates, dominated, nd;
            dominates = dominated = nd = false;

            for (Individual<T> ind: ranks.get(i).getMembers()) {
                int domComparisonResult = Utils.dominates(nInd.getObjectives(), ind.getObjectives(), nInd.getObjectives().length);
                //nInd.compareDom(ind);
                if (domComparisonResult == 0)
                    nd = true;
                else if (domComparisonResult > 0) {
                    dominated = true;
                    break;
                } else {
                    dominates = true;
                }
            }

            if (dominated)
                //noinspection UnnecessaryContinue
                continue;
            else if (!nd && dominates) {
                final Set<Individual<T>> newRank = new HashSet<>();
                newRank.add(nInd);
                ranks.add(i, new EnluNonDominationLevel<>(newRank, SortedObjectives.create(
                        nInd.getObjectives().length,
                        Collections.singletonList(nInd),
                        (ind, d) -> new FitnessAndCdIndividual<>(ind.getObjectives(), d, ind.getPayload())
                )));
                return i;
            } else { //enlu update procedure
                List<Individual<T>> dominatedList = Collections.singletonList(nInd);
                final int rs = i;
                while (!dominatedList.isEmpty()) {
                    if (i >= ranks.size()) {
                        ranks.add(i, new EnluNonDominationLevel<>(new HashSet<>(dominatedList), SortedObjectives.create(
                                nInd.getObjectives().length,
                                dominatedList,
                                (ind, d) -> new FitnessAndCdIndividual<>(ind.getObjectives(), d, ind.getPayload())
                        )));
                        return rs;
                    } else {
                        final NonDominationLevel.MemberAdditionResult<T, EnluNonDominationLevel<T>> mar =
                                ranks.get(i).addMembers(dominatedList);
                        dominatedList = mar.getEvictedMembers();
                        ranks.set(i, mar.getModifiedLevel());
                        ++i;
                    }
                }
                return rs;
            }
        }

        //Dominated by everyone
        ranks.add(new EnluNonDominationLevel<>(Collections.singleton(nInd), SortedObjectives.create(
                nInd.getObjectives().length,
                Collections.singletonList(nInd),
                (ind, d) -> new FitnessAndCdIndividual<>(ind.getObjectives(), d, ind.getPayload())
        )));
        return ranks.size() - 1;
    }

    private int detRankOfExPoint(Individual<T> ind) {
        for (int i = 0; i < ranks.size(); ++i) {
            if (ranks.get(i).getMembersSet().contains(ind))
                return i;
        }
        throw new RuntimeException("Point not exists");
    }

    @Nonnull
    @Override
    public synchronized PopulationSnapshot<T> getSnapshot() {
        final List<NonDominationLevel<T>> levelsSnap = new ArrayList<>();
        int size = 0;
        for (EnluNonDominationLevel<T> rank : ranks) {
            size += rank.getMembersSet().size();
            levelsSnap.add(rank.copy());
        }
        return new PopulationSnapshot<>(levelsSnap, size);
    }

    @Override
    public int size() {
        return individuals.size();
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public synchronized ManagedPopulation<T> clone() {
        final List<EnluNonDominationLevel<T>> ranksSnap = new ArrayList<>();
        for (EnluNonDominationLevel<T> rank : ranks) {
            ranksSnap.add(rank.copy());
        }
        return new EnluPopulation<>(new HashSet<>(individuals), ranksSnap, maxSize);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ENLUManagedPopulation{");
        sb.append("individuals=").append(individuals);
        sb.append(", ranks=").append(ranks);
        sb.append(", maxSize=").append(maxSize);
        sb.append('}');
        return sb.toString();
    }
}
