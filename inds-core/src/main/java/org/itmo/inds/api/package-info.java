/**
 * Population API shared by all populations of this library (k objectives, minimized):
 * <ul>
 * <li>{@link org.itmo.inds.api.Individual}: objectives, crowding distance and a user payload;
 *     {@link org.itmo.inds.api.FitnessAndCdIndividual} is the standard implementation.</li>
 * <li>{@link org.itmo.inds.api.ManagedPopulation}: a steady-state population. {@code addIndividual} inserts a point
 *     and returns its rank (0 = non-dominated); once the expected size is exceeded the population evicts the point
 *     with the worst crowding distance from the last level (Fine: in batches above 1.2 times the expected size).
 *     {@code getSnapshot} returns a consistent
 *     {@link org.itmo.inds.api.PopulationSnapshot} (levels in rank order, members with crowding distances) and does
 *     not modify the population; {@code determineRank}, {@code getRandomSolutions} and {@code toRankedPopulation}
 *     are queries.</li>
 * <li>{@link org.itmo.inds.api.NonDominationLevel}: one level (rank) of a population, members in lexicographic order
 *     with their crowding distances.</li>
 * <li>{@link org.itmo.inds.api.RankedIndividual}: an individual with its rank, as returned by
 *     {@code getRandomSolutions}.</li>
 * </ul>
 * Implementations: {@link org.itmo.inds.population} (and the ENLU population of module {@code inds-baselines}).
 * Ported from nds2018 ({@code ru.ifmo.nds}) with the interfaces' {@code I} prefixes dropped.
 */
package org.itmo.inds.api;
