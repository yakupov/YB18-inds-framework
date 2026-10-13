package org.itmo.inds.util;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SortedObjectivesTest {
    /**
     * The members share the first objective: it contributes 0 to the crowding distance of the inner member, the other
     * two objectives contribute 1 each.
     */
    @Test
    void objectiveWithoutRangeContributesZero() {
        final List<Individual<Integer>> members = new ArrayList<>(List.of( // create() sorts the list in place
                new FitnessAndCdIndividual<>(new double[]{1, 1, 3}, 0),
                new FitnessAndCdIndividual<>(new double[]{1, 2, 2}, 1),
                new FitnessAndCdIndividual<>(new double[]{1, 3, 1}, 2)));
        final SortedObjectives<Individual<Integer>, Integer> sortedObjectives = SortedObjectives.create(3, members,
                (individual, cd) -> new FitnessAndCdIndividual<>(individual.getObjectives(), cd,
                        individual.getPayload()));
        final List<Double> crowdingDistances = sortedObjectives.getLexSortedPop().stream()
                .map(Individual::getCrowdingDistance).toList();
        assertEquals(List.of(Double.POSITIVE_INFINITY, 2.0, Double.POSITIVE_INFINITY), crowdingDistances);
    }
}
