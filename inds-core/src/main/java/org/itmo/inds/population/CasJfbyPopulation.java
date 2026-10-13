package org.itmo.inds.population;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.api.PopulationSnapshot;
import org.itmo.inds.sorting.JfbSorter;
import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.util.AscLexSortComparator;
import org.itmo.inds.util.SortedObjectives;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.itmo.inds.util.Utils.*;

@ThreadSafe
public class CasJfbyPopulation<T> implements ManagedPopulation<T> {
    private class LevelRef {
        final long modificationTs;
        final JfbyNonDominationLevel<T> level;
        /**
         * Time taken after this version was published (fix of the time-stamping race, see
         * docs/changes-from-cited-code.md): {@code modificationTs} is taken before the CAS that publishes the version,
         * so it cannot tell whether a modification is older than an insertion. {@link Long#MAX_VALUE} until published.
         */
        volatile long publishedTs = Long.MAX_VALUE;

        LevelRef(long modificationTs, JfbyNonDominationLevel<T> level) {
            this.modificationTs = modificationTs;
            this.level = level;
        }

        LevelRef published(long ts) {
            publishedTs = ts;
            return this;
        }
    }

    private boolean casPublish(AtomicReference<LevelRef> ref, LevelRef expected, LevelRef update) {
        if (ref.compareAndSet(expected, update)) {
            update.published(time.incrementAndGet());
            return true;
        }
        return false;
    }

    private final CopyOnWriteArrayList<AtomicReference<LevelRef>> nonDominationLevels;
    private final AtomicLong time = new AtomicLong(0); //todo: process overflow

    private final Map<Individual<T>, Boolean> presentIndividuals = new ConcurrentHashMap<>();
    private final Lock addRemoveLevelLock = new ReentrantLock();
    private final AtomicInteger size = new AtomicInteger(0);

    private final JfbSorter sorter = new JfbSorter();
    private final int expectedPopulationSize; //Members will not be deleted if the size is less or equal to this value
    private final boolean useOneByOneSorting;

    @SuppressWarnings("WeakerAccess")
    public CasJfbyPopulation(int expectedPopulationSize, boolean useOneByOneSorting) {
        this(new CopyOnWriteArrayList<>(), expectedPopulationSize, useOneByOneSorting);
    }

    @SuppressWarnings("WeakerAccess")
    public CasJfbyPopulation(CopyOnWriteArrayList<AtomicReference<JfbyNonDominationLevel<T>>> nonDominationLevels,
                           int expectedPopulationSize,
                           boolean useOneByOneSorting) {
        this.nonDominationLevels = new CopyOnWriteArrayList<>();
        this.nonDominationLevels.addAll(nonDominationLevels.stream()
                .map(ref -> new AtomicReference<>(new LevelRef(0, ref.get()).published(0)))
                .collect(Collectors.toList()));

        this.expectedPopulationSize = expectedPopulationSize;
        this.useOneByOneSorting = useOneByOneSorting;

        if (!nonDominationLevels.isEmpty()) {
            int ctr = 0;
            for (AtomicReference<JfbyNonDominationLevel<T>> levelRef : nonDominationLevels) {
                for (Individual<T> individual : levelRef.get().getMembers()) {
                    presentIndividuals.put(individual, true);
                    ++ctr;
                }
            }
            size.set(ctr);
        }
    }

    @Override
    @Nonnull
    public PopulationSnapshot<T> getSnapshot() {
        int sizeSnapshot = 0;
        final Object[] levels = nonDominationLevels.toArray();
        @SuppressWarnings("unchecked") final List<NonDominationLevel<T>> levelsSnapshot = Arrays.asList(new NonDominationLevel[levels.length]);
        for (int j = levels.length - 1; j >= 0; --j) { //Reverse iterator because elements may only move to latter levels, and we do not want any collisions
            @SuppressWarnings("unchecked") final AtomicReference<LevelRef> levelRefRef =
                    (AtomicReference<LevelRef>) levels[j];
            final LevelRef levelRef = levelRefRef.get();
            if (levelRef != null) {
                final NonDominationLevel<T> level = levelRef.level;
                levelsSnapshot.set(j, level);
                sizeSnapshot += level.getMembers().size();
            }
        }
        final List<NonDominationLevel<T>> rs = new ArrayList<>();
        for (NonDominationLevel<T> level : levelsSnapshot) {
            if (level != null) {
                rs.add(level);
            }
        }
        return new PopulationSnapshot<>(rs, sizeSnapshot);
    }

    @Nullable
    Individual<T> intRemoveWorst() {
        while (true) {
            try {
                final int lastLevelIndex = nonDominationLevels.size() - 1;
                final LevelRef lastLevelRef = nonDominationLevels.get(lastLevelIndex).get();
                if (lastLevelRef == null) {
                    continue;
                }
                final JfbyNonDominationLevel<T> lastLevel = lastLevelRef.level;
                if (lastLevel.getMembers().size() <= 1) {
                    try {
                        addRemoveLevelLock.lock();
                        if (lastLevelIndex == nonDominationLevels.size() - 1 &&
                                nonDominationLevels.get(lastLevelIndex).compareAndSet(lastLevelRef, null)) {
                            nonDominationLevels.remove(lastLevelIndex);
                            if (lastLevel.getMembers().isEmpty()) {
                                return null;
                            } else {
                                final Individual<T> rs = lastLevel.getMembers().get(0);
                                presentIndividuals.remove(rs);
                                size.decrementAndGet();
                                return rs;
                            }
                        }
                    } finally {
                        addRemoveLevelLock.unlock();
                    }
                } else {
                    final Individual<T> removedIndividual = getWorstCDIndividual(lastLevel);
                    if (removedIndividual == null)
                        return null;

                    final JfbyNonDominationLevel<T> newLevel = removeIndividualFromLevel(lastLevel, removedIndividual, sorter);

                    if (casPublish(nonDominationLevels.get(lastLevelIndex),
                            lastLevelRef, new LevelRef(time.incrementAndGet(), newLevel))) {
                        presentIndividuals.remove(removedIndividual);
                        size.decrementAndGet();
                        return removedIndividual;
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
                //retry
            }
        }
    }

    @Override
    public int size() {
        return size.get();
    }

    private int determineMinimalPossibleRank(Individual point) {
        int l = 0;
        int r = nonDominationLevels.size() - 1;
        int lastNonDominating = r + 1;
        while (l <= r) {
            final int test = (l + r) / 2;
            try {
                final LevelRef levelRef = nonDominationLevels.get(test).get();
                if (levelRef == null) {
                    r--;
                } else if (!levelRef.level.dominatedByAnyPointOfThisLayer(point)) {
                    //Racy, but that's OK
                    lastNonDominating = test;
                    r = test - 1;
                } else {
                    l = test + 1;
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                r--;
            }
        }

        return lastNonDominating;
    }

    @Override
    public int addIndividual(@Nonnull Individual<T> addend) {
        final long insertionTs = time.incrementAndGet();
        int rank = determineMinimalPossibleRank(addend);
        if (presentIndividuals.putIfAbsent(addend, true) != null) {
            return rank;
        }

        final Integer firstModifiedLevelRank;
        if (useOneByOneSorting) {
            firstModifiedLevelRank = insertOneByOne(addend, rank, insertionTs);
        } else {
            firstModifiedLevelRank = insertViaFullSortings(addend, rank);
        }

        if (size.incrementAndGet() > expectedPopulationSize) {
            intRemoveWorst();
        }

        return Objects.requireNonNull(firstModifiedLevelRank, "Impossible situation: the point was not added");
    }

    private Integer insertOneByOne(@Nonnull final Individual<T> originalAddend, int rank, final long ts) {
        Integer firstModifiedLevelRank = null;
        List<Individual<T>> addends = new LinkedList<>();
        addends.add(originalAddend);
        while (!addends.isEmpty()) {
            try {
                if (rank >= nonDominationLevels.size()) {
                    if (tryToAddLevels(addends, rank)) {
                        if (firstModifiedLevelRank == null) {
                            return rank;
                        } else {
                            return firstModifiedLevelRank;
                        }
                    }
                } else {
                    final LevelRef levelRef = nonDominationLevels.get(rank).get();
                    if (levelRef == null) {
                        continue;
                    }
                    final JfbyNonDominationLevel<T> level = levelRef.level;
                    if (ts > levelRef.publishedTs) {
                        final NonDominationLevel.MemberAdditionResult<T, JfbyNonDominationLevel<T>> mar = level.addMembers(addends);
                        if (casPublish(nonDominationLevels.get(rank), levelRef, new LevelRef(time.incrementAndGet(), mar.getModifiedLevel()))) {
                            if (firstModifiedLevelRank == null) {
                                firstModifiedLevelRank = rank;
                            }
                            ++rank;
                            addends = mar.getEvictedMembers();
                        }
                    } else {
                        final Individual[] allMembers = lexMerge(addends, level.getMembers());
                        final int[] ranks = sorter.performNds(allMembers);
                        final List<Individual<T>> newCurrLevelMembers = new ArrayList<>();
                        final List<Individual<T>> nextAddends = new ArrayList<>();
                        final Set<Individual<T>> addendsSet = new HashSet<>(addends);
                        final List<Individual<T>> actualAddends = new ArrayList<>(addends.size());
                        final List<Individual<T>> actualRemovals = new ArrayList<>();
                        for (int i = 0; i < allMembers.length; ++i) {
                            if (ranks[i] == 0) {
                                //noinspection unchecked
                                newCurrLevelMembers.add(allMembers[i]);
                                if (addendsSet.contains(allMembers[i])) {
                                    //noinspection unchecked
                                    actualAddends.add(allMembers[i]);
                                }
                            } else {
                                //noinspection unchecked
                                nextAddends.add(allMembers[i]);
                                if (!addendsSet.contains(allMembers[i])) {
                                    //noinspection unchecked
                                    actualRemovals.add(allMembers[i]);
                                }
                            }
                        }

                        final SortedObjectives<Individual<T>, T> nso = level.getSortedObjectives().update(
                                actualAddends,
                                actualRemovals,
                                (i, d) -> new FitnessAndCdIndividual<>(i.getObjectives(), d, i.getPayload())
                        );

                        final JfbyNonDominationLevel<T> newLevel = new JfbyNonDominationLevel<>(sorter, nso.getLexSortedPop(), nso);
                        if (casPublish(nonDominationLevels.get(rank), levelRef, new LevelRef(time.incrementAndGet(), newLevel))) {
                            if (firstModifiedLevelRank == null) {
                                firstModifiedLevelRank = rank;
                            }
                            addends = nextAddends;
                            ++rank;
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
            }
        }

        return firstModifiedLevelRank;
    }

    private boolean tryToAddLevels(List<Individual<T>> addends, int minPossibleRank) {
        addRemoveLevelLock.lock();
        try {
            if (minPossibleRank >= nonDominationLevels.size()) {
                final Individual[] members = addends.toArray(new Individual[addends.size()]);
                final int[] ranks = sorter.performNds(members);
                final List<List<Individual<T>>> levels = new ArrayList<>();
                for (int i = 0; i < members.length; ++i) {
                    while (levels.size() <= ranks[i]) {
                        levels.add(new ArrayList<>());
                    }
                    //noinspection unchecked
                    levels.get(ranks[i]).add(members[i]);
                }
                for (List<Individual<T>> level : levels) {
                    final LevelRef levelRef = new LevelRef(time.incrementAndGet(), new JfbyNonDominationLevel<>(sorter, level));
                    nonDominationLevels.add(new AtomicReference<>(levelRef));
                    levelRef.published(time.incrementAndGet());
                }
                return true;
            }
        } finally {
            addRemoveLevelLock.unlock();
        }
        return false;
    }

    private Integer insertViaFullSortings(@Nonnull Individual<T> addend, int rank) {
        Integer firstModifiedLevelRank = null;
        List<Individual<T>> addends = new ArrayList<>();
        addends.add(addend);
        while (!addends.isEmpty()) {
            try {
                if (rank >= nonDominationLevels.size()) {
                    if (tryToAddLevels(addends, rank)) {
                        if (firstModifiedLevelRank == null) {
                            return rank;
                        } else {
                            return firstModifiedLevelRank;
                        }
                    }
                } else {
                    final LevelRef levelRef = nonDominationLevels.get(rank).get();
                    if (levelRef == null) {
                        continue;
                    }
                    final Individual[] allMembers = lexMerge(addends, levelRef.level.getMembers());
                    final int[] ranks = sorter.performNds(allMembers);
                    final List<Individual<T>> newCurrLevelMembers = new ArrayList<>();
                    final List<Individual<T>> nextAddends = new ArrayList<>();
                    for (int i = 0; i < allMembers.length; ++i) {
                        if (ranks[i] == 0) {
                            //noinspection unchecked
                            newCurrLevelMembers.add(allMembers[i]);
                        } else {
                            //noinspection unchecked
                            nextAddends.add(allMembers[i]);
                        }
                    }

                    final Set<Individual<T>> addendsSet = new HashSet<>(addends);
                    final List<Individual<T>> removed = new ArrayList<>(nextAddends.size());
                    for (Individual<T> nextAddend : nextAddends) {
                        if (addendsSet.contains(nextAddend)) {
                            addendsSet.remove(nextAddend);
                        } else {
                            removed.add(nextAddend);
                        }
                    }

                    final List<Individual<T>> addendsList = new ArrayList<>(addendsSet.size());
                    addendsList.addAll(addendsSet);
                    addendsList.sort(AscLexSortComparator.getInstance());

                    final SortedObjectives<Individual<T>, T> nso = levelRef.level.getSortedObjectives().update(
                            addendsList,
                            removed,
                            (i, d) -> new FitnessAndCdIndividual<>(i.getObjectives(), d, i.getPayload())
                    );

                    final JfbyNonDominationLevel<T> newLevel = new JfbyNonDominationLevel<>(sorter, nso.getLexSortedPop(), nso);
                    if (casPublish(nonDominationLevels.get(rank), levelRef, new LevelRef(time.incrementAndGet(), newLevel))) {
                        if (firstModifiedLevelRank == null) {
                            firstModifiedLevelRank = rank;
                        }
                        addends = nextAddends;
                        ++rank;
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
            }
        }
        return firstModifiedLevelRank;
    }

    private Individual[] lexMerge(@Nonnull final List<Individual<T>> aList,
                                   @Nonnull final List<Individual<T>> mList) {
        int ai = 0;
        int mi = 0;
        final Individual[] result = new Individual[aList.size() + mList.size()];
        while (mi < mList.size() || ai < aList.size()) {
            if (mi >= mList.size()) {
                result[ai + mi] = aList.get(ai);
                ++ai;
            } else if (ai >= aList.size()) {
                result[ai + mi] = mList.get(mi);
                ++mi;
            } else {
                final Individual m = mList.get(mi);
                final Individual a = aList.get(ai);
                if (lexCompare(m.getObjectives(), a.getObjectives(), a.getObjectives().length) <= 0) {
                    result[ai + mi] = m;
                    ++mi;
                } else {
                    result[ai + mi] = a;
                    ++ai;
                }
            }
        }
        return result;
    }

    /**
     * @return A copy of this population. All layers are also copied.
     * Thread-safe because of the COW snapshot iterator
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public CasJfbyPopulation<T> clone() {
        final CopyOnWriteArrayList<AtomicReference<JfbyNonDominationLevel<T>>> list = new CopyOnWriteArrayList<>();
        for (AtomicReference<LevelRef> levelRef : nonDominationLevels) {
            final LevelRef ref = levelRef.get();
            if (ref != null) { //the level is being removed
                list.add(new AtomicReference<>(ref.level.copy()));
            }
        }
        return new CasJfbyPopulation<>(list, expectedPopulationSize, useOneByOneSorting);
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CJFBYPopulation{");
        sb.append("nonDominationLevels=").append(nonDominationLevels);
        sb.append(", time=").append(time);
        sb.append(", presentIndividuals=").append(presentIndividuals);
        sb.append(", addRemoveLevelLock=").append(addRemoveLevelLock);
        sb.append(", size=").append(size);
        sb.append(", sorter=").append(sorter);
        sb.append(", expectedPopulationSize=").append(expectedPopulationSize);
        sb.append(", useOneByOneSorting=").append(useOneByOneSorting);
        sb.append('}');
        return sb.toString();
    }
}
