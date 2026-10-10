/**
 * Incremental and asynchronous non-dominated sorting populations for any number of objectives: insert a point, query
 * levels (ranks) with crowding distances, remove the worst point. Labels follow the thesis [T]; references are listed
 * below. The two-objective population (2D) is in {@link org.itmo.inds.twod}.
 *
 * <table>
 * <caption>Algorithms and classes</caption>
 * <tr><th>Label</th><th>Idea</th><th>Classes</th><th>References</th></tr>
 * <tr><td><b>Level</b></td>
 *     <td>Level-wise incremental non-dominated sorting with incremental crowding distance (called INDS in Chapter 4)</td>
 *     <td>{@link org.itmo.inds.population.JfbyPopulation}, {@link org.itmo.inds.population.JfbyNonDominationLevel}</td>
 *     <td>[T] Section 3.2.5; [GECCO 2017]</td></tr>
 * <tr><td><b>Coarse</b></td>
 *     <td>Level behind a global lock: the asynchronous baseline</td>
 *     <td>{@link org.itmo.inds.population.CoarseLockJfbyPopulation}</td>
 *     <td>[T] Section 4.2; [GECCO 2018]</td></tr>
 * <tr><td><b>Fine</b></td>
 *     <td>One lock per level, hand-over-hand. Two versions: as in the thesis (nds2018 r2018.9.1-cd1) and the 2019
 *         revision that retries from a rank hint</td>
 *     <td>{@link org.itmo.inds.population.FineLockJfbyPopulation},
 *         {@link org.itmo.inds.population.FineLockJfbyPopulationRankHint}</td>
 *     <td>[T] Section 4.2.3; [GECCO 2018]</td></tr>
 * <tr><td><b>CAS</b></td>
 *     <td>Lock-free levels with compare-and-set and time-stamping; the one-by-one variant is a constructor flag</td>
 *     <td>{@link org.itmo.inds.population.CasJfbyPopulation}</td>
 *     <td>[T] Sections 4.2.1-4.2.2; [GECCO 2018]</td></tr>
 * </table>
 *
 * <p>{@link org.itmo.inds.population.AbstractConcurrentJfbyPopulation} is unused legacy code, kept for now
 * ({@code docs/roadmap.md}). The divide-and-conquer sorters DC, DC1, DC2 used inside the populations are in
 * {@link org.itmo.inds.sorting}.
 *
 * <p>The asynchronous classes (Coarse, Fine, CAS) are ported by moving and class renames only, except three
 * concurrency fixes found by stress tests (Fine lock order, CAS time-stamping, CAS full sortings); see
 * {@code docs/changes-from-cited-code.md}. Single-threaded behaviour is identical to the cited code.
 *
 * <p>References:
 * <ul>
 * <li>[T] I. Yakupov. Efficient Incremental and Asynchronous Non-Dominated Sorting for Evolutionary Multiobjective
 *     Algorithms. PhD thesis, ITMO University, 2019.</li>
 * <li>[GECCO 2017] I. Yakupov, M. Buzdalov. Improved Incremental Non-dominated Sorting for Steady-State Evolutionary
 *     Multiobjective Optimization. GECCO 2017, pp. 649-656.</li>
 * <li>[GECCO 2018] I. Yakupov, M. Buzdalov. On Asynchronous Non-Dominated Sorting for Steady-State Multiobjective
 *     Evolutionary Algorithms. GECCO 2018 Companion, pp. 205-206, arXiv:1804.05208.</li>
 * </ul>
 */
package org.itmo.inds.population;
