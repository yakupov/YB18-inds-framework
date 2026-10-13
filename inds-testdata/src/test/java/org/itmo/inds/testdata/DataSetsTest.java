package org.itmo.inds.testdata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every data set of {@code inds-benchmark-data} is on the classpath under its expected name and loads with
 * {@link DataSets}, both as the uniform {@link FrontsAndAddends} view and as the legacy model; structural checks only
 * (number of objectives, snapshot ids, sizes; see {@code inds-benchmark-data/README.md}). Content is checked against
 * the golden inputs by {@code GoldenInputsFromDataTest}.
 */
class DataSetsTest {
    /**
     * A data set file as expected in {@code inds-benchmark-data}.
     *
     * @param path        resource path relative to {@link DataSets#ROOT}
     * @param k           number of objectives
     * @param snapshotIds the snapshot ids in the file ({@code [0]} for a sequence)
     */
    record ExpectedDataSet(String path, int k, List<Integer> snapshotIds) {
        @Override
        @Nonnull
        public String toString() {
            return path;
        }
    }

    private static final List<Integer> NSGA_SNAPSHOTS = List.of(0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000,
            9000, 10000);
    private static final List<Integer> GENERATIONS = List.of(0, 10, 20, 30, 40, 50, 60, 70, 80, 90);

    static List<ExpectedDataSet> snapshotSets() {
        final List<ExpectedDataSet> sets = new ArrayList<>();
        for (final String shape : new String[]{"twoLayers", "twoLines", "uniform"}) {
            for (final int k : new int[]{3, 5, 10, 20}) {
                sets.add(new ExpectedDataSet("snapshots/constant/" + shape + "_dim" + k + "_gen10000.json", k,
                        shape.equals("twoLines") ? List.of(0) : List.of(0, 1, 2)));
            }
        }
        for (final int problem : new int[]{1, 2, 3, 4, 7}) {
            for (int dataset = 1; dataset <= 3; ++dataset) {
                sets.add(new ExpectedDataSet("snapshots/dtlz3d/dtlz" + problem + "_dim3_gen10000_iter10000_dataset"
                        + dataset + ".json", 3, NSGA_SNAPSHOTS));
            }
        }
        for (final int problem : new int[]{1, 2}) {
            for (final int k : new int[]{4, 6, 8, 10}) {
                for (int dataset = 0; dataset <= 1; ++dataset) {
                    sets.add(new ExpectedDataSet("snapshots/dtlzNd/dtlz" + problem + "_dim" + k
                            + "_gen10000_iter10000_dataset" + dataset + ".json", k, NSGA_SNAPSHOTS));
                }
            }
        }
        for (final int problem : new int[]{1, 2, 3, 4, 6}) {
            for (int dataset = 1; dataset <= 3; ++dataset) {
                sets.add(new ExpectedDataSet("snapshots/zdt/zdt" + problem + "_gen10000_iter10000_dataset" + dataset
                        + ".json", 2, NSGA_SNAPSHOTS));
            }
        }
        sets.addAll(generationalSets());
        return sets;
    }

    /**
     * @return the snapshots of generational NSGA-II runs (thesis Chapter 3 figures)
     */
    private static List<ExpectedDataSet> generationalSets() {
        final String folder = "snapshots/generational/";
        final List<ExpectedDataSet> sets = new ArrayList<>();
        for (final int problem : new int[]{1, 2, 3, 4, 7}) {
            for (int dataset = 1; dataset <= 3; ++dataset) {
                sets.add(new ExpectedDataSet(folder + "dtlz3d/dtlz" + problem + "_dim3_gen10000_iter100_dataset"
                        + dataset + ".json", 3, GENERATIONS));
            }
        }
        for (final int problem : new int[]{1, 2}) {
            for (final int k : new int[]{4, 6, 8, 10}) {
                for (int dataset = 0; dataset <= 1; ++dataset) {
                    sets.add(new ExpectedDataSet(folder + "dtlzNd/dtlz" + problem + "_dim" + k
                            + "_gen10000_iter100_dataset" + dataset + ".json", k, GENERATIONS));
                }
            }
        }
        for (final int problem : new int[]{1, 2, 3, 4, 6}) {
            for (int dataset = 1; dataset <= 3; ++dataset) {
                sets.add(new ExpectedDataSet(folder + "zdt/zdt" + problem + "_gen10000_iter100_dataset" + dataset
                        + ".json", 2, GENERATIONS));
            }
        }
        sets.add(new ExpectedDataSet(folder + "uniform/uniform_dim3_gen10000_dataset1.json", 3, List.of(0, 1, 2)));
        return sets;
    }

    static List<ExpectedDataSet> sequenceSets() {
        final List<ExpectedDataSet> sets = new ArrayList<>();
        for (final int problem : new int[]{1, 2, 3, 4, 7}) {
            for (int dataset = 1; dataset <= 3; ++dataset) {
                sets.add(new ExpectedDataSet("sequences/dtlz3d/dtlz" + problem
                        + "_dim3_initSize5000_addendsCount1000_dataset" + dataset + ".json", 3, List.of(0)));
            }
        }
        for (final int problem : new int[]{1, 2}) {
            for (final int k : new int[]{4, 6, 8, 10}) {
                for (int dataset = 0; dataset <= 1; ++dataset) {
                    sets.add(new ExpectedDataSet("sequences/dtlznd/dtlz" + problem + "_dim" + k
                            + "_initSize5000_addendsCount1000_dataset" + dataset + ".json", k, List.of(0)));
                }
            }
        }
        for (final int problem : new int[]{1, 2, 3, 4, 6}) {
            for (int dataset = 1; dataset <= 3; ++dataset) {
                sets.add(new ExpectedDataSet("sequences/zdt/zdt" + problem
                        + "_dim2_initSize5000_addendsCount1000_dataset" + dataset + ".json", 2, List.of(0)));
            }
        }
        return sets;
    }

    @Test
    void setCounts() {
        assertEquals(58 + 47, snapshotSets().size());
        assertEquals(46, sequenceSets().size());
    }

    private static void assertPointDimensions(final FrontsAndAddends view, final int k) {
        assertEquals(k, view.k(), view + ": objectives");
        for (final double[][] front : view.fronts()) {
            assertTrue(front.length > 0, view + ": empty front");
            for (final double[] point : front) {
                assertEquals(k, point.length, view + ": objectives of a member");
            }
        }
        for (final double[] addend : view.addends()) {
            assertEquals(k, addend.length, view + ": objectives of an addend");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("snapshotSets")
    void snapshotSetLoads(final ExpectedDataSet set) {
        final NavigableMap<Integer, FrontsAndAddends> snapshots = DataSets.snapshots(set.path());
        assertEquals(set.snapshotIds(), List.copyOf(snapshots.keySet()), "snapshot ids");
        for (final FrontsAndAddends view : snapshots.values()) {
            assertPointDimensions(view, set.k());
            assertEquals(10000, view.populationSize(), view + ": population");
            assertEquals(1, view.addends().size(), view + ": one addend per snapshot");
        }
        // The view is the legacy model reordered by front id, without copies.
        final RunConfiguration run = DataSets.frontStorage(set.path()).getRunConfigurations().iterator().next();
        assertNotNull(run.getTimestamp(), "timestamp");
        final DoublesGeneration last = new ArrayList<>(run.getGenerations()).get(run.getGenerations().size() - 1);
        final FrontsAndAddends lastView = snapshots.lastEntry().getValue();
        assertArrayEquals(last.getNextAddend(), lastView.addends().get(0));
        for (final Front<double[]> front : last.getFronts()) {
            assertArrayEquals(front.getFitnesses().toArray(new double[0][]), lastView.fronts().get(front.getId()),
                    "front " + front.getId());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sequenceSets")
    void sequenceSetLoads(final ExpectedDataSet set) {
        final FrontsAndAddends view = DataSets.sequence(set.path());
        assertPointDimensions(view, set.k());
        assertEquals(0, view.snapshot());
        assertEquals(5000, view.populationSize(), "population (initSize5000)");
        // The files hold one addend more than their names say (generator loop i <= 1000).
        assertEquals(1001, view.addends().size(), "addends (addendsCount1000: 1001 stored)");
        final DoublesAdditionProblem problem = DataSets.additionProblem(set.path());
        assertEquals(problem.getFronts().size(), view.fronts().size());
        assertArrayEquals(problem.getAddends().get(1000), view.addends().get(1000));
    }

    @Test
    void snapshotById() {
        final String path = "snapshots/constant/uniform_dim3_gen10000.json";
        final NavigableMap<Integer, FrontsAndAddends> snapshots = DataSets.snapshots(path);
        final FrontsAndAddends view = snapshots.get(2);
        assertEquals(2, view.snapshot());
        assertEquals(path + "#2", view.toString());
        assertNull(snapshots.get(3));
        final double[][] population = view.population();
        assertEquals(10000, population.length);
        assertSame(view.fronts().get(0)[0], population[0]);
    }

    @Test
    void snapshotByReference() {
        final FrontsAndAddends view =
                DataSets.snapshots("snapshots/zdt/zdt1_gen10000_iter10000_dataset1.json").get(5000);
        final FrontsAndAddends byReference = DataSets.snapshot(view.toString());
        assertEquals(view.toString(), byReference.toString());
        assertArrayEquals(view.population(), byReference.population());
        assertArrayEquals(view.addends().get(0), byReference.addends().get(0));
        assertThrows(IllegalArgumentException.class,
                () -> DataSets.snapshot("snapshots/zdt/zdt1_gen10000_iter10000_dataset1.json"));
        assertThrows(IllegalArgumentException.class,
                () -> DataSets.snapshot("snapshots/zdt/zdt1_gen10000_iter10000_dataset1.json#5"));
    }

    @Test
    void missingSetIsReported() {
        assertThrows(UncheckedIOException.class, () -> DataSets.sequence("sequences/none.json"));
    }

    /**
     * The legacy {@link FrontStorage#deserialize} uses a plain Gson; it must read the stored {@code LocalDateTime}
     * timestamp on JDK 17 without {@code --add-opens java.base/java.time} (Gson before 2.14 could not).
     */
    @Test
    void legacyDeserializeReadsTimestampOnJdk17() throws Exception {
        final String path = DataSets.ROOT + "snapshots/constant/twoLines_dim3_gen10000.json";
        try (final InputStream stream = DataSets.class.getResourceAsStream(path)) {
            assertNotNull(stream);
            final FrontStorage storage = new FrontStorage();
            storage.deserialize(stream);
            assertNotNull(storage.getRunConfigurations().iterator().next().getTimestamp());
        }
    }
}
