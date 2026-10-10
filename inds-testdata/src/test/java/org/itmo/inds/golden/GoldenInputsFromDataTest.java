package org.itmo.inds.golden;

import org.itmo.inds.testdata.DataSets;
import org.itmo.inds.testdata.FrontsAndAddends;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Cross-checks the data loader and the data against the golden inputs: the nine golden input sets of family R
 * (NSGA-II snapshots; families: inds-core golden package documentation) were taken from the snapshot data sets by the
 * legacy recorder with the legacy loader. This test rebuilds them from {@code inds-benchmark-data} with
 * {@link DataSets#snapshots(String)} the same way (fronts by id, members in stored order, then the snapshot's next
 * point) and requires the points to be bit-identical. Skipped without the golden files.
 */
class GoldenInputsFromDataTest {
    /**
     * Snapshot data set and the name it has in the golden input set ids ({@code R-<name>-g<snapshot>}).
     */
    private static final String[][] R_SETS = {
            {"snapshots/dtlz3d/dtlz1_dim3_gen10000_iter10000_dataset1.json", "dtlz1-k3-ds1"},
            {"snapshots/dtlzNd/dtlz2_dim6_gen10000_iter10000_dataset1.json", "dtlz2-k6-ds1"},
            {"snapshots/zdt/zdt1_gen10000_iter10000_dataset1.json", "zdt1-k2-ds1"},
    };
    /**
     * The snapshot ids (iterations) the golden input sets were taken at.
     */
    private static final List<Integer> R_SNAPSHOTS = List.of(0, 5000, 10000);

    @Test
    void realSetsEqualGoldenInputs() throws Exception {
        Assumptions.assumeTrue(GoldenCheck.available(GoldenCheck.INPUTS), "golden files not found: test skipped");
        final Map<String, InputSet> golden = GoldenCheck.inputs().stream()
                .collect(Collectors.toMap(InputSet::id, Function.identity()));
        int checked = 0;
        for (final String[] r : R_SETS) {
            final Map<Integer, FrontsAndAddends> snapshots = DataSets.snapshots(r[0]);
            for (final int id : R_SNAPSHOTS) {
                final String setId = "R-" + r[1] + "-g" + id;
                final FrontsAndAddends v = snapshots.get(id);
                assertNotNull(v, setId);
                final InputSet expected = golden.get(setId);
                assertNotNull(expected, "golden set " + setId);
                final double[][] population = v.population();
                assertEquals(expected.prefix(), population.length, setId + ": population size");
                assertEquals(1, v.addends().size(), setId + ": addends");
                assertEquals(expected.n(), population.length + 1, setId + ": points");
                for (int i = 0; i < population.length; ++i) {
                    assertArrayEquals(expected.points()[i], population[i], setId + ": point " + i);
                }
                assertArrayEquals(expected.points()[population.length], v.addends().get(0), setId + ": addend");
                ++checked;
            }
        }
        assertEquals(9, checked);
    }
}
