package org.itmo.inds.testdata;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Loads the benchmark data sets of module {@code inds-benchmark-data} (a runtime dependency) by resource path relative
 * to {@link #ROOT}; the files and their origin are listed in that module's {@code README.md} and {@code MANIFEST.tsv}.
 * Every data set is a population split into non-domination fronts plus points to insert, returned as
 * {@link FrontsAndAddends}. There are two kinds:
 * <ul>
 * <li><b>snapshots</b> ({@code snapshots/…}), loaded with {@link #snapshots(String)} or {@link #snapshot(String)}:
 *     each file holds several snapshots of one run, each with the one point to insert next. Generational NSGA-II
 *     runs ({@code snapshots/generational/…}, the data of the thesis Ch. 3 figures): 10 snapshots of 10 000 points
 *     (ids 0, 10, …, 90 = generations); steady-state NSGA-II runs: 11 snapshots of 10 000 points (ids 0, 1000, …,
 *     10000 = iterations); synthetic sets ({@code snapshots/constant/…}, {@code snapshots/generational/uniform/…}):
 *     1 or 3 samples (ids 0, 1, 2).</li>
 * <li><b>sequences</b> ({@code sequences/…}), loaded with {@link #sequence(String)}: each file holds one population
 *     of 5 000 points and a sequence of 1 001 points to insert.</li>
 * </ul>
 *
 * <pre>{@code
 * FrontsAndAddends s = DataSets.snapshots("snapshots/dtlzNd/dtlz2_dim6_gen10000_iter10000_dataset1.json").get(5000);
 * double[][] population = s.population();   // 10 000 points after 5000 iterations, front by front
 * double[] next = s.addends().get(0);        // the point inserted next
 *
 * FrontsAndAddends seq = DataSets.sequence("sequences/dtlz3d/dtlz1_dim3_initSize5000_addendsCount1000_dataset1.json");
 * List<double[]> toInsert = seq.addends();   // 1 001 points
 * }</pre>
 *
 * <p>Every call parses the JSON file (up to 44 MB); nothing is cached. The returned arrays are shared with the parsed
 * model: copy them before handing them to code that modifies its input.
 *
 * <p>The legacy model is available too ({@link #frontStorage}, {@link #additionProblem}): {@link FrontStorage} for
 * snapshot files, {@link DoublesAdditionProblem} for sequence files. Its classes are unchanged from the original code.
 */
public final class DataSets {
    /**
     * Classpath root of the data sets.
     */
    public static final String ROOT = "/org/itmo/inds/data/";

    private static final Type RUN_CONFIGURATIONS = new TypeToken<Collection<RunConfiguration>>() {
    }.getType();

    private DataSets() {
    }

    // ---------------------------------------------------------------- uniform view

    /**
     * All snapshots of a snapshot file by snapshot id, e.g. for
     * {@code snapshots/zdt/zdt1_gen10000_iter10000_dataset1.json} ids 0, 1000, …, 10000. Parses the file once.
     */
    public static NavigableMap<Integer, FrontsAndAddends> snapshots(final String path) {
        final TreeMap<Integer, FrontsAndAddends> snapshots = new TreeMap<>();
        for (final RunConfiguration run : frontStorage(path).getRunConfigurations()) {
            for (final DoublesGeneration generation : run.getGenerations()) {
                if (snapshots.put(generation.getId(), view(path, generation)) != null) {
                    throw new IllegalStateException(path + ": snapshot id " + generation.getId() + " occurs twice");
                }
            }
        }
        return Collections.unmodifiableNavigableMap(snapshots);
    }

    /**
     * One snapshot by its reference {@code path#id}, e.g.
     * {@code snapshots/dtlz3d/dtlz1_dim3_gen10000_iter10000_dataset1.json#5000} (the form
     * {@link FrontsAndAddends#toString()} prints). Parses the whole file.
     *
     * @throws IllegalArgumentException if the reference has no snapshot id or the file has no such snapshot
     */
    public static FrontsAndAddends snapshot(final String reference) {
        final int separator = reference.lastIndexOf(FrontsAndAddends.SNAPSHOT_SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("not a snapshot reference (path#id): " + reference);
        }
        final String path = reference.substring(0, separator);
        final int id = Integer.parseInt(reference.substring(separator + FrontsAndAddends.SNAPSHOT_SEPARATOR.length()));
        final FrontsAndAddends snapshot = snapshots(path).get(id);
        if (snapshot == null) {
            throw new IllegalArgumentException(path + ": no snapshot " + id);
        }
        return snapshot;
    }

    /**
     * A sequence file, e.g. {@code sequences/dtlz3d/dtlz1_dim3_initSize5000_addendsCount1000_dataset1.json}.
     */
    public static FrontsAndAddends sequence(final String path) {
        final DoublesAdditionProblem problem = additionProblem(path);
        final List<double[][]> fronts = new ArrayList<>();
        for (final List<double[]> front : problem.getFronts()) {
            fronts.add(front.toArray(new double[0][]));
        }
        return new FrontsAndAddends(path, 0, fronts, problem.getAddends());
    }

    private static FrontsAndAddends view(final String path, final DoublesGeneration generation) {
        final List<Front<double[]>> sorted = new ArrayList<>(generation.getFronts());
        sorted.sort(Comparator.comparingInt(Front::getId));
        final List<double[][]> fronts = new ArrayList<>();
        for (int i = 0; i < sorted.size(); ++i) {
            if (sorted.get(i).getId() != i) {
                throw new IllegalStateException(
                        path + " snapshot " + generation.getId() + ": front ids are not 0.." + (sorted.size() - 1));
            }
            fronts.add(sorted.get(i).getFitnesses().toArray(new double[0][]));
        }
        final List<double[]> addends =
                generation.getNextAddend() == null ? List.of() : List.of(generation.getNextAddend());
        return new FrontsAndAddends(path, generation.getId(), fronts, addends);
    }

    // ---------------------------------------------------------------- legacy model

    /**
     * Gson for the data model: default settings.
     */
    public static Gson gson() {
        return new Gson();
    }

    /**
     * A snapshot file as the legacy {@link FrontStorage}.
     */
    public static FrontStorage frontStorage(final String path) {
        try (final Reader reader = reader(path)) {
            final FrontStorage storage = new FrontStorage();
            storage.setRunConfigurations(gson().fromJson(reader, RUN_CONFIGURATIONS));
            return storage;
        } catch (final IOException e) {
            throw new UncheckedIOException(path, e);
        }
    }

    /**
     * A sequence file as the legacy {@link DoublesAdditionProblem}.
     */
    public static DoublesAdditionProblem additionProblem(final String path) {
        try (final Reader reader = reader(path)) {
            return gson().fromJson(reader, DoublesAdditionProblem.class);
        } catch (final IOException e) {
            throw new UncheckedIOException(path, e);
        }
    }

    private static Reader reader(final String path) throws IOException {
        final InputStream stream = DataSets.class.getResourceAsStream(ROOT + path);
        if (stream == null) {
            throw new IOException("data set not on the classpath: " + ROOT + path + " (module inds-benchmark-data)");
        }
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8), 1 << 16);
    }
}
