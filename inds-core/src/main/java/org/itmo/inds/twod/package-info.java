/**
 * Incremental non-dominated sorting for two objectives (thesis label <b>2D</b>): each level is a persistent Cartesian
 * tree (treap) ordered by the first objective, the levels are kept in a list. Inserting a point changes a contiguous
 * segment of each affected level, which is cut out with two splits and moved to the next level; O(N) insertion in the
 * worst case [CEC 2015]. This is the insertion-only version used in the CEC 2015 experiments, not the full "tree of
 * trees" of [T] Chapter 2: no removal, no crowding distance, and not a {@link org.itmo.inds.api.ManagedPopulation}.
 *
 * <ul>
 * <li>{@link org.itmo.inds.twod.TwoDimTreapPopulation}: the population ({@code addPoint}, {@code size},
 *     {@code getRandWithRank}).</li>
 * <li>{@link org.itmo.inds.twod.TwoDimTreap}: one level, an immutable treap with split / merge.</li>
 * <li>{@link org.itmo.inds.twod.TwoDimPoint}: a point with two objectives (minimized); equal points are stored
 *     once.</li>
 * </ul>
 *
 * <p>The classes are ported from the cited code with two fixes ({@code toString}; {@code detRankOfExPoint} with one
 * level). Known defects that remain: a static unsynchronized comparison counter, and {@code detRankOfExPoint} does not
 * return for some points that are not in the population. Both lists: {@code docs/changes-from-cited-code.md}.
 *
 * <p>References:
 * <ul>
 * <li>[T] I. Yakupov. Efficient Incremental and Asynchronous Non-Dominated Sorting for Evolutionary Multiobjective
 *     Algorithms. PhD thesis, ITMO University, 2019.</li>
 * <li>[CEC 2015] I. Yakupov, M. Buzdalov. Incremental Non-Dominated Sorting with O(N) Insertion for the
 *     Two-Dimensional Case. IEEE CEC 2015, pp. 1853-1860.</li>
 * </ul>
 */
package org.itmo.inds.twod;
