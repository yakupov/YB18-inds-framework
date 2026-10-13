package org.itmo.inds.population;

import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.itmo.inds.api.NonDominationLevel;
import org.itmo.inds.api.PopulationSnapshot;
import org.itmo.inds.sorting.IncrementalJfbSorter;
import org.itmo.inds.sorting.JfbSorter;
import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.util.median.QuickSelect;
import org.itmo.inds.util.SortedObjectives;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class FineLockJfbyPopulationRankHint<T> implements ManagedPopulation<T> {
    private static final double DEFAULT_DELETION_THRESHOLD = 1.2;

    private final List<Lock> levelLocks = new CopyOnWriteArrayList<>();
    private final Lock addLevelLock = new ReentrantLock();
    private final Lock removeLevelLock = new ReentrantLock();

    private final JfbSorter sorter;

    private final AtomicInteger size = new AtomicInteger(0);
    private final CopyOnWriteArrayList<JfbyNonDominationLevel<T>> nonDominationLevels;
    private final Map<Individual<T>, Boolean> presentIndividuals = new ConcurrentHashMap<>();

    private final long expectedPopSize;
    private final double deletionThreshold;

    @SuppressWarnings("WeakerAccess")
    public FineLockJfbyPopulationRankHint() {
        this(Long.MAX_VALUE);
    }

    @SuppressWarnings("WeakerAccess")
    public FineLockJfbyPopulationRankHint(long expectedPopSize) {
        this(new IncrementalJfbSorter(), new CopyOnWriteArrayList<>(), expectedPopSize, DEFAULT_DELETION_THRESHOLD);
    }

    @SuppressWarnings("WeakerAccess")
    public FineLockJfbyPopulationRankHint(final long expectedPopSize,
                                   final double deletionThreshold) {
        this(new IncrementalJfbSorter(), new CopyOnWriteArrayList<>(), expectedPopSize, deletionThreshold);
    }

    @SuppressWarnings("WeakerAccess")
    public FineLockJfbyPopulationRankHint(@Nonnull final JfbSorter sorter,
                                   @Nonnull final CopyOnWriteArrayList<JfbyNonDominationLevel<T>> nonDominationLevels,
                                   final long expectedPopSize,
                                   final double deletionThreshold) {
        this.sorter = sorter;
        this.nonDominationLevels = nonDominationLevels;
        this.expectedPopSize = expectedPopSize;
        this.deletionThreshold = deletionThreshold;

        for (NonDominationLevel<T> level : nonDominationLevels) {
            levelLocks.add(new ReentrantLock());
            size.addAndGet(level.getMembers().size());
            for (Individual<T> individual : level.getMembers()) {
                presentIndividuals.put(individual, true);
            }
        }
    }

    @Override
    @Nonnull
    public PopulationSnapshot<T> getSnapshot() {
        final ArrayList<NonDominationLevel<T>> rs = new ArrayList<>(nonDominationLevels);
        int size = 0;
        for (NonDominationLevel<T> level : rs) {
            size += level.getMembers().size();
        }
        return new PopulationSnapshot<>(rs, size);
    }

    @Nonnull
    @Override
    public List<? extends NonDominationLevel<T>> getLevelsUnsafe() {
        return Collections.unmodifiableList(nonDominationLevels);
    }

    @SuppressWarnings("UnusedReturnValue")
    private int massRemoveWorst() {
        if (size.get() > expectedPopSize * deletionThreshold && removeLevelLock.tryLock()) {
            try {
                final int toDelete = (int) (size.get() - expectedPopSize);
                int remaining = toDelete;
                while (remaining > 0) {
                    final int lastLevelIndex = nonDominationLevels.size() - 1;
                    final Lock lastLevelLock;

                    lastLevelLock = acquireLock(lastLevelIndex);
                    addLevelLock.lock();
                    if (lastLevelLock != levelLocks.get(lastLevelIndex)
                            || lastLevelIndex != nonDominationLevels.size() - 1) {
                        lastLevelLock.unlock();
                        addLevelLock.unlock();
                        continue;
                    }

                    try {
                        final JfbyNonDominationLevel<T> lastLevel = nonDominationLevels.get(lastLevelIndex);
                        if (lastLevel.getMembers().size() <= remaining) {
                            levelLocks.remove(lastLevelIndex);
                            nonDominationLevels.remove(lastLevelIndex);
                            if (lastLevel.getMembers().isEmpty()) {
                                System.err.println("Empty last ND level! Levels = " + nonDominationLevels);
                            } else {
                                for (Individual individual : lastLevel.getMembers()) {
                                    presentIndividuals.remove(individual);
                                }
                            }
                            remaining -= lastLevel.getMembers().size();
                        } else {
                            final double[] cd = new double[lastLevel.getMembers().size()];
                            int i = 0;
                            for (Individual cdIndividual : lastLevel.getMembers()) {
                                cd[i++] = cdIndividual.getCrowdingDistance();
                            }
                            final double cdThreshold = new QuickSelect().getKthElement(cd, remaining);
                            final List<Individual<T>> newMembers = new ArrayList<>();
                            final List<Individual<T>> removals = new ArrayList<>();
                            for (Individual<T> individual : lastLevel.getMembers()) {
                                if (remaining > 0 && individual.getCrowdingDistance() <= cdThreshold) {
                                    presentIndividuals.remove(individual);
                                    removals.add(individual);
                                    --remaining;
                                } else {
                                    newMembers.add(individual);
                                }
                            }
                            if (newMembers.isEmpty()) {
                                System.err.println("Empty members generated");
                                System.out.println(cdThreshold);
                                System.out.println(Arrays.toString(cd));
                                throw new RuntimeException(remaining + "<" + lastLevel.getMembers().size());
                            }

//                            System.err.println("Level: " + lastLevel.getMembers());
//                            System.err.println("Removin': " + removals);

                            final SortedObjectives<Individual<T>, T> nso = lastLevel.getSortedObjectives().update(
                                    Collections.emptyList(),
                                    removals,
                                    (i1, d) -> new FitnessAndCdIndividual<>(i1.getObjectives(), d, i1.getPayload())
                            );

                            final JfbyNonDominationLevel<T> newLevel = new JfbyNonDominationLevel<>(sorter, nso.getLexSortedPop(), nso);
                            nonDominationLevels.set(lastLevelIndex, newLevel);
                        }

                    } finally {
                        lastLevelLock.unlock();
                        addLevelLock.unlock();
                    }
                }
                if (toDelete > 0) {
                    int tSize = size.get();
                    while (!size.compareAndSet(tSize, tSize - toDelete)) {
                        tSize = size.get();
                    }
                }
                return toDelete;
            } finally {
                removeLevelLock.unlock();
            }
        }
        return 0;
    }

    @Override
    public int size() {
        return size.get();
    }

    private int determineRank(Individual<T> point, int rankHint) {
        if (rankHint < 0) {
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
        } else {
            return rankHint + 1;
        }
    }

    @Override
    public int addIndividual(@Nonnull Individual<T> addend) {
        if (presentIndividuals.putIfAbsent(addend, true) != null) {
            return determineRank(addend);
        }

        int rank = -1;
        while (true) { //TODO: under remove level lock? ReentrantReadWriteLock?
            rank = determineRank(addend, rank);
            final Lock lock = acquireLock(rank);
            try {
                if (rank >= nonDominationLevels.size() && lock == addLevelLock) {
                    break;
                } else if (!nonDominationLevels.get(rank).dominatedByAnyPointOfThisLayer(addend)) {
                    break;
                } else {
                    lock.unlock();
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
                lock.unlock();
            }
        }

        //Locked current level or all level addition

        if (rank >= nonDominationLevels.size()) {
            final List<Individual<T>> individuals = Collections.singletonList(addend);
            final JfbyNonDominationLevel<T> level = new JfbyNonDominationLevel<>(
                    sorter,
                    individuals
            );
            levelLocks.add(new ReentrantLock());
            nonDominationLevels.add(level);
            addLevelLock.unlock();
        } else {
            List<Individual<T>> addends = Collections.singletonList(addend);
            int i = rank;
            while (!addends.isEmpty() && i < nonDominationLevels.size()) {
                //assertion: we have locked levelLocks.get(i)
                try {
                    final JfbyNonDominationLevel<T> level = nonDominationLevels.get(i);
                    final NonDominationLevel.MemberAdditionResult<T, JfbyNonDominationLevel<T>> memberAdditionResult = level.addMembers(addends);

                    if (!memberAdditionResult.getEvictedMembers().isEmpty()) {
                        acquireLock(i + 1);
                    }
                    nonDominationLevels.set(i, memberAdditionResult.getModifiedLevel());
                    addends = memberAdditionResult.getEvictedMembers();
                } finally {
                    levelLocks.get(i).unlock();
                }

                i++;
            }
            if (!addends.isEmpty()) {
                levelLocks.add(new ReentrantLock());
                final JfbyNonDominationLevel<T> level = new JfbyNonDominationLevel<>(sorter, addends); //New level - full CD calc
                nonDominationLevels.add(level);
                addLevelLock.unlock();
            }
        }

        size.incrementAndGet();
        massRemoveWorst();

        return rank;
    }

    private Lock acquireLock(int rank) {
        while (true) {
            Lock lock = null;
            try {
                if (rank < nonDominationLevels.size()) {
                    lock = levelLocks.get(rank);
                    lock.lock();
                    if (levelLocks.get(rank) == lock) {
                        return lock;
                    } else {
                        lock.unlock();
                    }
                } else {
                    addLevelLock.lock();
                    if (rank < nonDominationLevels.size()) {
                        addLevelLock.unlock();
                    } else {
                        return addLevelLock;
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
                if (lock != null) {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * @return A copy of this population. All layers are also copied. During concurrent insertions, the copy is as
     * consistent as {@link #getSnapshot()}.
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public FineLockJfbyPopulationRankHint<T> clone() {
        final List<JfbyNonDominationLevel<T>> levels = new ArrayList<>(nonDominationLevels.size());
        for (JfbyNonDominationLevel<T> level : nonDominationLevels) {
            levels.add(level.copy());
        }
        return new FineLockJfbyPopulationRankHint<>(sorter, new CopyOnWriteArrayList<>(levels), expectedPopSize, deletionThreshold);
    }
}
