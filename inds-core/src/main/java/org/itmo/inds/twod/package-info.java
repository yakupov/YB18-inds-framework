/**
 * Incremental non-dominated sorting for two objectives (thesis label <b>2D</b>): each level is a persistent Cartesian
 * tree (treap) ordered by the first objective, the levels are kept in a list. Inserting a point changes a contiguous
 * segment of each affected level, which is cut out with two splits and moved to the next level; O(N) insertion in the
 * worst case [CEC 2015]. This is the insertion-only version used in the CEC 2015 experiments, not the full "tree of
 * trees" of [T] Chapter 2: no removal, no crowding distance, and not a {@link org.itmo.inds.api.ManagedPopulation}.
 *
 * <ul>
 * <li>{@link org.itmo.inds.twod.TwoDimTreapPopulation}: the population ({@code addPoint}, {@code size},
 *     {@code getRandWithRank}). Points are {@link org.itmo.inds.api.Individual}s with at least two objectives; they
 *     are ranked by the first two ({@code x1}, {@code x2}, minimized), further objectives are ignored. A point equal
 *     ({@code equals}) to one in the population is not added again.</li>
 * <li>{@link org.itmo.inds.twod.TwoDimTreap}: one level, an immutable treap with split / merge, ordered by
 *     {@code x1}.</li>
 * <li>{@link org.itmo.inds.twod.TwoDimBucket}: the content of a treap node: the points with the same
 *     {@code (x1, x2)}, which share a level.</li>
 * </ul>
 *
 * <p>The classes are ported from the cited code; the cited point type {@code Double2DIndividual} became the bucket.
 * Fixes: {@code detRankOfExPoint} with one level or with a point that is not in the population; see
 * {@code docs/changes-from-cited-code.md}.
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
