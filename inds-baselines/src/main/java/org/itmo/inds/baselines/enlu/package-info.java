/**
 * <b>ENLU</b> - Efficient Non-domination Level Update [L], the reference algorithm the thesis [T] compares with,
 * implemented by the author: {@link org.itmo.inds.baselines.enlu.EnluPopulation} with
 * {@link org.itmo.inds.baselines.enlu.EnluNonDominationLevel}, a population (crowding distance, eviction) behind a
 * global lock, used as Coarse-ENLU in [T] Chapter 4. From nds2018 r2019.2, ported verbatim with class renames only
 * ({@code EnluPopulation} is synchronized: move and class renames only).
 *
 * <p>References:
 * <ul>
 * <li>[L] K. Li, K. Deb, Q. Zhang, Q. Zhang. Efficient Nondomination Level Update Method for Steady-State
 *     Evolutionary Multiobjective Optimization. IEEE Transactions on Cybernetics 47(9), 2017, pp. 2838-2849.</li>
 * <li>[T] I. Yakupov. Efficient Incremental and Asynchronous Non-Dominated Sorting for Evolutionary Multiobjective
 *     Algorithms. PhD thesis, ITMO University, 2019.</li>
 * </ul>
 */
package org.itmo.inds.baselines.enlu;
