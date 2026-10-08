package org.itmo.inds.api;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Non-domination level
 */
public interface NonDominationLevel<T> {
    class MemberAdditionResult<T1, L extends NonDominationLevel<T1>> {
        private final List<Individual<T1>> evicted;
        private final L modifiedLevel;

        public MemberAdditionResult(List<Individual<T1>> evicted, L modifiedLevel) {
            this.evicted = evicted;
            this.modifiedLevel = modifiedLevel;
        }

        public List<Individual<T1>> getEvictedMembers() {
            return evicted;
        }

        public L getModifiedLevel() {
            return modifiedLevel;
        }
    }

    /**
     * @return Lexicographically sorted members of this layer with respective CD
     */
    List<Individual<T>> getMembers();

    /**
     * Add new points (assuming that their ranks equal the rank of this level).
     *
     * @param addends New points
     * @return A set of evicted points that should be moved to the next level
     */
    MemberAdditionResult addMembers(@Nonnull List<Individual<T>> addends);

    /**
     * @return true if {@code point} is dominated by any member of this layer
     */
    boolean dominatedByAnyPointOfThisLayer(@Nonnull Individual<T> point);

    /**
     * @return Shallow copy of this layer
     */
    NonDominationLevel<T> copy();
}
