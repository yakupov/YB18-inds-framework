/**
 * Divide-and-conquer non-dominated sorting.
 * <ul>
 * <li><b>DC</b> - offline divide-and-conquer sorting (Jensen; Fortin et al.; Buzdalov &amp; Shalyto, PPSN 2014):
 *     {@code JfbSorter#performNds}.</li>
 * <li><b>DC1</b> - incremental insertion restricted to the points dominated by the inserted one, with the
 *     is-anything-changed heuristic (thesis Sections 3.2.2, 3.2.4; GECCO 2017): {@code JfbSorter#performIncrementalNds}.</li>
 * <li><b>DC2</b> - DC1 with linear-time sweeps (thesis Section 3.2.3): {@code IncrementalJfbSorter}.</li>
 * </ul>
 */
package org.itmo.inds.sorting;
