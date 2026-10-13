package org.itmo.inds.population;

import org.itmo.inds.api.FitnessAndCdIndividual;
import org.itmo.inds.api.Individual;
import org.itmo.inds.api.ManagedPopulation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The populations that evict one point per insertion above their expected size keep exactly that size.
 */
class ExpectedSizeTest {
    static Stream<Arguments> populations() {
        return Stream.of(
                Arguments.of("Level", (LongFunction<ManagedPopulation<Integer>>) JfbyPopulation::new),
                Arguments.of("Coarse", (LongFunction<ManagedPopulation<Integer>>) CoarseLockJfbyPopulation::new),
                Arguments.of("CAS", (LongFunction<ManagedPopulation<Integer>>)
                        expected -> new CasJfbyPopulation<>((int) expected, false)),
                Arguments.of("CAS-oneByOne", (LongFunction<ManagedPopulation<Integer>>)
                        expected -> new CasJfbyPopulation<>((int) expected, true)));
    }

    /**
     * A dominance chain inserted worst first: every point pushes all levels down and the worst point is evicted, so
     * the 25 best points remain.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("populations")
    void chainInsertedWorstFirstKeepsTheBestPoints(final String name,
                                                   final LongFunction<ManagedPopulation<Integer>> factory) {
        final ManagedPopulation<Integer> population = factory.apply(25);
        for (int i = 0; i < 50; ++i) {
            final double value = 50 - i;
            population.addIndividual(new FitnessAndCdIndividual<>(new double[]{value, value, value}, i));
        }
        final Set<Integer> present = population.getSnapshot().getLevels().stream()
                .flatMap(level -> level.getMembers().stream()).map(Individual::getPayload).collect(Collectors.toSet());
        assertEquals(IntStream.range(25, 50).boxed().collect(Collectors.toSet()), present);
        assertEquals(25, population.size());
    }
}
