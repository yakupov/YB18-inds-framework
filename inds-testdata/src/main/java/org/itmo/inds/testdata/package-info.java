/**
 * Benchmark data sets: loader and model. Start with {@link org.itmo.inds.testdata.DataSets} (usage example there):
 * it loads the data sets of module {@code inds-benchmark-data} by path as {@link org.itmo.inds.testdata.FrontsAndAddends},
 * a population split into non-domination fronts plus the points to insert (one per snapshot, or a sequence).
 * <p>
 * The legacy model classes ({@link org.itmo.inds.testdata.FrontStorage}, {@link org.itmo.inds.testdata.RunConfiguration},
 * {@link org.itmo.inds.testdata.DoublesGeneration} / {@link org.itmo.inds.testdata.Generation},
 * {@link org.itmo.inds.testdata.Front}, {@link org.itmo.inds.testdata.DoublesAdditionProblem}) are ported unchanged
 * from {@code nds2016/NDSUtils} ({@code front_storage}). Their field names are the JSON schema of the existing data
 * sets and must not be renamed without {@code @SerializedName}. Their names come from the first use for generational
 * NSGA-II snapshots ({@code snapshots/generational/}); in the steady-state snapshot sets a "generation" is a
 * population snapshot after a number of iterations.
 */
package org.itmo.inds.testdata;
