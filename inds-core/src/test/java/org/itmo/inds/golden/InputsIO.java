package org.itmo.inds.golden;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Reads the golden input sets file {@code inputs.txt.gz} (gzipped text):
 * <pre>{@code
 * # inds-golden-inputs v1
 * set <id> <family> <k> <n> <seed> <prefix> <smallExpected> <unlimited 0|1> <nProbes>
 * <k doubles>      (n point lines, then nProbes probe lines)
 * }</pre>
 * Doubles are written exactly, as {@link #hex(double)}.
 */
public final class InputsIO {
    /**
     * First line of the inputs file.
     */
    public static final String HEADER = "# inds-golden-inputs v1";

    private InputsIO() {
    }

    /**
     * @param value a double
     * @return its exact text form: {@code Long.toHexString(Double.doubleToRawLongBits(value))}, as in the golden files
     */
    public static String hex(final double value) {
        return Long.toHexString(Double.doubleToRawLongBits(value));
    }

    /**
     * @param s the text form produced by {@link #hex(double)}
     * @return the double
     */
    public static double unhex(final String s) {
        return Double.longBitsToDouble(Long.parseUnsignedLong(s, 16));
    }

    /**
     * Reads all input sets.
     *
     * @param gz the gzipped file content; closed by this method
     * @return the input sets in file order
     * @throws IOException if the content cannot be read or is malformed
     */
    public static List<InputSet> read(final InputStream gz) throws IOException {
        final List<InputSet> sets = new ArrayList<>();
        try (final BufferedReader in = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(gz), StandardCharsets.UTF_8))) {
            final String header = in.readLine();
            if (!HEADER.equals(header)) {
                throw new IOException("Unexpected header: " + header);
            }
            String line;
            while ((line = in.readLine()) != null) {
                final String[] fields = line.split(" ");
                if (!fields[0].equals("set")) {
                    throw new IOException("Expected 'set', got: " + line);
                }
                final int k = Integer.parseInt(fields[3]);
                final int n = Integer.parseInt(fields[4]);
                final int nProbes = Integer.parseInt(fields[9]);
                final double[][] points = readPoints(in, n, k);
                final double[][] probes = readPoints(in, nProbes, k);
                sets.add(new InputSet(fields[1], InputSet.Family.of(fields[2]), k, Long.parseLong(fields[5]),
                        Integer.parseInt(fields[6]), Long.parseLong(fields[7]), fields[8].equals("1"), points, probes));
            }
        }
        return sets;
    }

    /**
     * @param in reader positioned at the first point line
     * @param n  number of point lines to read
     * @param k  number of objectives per line
     * @return the points
     * @throws IOException if a line has not exactly k values
     */
    private static double[][] readPoints(final BufferedReader in, final int n, final int k) throws IOException {
        final double[][] points = new double[n][];
        for (int i = 0; i < n; ++i) {
            final String[] fields = in.readLine().split(" ");
            if (fields.length != k) {
                throw new IOException("Expected " + k + " values, got " + fields.length);
            }
            points[i] = new double[k];
            for (int j = 0; j < k; ++j) {
                points[i][j] = unhex(fields[j]);
            }
        }
        return points;
    }
}
