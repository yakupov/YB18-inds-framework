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

import static org.itmo.inds.util.Utils.*;

@SuppressWarnings("rawtypes")
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
        /**
         * True if a level follows this one or is being added behind it. Eviction cuts only the last level, which is
         * the one that is not sealed. Levels are sealed and unsealed under {@code addRemoveLevelLock}.
         */
        final boolean sealed;

        LevelRef(long modificationTs, JfbyNonDominationLevel<T> level) {
            this(modificationTs, level, false);
        }

        LevelRef(long modificationTs, JfbyNonDominationLevel<T> level, boolean sealed) {
            this.modificationTs = modificationTs;
            this.level = level;
            this.sealed = sealed;
        }

        /**
         * Records the time taken after the CAS that made this version visible.
         *
         * @return this
         */
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
    private final AtomicLong time = new AtomicLong(0);
    /**
     * Counts finished eviction steps (a level or a member of the last level removed). An insertion checks the rank of
     * its points again ({@link #justify}) if the count changed since the rank was determined.
     */
    private final AtomicLong evictionEpoch = new AtomicLong(0);

    /**
     * Addends split by whether the level before their rank dominates them.
     */
    private record Justification<E>(List<E> justified, List<E> unjustified) {
    }

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
        for (int i = 0; i < nonDominationLevels.size(); ++i) {
            this.nonDominationLevels.add(new AtomicReference<>(
                    new LevelRef(0, nonDominationLevels.get(i).get(), i < nonDominationLevels.size() - 1).published(0)));
        }

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
            final int lastLevelIndex = nonDominationLevels.size() - 1;
            if (lastLevelIndex < 0) { //no level at the moment: the population is being filled or emptied by other threads
                awaitLevelChange();
                continue;
            }
            final LevelRef lastLevelRef = levelAt(lastLevelIndex);
            if (lastLevelRef == null || lastLevelRef.sealed) { //removed, or a level is being added behind it
                awaitLevelChange();
                continue;
            }
            final JfbyNonDominationLevel<T> lastLevel = lastLevelRef.level;
            if (lastLevel.getMembers().size() <= 1) {
                try {
                    addRemoveLevelLock.lock();
                    if (lastLevelIndex == nonDominationLevels.size() - 1 &&
                            nonDominationLevels.get(lastLevelIndex).compareAndSet(lastLevelRef, null)) {
                        nonDominationLevels.remove(lastLevelIndex);
                        unseal(lastLevelIndex - 1);
                        evictionEpoch.incrementAndGet();
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

                //succeeds only if the level is still unsealed, that is, still the last one
                if (casPublish(lastLevelIndex, lastLevelRef, new LevelRef(time.incrementAndGet(), newLevel))) {
                    evictionEpoch.incrementAndGet();
                    presentIndividuals.remove(removedIndividual);
                    size.decrementAndGet();
                    return removedIndividual;
                }
            }
        }
    }

    /**
     * @return the reference of the level at the index, null if there is no such level because it was removed
     */
    @Nullable
    private AtomicReference<LevelRef> levelSlot(final int index) {
        try {
            return nonDominationLevels.get(index);
        } catch (final IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * @return the current version of the level at the index, null if the level was removed or is being removed
     */
    @Nullable
    private LevelRef levelAt(final int index) {
        final AtomicReference<LevelRef> slot = levelSlot(index);
        return slot == null ? null : slot.get();
    }

    /**
     * Publishes {@code update} as the next version of the level at the index if it is still {@code expected}.
     *
     * @return false if it is not, or if the level was removed
     */
    private boolean casPublish(final int index, final LevelRef expected, final LevelRef update) {
        final AtomicReference<LevelRef> slot = levelSlot(index);
        return slot != null && casPublish(slot, expected, update);
    }

    /**
     * Waits until the thread that removes a level or adds levels has released the lock, that is, the levels are
     * consistent again.
     */
    private void awaitLevelChange() {
        addRemoveLevelLock.lock();
        addRemoveLevelLock.unlock();
    }

    @Override
    public int size() {
        return size.get();
    }

    private int determineMinimalPossibleRank(Individual<T> point) {
        int l = 0;
        int r = nonDominationLevels.size() - 1;
        int lastNonDominating = r + 1;
        while (l <= r) {
            final int test = (l + r) / 2;
            final LevelRef levelRef = levelAt(test);
            if (levelRef == null) { //removed or being removed
                r--;
            } else if (!levelRef.level.dominatedByAnyPointOfThisLayer(point)) {
                //Racy, but that's OK
                lastNonDominating = test;
                r = test - 1;
            } else {
                l = test + 1;
            }
        }

        return lastNonDominating;
    }

    @Override
    public int addIndividual(@Nonnull Individual<T> addend) {
        final long insertionTs = time.incrementAndGet();
        final long epoch = evictionEpoch.get(); //before the rank is determined
        int rank = determineMinimalPossibleRank(addend);
        if (presentIndividuals.putIfAbsent(addend, true) != null) {
            return rank;
        }

        final Integer firstModifiedLevelRank;
        if (useOneByOneSorting) {
            firstModifiedLevelRank = insertOneByOne(addend, rank, insertionTs, epoch);
        } else {
            firstModifiedLevelRank = insertViaFullSortings(addend, rank, epoch);
        }

        if (size.incrementAndGet() > expectedPopulationSize) {
            intRemoveWorst();
        }

        return Objects.requireNonNull(firstModifiedLevelRank, "Impossible situation: the point was not added");
    }

    /**
     * Splits the addends by whether the level before the rank (the last level if the rank is not below the level count)
     * dominates them. Eviction can remove that level or its members, which lowers the rank of the points. Nothing is
     * needed before level 0.
     */
    private Justification<Individual<T>> justify(final List<Individual<T>> addends, final int rank) {
        final int previous = Math.min(rank, nonDominationLevels.size()) - 1;
        if (previous < 0) {
            return new Justification<>(addends, new ArrayList<>());
        }
        final LevelRef previousLevelRef = levelAt(previous);
        final List<Individual<T>> justified = new ArrayList<>();
        final List<Individual<T>> unjustified = new ArrayList<>();
        for (final Individual<T> addend : addends) {
            if (previousLevelRef != null && previousLevelRef.level.dominatedByAnyPointOfThisLayer(addend)) {
                justified.add(addend);
            } else { //also if the level before is being removed
                unjustified.add(addend);
            }
        }
        return new Justification<>(justified, unjustified);
    }

    /**
     * Inserts the points again as new points, each with its own rank.
     *
     * @return the rank of the first level changed by the insertion of {@code original}, null if it is not among the points
     */
    private Integer reinsert(final List<Individual<T>> points, final Individual<T> original) {
        Integer originalFirstModifiedLevelRank = null;
        for (final Individual<T> point : points) {
            final long ts = time.incrementAndGet();
            final long epoch = evictionEpoch.get(); //before the rank is determined
            final int rank = determineMinimalPossibleRank(point);
            final Integer firstModifiedLevelRank = useOneByOneSorting
                    ? insertOneByOne(point, rank, ts, epoch)
                    : insertViaFullSortings(point, rank, epoch);
            if (point == original) {
                originalFirstModifiedLevelRank = firstModifiedLevelRank;
            }
        }
        return originalFirstModifiedLevelRank;
    }

    private Integer insertOneByOne(@Nonnull final Individual<T> originalAddend, int rank, final long ts,
                                   final long initialEpoch) {
        Integer firstModifiedLevelRank = null;
        long justifiedEpoch = initialEpoch;
        List<Individual<T>> addends = new LinkedList<>();
        addends.add(originalAddend);
        while (!addends.isEmpty()) {
            final long epoch = evictionEpoch.get();
            if (epoch != justifiedEpoch) { //an eviction step finished since the rank was justified
                final Justification<Individual<T>> justification = justify(addends, rank);
                justifiedEpoch = epoch;
                if (!justification.unjustified().isEmpty()) {
                    addends = justification.justified();
                    final Integer reinsertedFirstModifiedLevelRank = reinsert(justification.unjustified(), originalAddend);
                    if (firstModifiedLevelRank == null) {
                        firstModifiedLevelRank = reinsertedFirstModifiedLevelRank;
                    }
                    continue;
                }
            }
            if (rank >= nonDominationLevels.size()) {
                if (tryToAddLevels(addends, rank, justifiedEpoch)) {
                    if (firstModifiedLevelRank == null) {
                        return rank;
                    } else {
                        return firstModifiedLevelRank;
                    }
                }
            } else {
                final LevelRef levelRef = levelAt(rank);
                if (levelRef == null) { //removed or being removed
                    awaitLevelChange();
                    continue;
                }
                final JfbyNonDominationLevel<T> level = levelRef.level;
                if (ts > levelRef.publishedTs) {
                    final NonDominationLevel.MemberAdditionResult<T, JfbyNonDominationLevel<T>> mar = level.addMembers(addends);
                    if (casPublish(rank, levelRef, new LevelRef(time.incrementAndGet(), mar.getModifiedLevel(), levelRef.sealed))) {
                        if (firstModifiedLevelRank == null) {
                            firstModifiedLevelRank = rank;
                        }
                        ++rank;
                        addends = mar.getEvictedMembers();
                    }
                } else {
                    final Individual[] allMembers = lexMerge(addends, level.getMembers());
                    final int[] ranks = sorter.performNds(allMembers);
                    final List<Individual<T>> nextAddends = new ArrayList<>();
                    final Set<Individual<T>> addendsSet = new HashSet<>(addends);
                    final List<Individual<T>> actualAddends = new ArrayList<>(addends.size());
                    final List<Individual<T>> actualRemovals = new ArrayList<>();
                    for (int i = 0; i < allMembers.length; ++i) {
                        if (ranks[i] == 0) {
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
                    if (casPublish(rank, levelRef, new LevelRef(time.incrementAndGet(), newLevel, levelRef.sealed))) {
                        if (firstModifiedLevelRank == null) {
                            firstModifiedLevelRank = rank;
                        }
                        addends = nextAddends;
                        ++rank;
                    }
                }
            }
        }

        return firstModifiedLevelRank;
    }

    /**
     * With the lock held: seals the last level. If {@code addends} is given and an eviction step finished since
     * {@code justifiedEpoch}, the last level must still dominate all of them; the sealing CAS commits that check.
     *
     * @return false (nothing sealed) if it does not
     */
    private boolean sealLastLevel(final List<Individual<T>> addends, final long justifiedEpoch) {
        while (true) {
            final int lastLevelIndex = nonDominationLevels.size() - 1;
            if (lastLevelIndex < 0) {
                return true;
            }
            final AtomicReference<LevelRef> ref = nonDominationLevels.get(lastLevelIndex);
            final LevelRef lastLevelRef = ref.get();
            if (addends != null && evictionEpoch.get() != justifiedEpoch) {
                for (final Individual<T> addend : addends) {
                    if (!lastLevelRef.level.dominatedByAnyPointOfThisLayer(addend)) {
                        return false;
                    }
                }
            }
            if (lastLevelRef.sealed || casPublish(ref, lastLevelRef,
                    new LevelRef(time.incrementAndGet(), lastLevelRef.level, true))) {
                return true;
            }
        }
    }

    /**
     * With the lock held, after the level behind the one at the index was removed: it is the last level again.
     */
    private void unseal(final int index) {
        if (index < 0) {
            return;
        }
        final AtomicReference<LevelRef> ref = nonDominationLevels.get(index);
        while (true) {
            final LevelRef levelRef = ref.get();
            if (!levelRef.sealed
                    || casPublish(ref, levelRef, new LevelRef(time.incrementAndGet(), levelRef.level, false))) {
                return;
            }
        }
    }

    private boolean tryToAddLevels(List<Individual<T>> addends, int minPossibleRank, final long justifiedEpoch) {
        addRemoveLevelLock.lock();
        try {
            if (minPossibleRank >= nonDominationLevels.size()) {
                if (!sealLastLevel(addends, justifiedEpoch)) {
                    return false; //the last level does not dominate the points: the caller inserts them again
                }
                final Individual[] members = addends.toArray(new Individual[addends.size()]);
                final int[] ranks = sorter.performNds(members);
                final List<List<Individual<T>>> levels = new ArrayList<>();
                for (int i = 0; i < members.length; ++i) {
                    while (levels.size() <= ranks[i]) {
                        levels.add(new ArrayList<>());
                    }
                    levels.get(ranks[i]).add(members[i]);
                }
                boolean first = true;
                for (List<Individual<T>> level : levels) {
                    if (!first) {
                        sealLastLevel(null, 0);
                    }
                    first = false;
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

    private Integer insertViaFullSortings(@Nonnull Individual<T> addend, int rank, final long initialEpoch) {
        Integer firstModifiedLevelRank = null;
        long justifiedEpoch = initialEpoch;
        List<Individual<T>> addends = new ArrayList<>();
        addends.add(addend);
        while (!addends.isEmpty()) {
            final long epoch = evictionEpoch.get();
            if (epoch != justifiedEpoch) { //an eviction step finished since the rank was justified
                final Justification<Individual<T>> justification = justify(addends, rank);
                justifiedEpoch = epoch;
                if (!justification.unjustified().isEmpty()) {
                    addends = justification.justified();
                    final Integer reinsertedFirstModifiedLevelRank = reinsert(justification.unjustified(), addend);
                    if (firstModifiedLevelRank == null) {
                        firstModifiedLevelRank = reinsertedFirstModifiedLevelRank;
                    }
                    continue;
                }
            }
            if (rank >= nonDominationLevels.size()) {
                if (tryToAddLevels(addends, rank, justifiedEpoch)) {
                    if (firstModifiedLevelRank == null) {
                        return rank;
                    } else {
                        return firstModifiedLevelRank;
                    }
                }
            } else {
                final LevelRef levelRef = levelAt(rank);
                if (levelRef == null) { //removed or being removed
                    awaitLevelChange();
                    continue;
                }
                final Individual[] allMembers = lexMerge(addends, levelRef.level.getMembers());
                final int[] ranks = sorter.performNds(allMembers);
                final List<Individual<T>> nextAddends = new ArrayList<>();
                for (int i = 0; i < allMembers.length; ++i) {
                    if (ranks[i] != 0) {
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
                if (casPublish(rank, levelRef, new LevelRef(time.incrementAndGet(), newLevel, levelRef.sealed))) {
                    if (firstModifiedLevelRank == null) {
                        firstModifiedLevelRank = rank;
                    }
                    addends = nextAddends;
                    ++rank;
                }
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
                final Individual<T> m = mList.get(mi);
                final Individual<T> a = aList.get(ai);
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

    @Override
    public String toString() {
        return "CJFBYPopulation{" + "nonDominationLevels=" + nonDominationLevels +
                ", time=" + time +
                ", presentIndividuals=" + presentIndividuals +
                ", addRemoveLevelLock=" + addRemoveLevelLock +
                ", size=" + size +
                ", sorter=" + sorter +
                ", expectedPopulationSize=" + expectedPopulationSize +
                ", useOneByOneSorting=" + useOneByOneSorting +
                '}';
    }
}
