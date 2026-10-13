/**
 * Incremental non-dominated sorting for two objectives (thesis label <b>2D</b>): each level is a persistent Cartesian
 * tree (treap) ordered by the first objective, the levels are kept in a list. Inserting a point changes a contiguous
 * segment of each affected level, which is cut out with two splits and moved to the next level; O(N) insertion in the
 * worst case [CEC 2015]. The population is a {@link org.itmo.inds.api.ManagedPopulation} with the operations of the
 * steady-state data structure of [T] Chapter 2 and [GECCO 2015]: insertion, eviction of the worst point of the last
 * level above an expected size, random points with their ranks, and crowding distances computed on demand from a
 * point's neighbours in its level.
 *
 * <ul>
 * <li>{@link org.itmo.inds.twod.TwoDimTreapPopulation}: the population. Points are
 *     {@link org.itmo.inds.api.Individual}s with at least two objectives; they are ranked by the first two
 *     ({@code x1}, {@code x2}, minimized), and crowding distances use these two only. A point equal ({@code equals})
 *     to one in the population is not added again.</li>
 * <li>{@link org.itmo.inds.twod.TwoDimTreap}: one level, an immutable treap with split / merge, ordered by
 *     {@code x1}.</li>
 * <li>{@link org.itmo.inds.twod.TwoDimBucket}: the content of a treap node: the points with the same
 *     {@code (x1, x2)}, which share a level.</li>
 * <li>{@link org.itmo.inds.twod.TwoDimLevel}: a level of a snapshot, with the crowding distances.</li>
 * </ul>
 *
 * <p>The insertion is the cited code; the cited point type {@code Double2DIndividual} became the bucket. See
 * {@code docs/changes-from-cited-code.md}.
 *
 * <p>References:
 * <ul>
 * <li>[T] I. Yakupov. Efficient Incremental and Asynchronous Non-Dominated Sorting for Evolutionary Multiobjective
 *     Algorithms. PhD thesis, ITMO University, 2019.</li>
 * <li>[GECCO 2015] M. Buzdalov, I. Yakupov, A. Stankevich. Fast Implementation of the Steady-State NSGA-II
 *     Algorithm for Two Dimensions Based on Incremental Non-Dominated Sorting. GECCO 2015, pp. 647-654.</li>
 * <li>[CEC 2015] I. Yakupov, M. Buzdalov. Incremental Non-Dominated Sorting with O(N) Insertion for the
 *     Two-Dimensional Case. IEEE CEC 2015, pp. 1853-1860.</li>
 * </ul>
 */
package org.itmo.inds.twod;
