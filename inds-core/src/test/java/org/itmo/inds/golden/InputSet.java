package org.itmo.inds.golden;

/**
 * One golden input set: points in insertion order plus the parameters the scenarios use with them.
 *
 * @param id            unique id, e.g. {@code U-k3-n100} or {@code R-zdt1-k2-ds1-g5000}
 * @param family        what kind of points the set holds
 * @param k             number of objectives
 * @param seed          generator seed; 0 for sets taken from the data sets ({@link Family#SNAPSHOT})
 * @param prefix        number of points sorted or inserted before the incremental part of a scenario (incremental
 *                      sorters, clone scenario); for {@link Family#SNAPSHOT} the snapshot size, so that only the
 *                      recorded next point is inserted incrementally
 * @param smallExpected expected population size of the eviction runs of {@link Scenario#POPULATION}
 * @param unlimitedRun  whether populations also run with unlimited expected size (false for {@link Family#SNAPSHOT})
 * @param points        the points, in insertion order
 * @param probes        points whose rank {@code determineRank} reports at the end of {@link Scenario#POPULATION}
 */
public record InputSet(String id, Family family, int k, long seed, int prefix, long smallExpected,
                       boolean unlimitedRun, double[][] points, double[][] probes) {
    /**
     * Families of input sets; each has a one-letter code, the first letter of the set ids.
     */
    public enum Family {
        /**
         * Uniform random points in [0, 1)<sup>k</sup>.
         */
        UNIFORM('U'),
        /**
         * Random integer points in [0, 10)<sup>k</sup>: ties in single objectives and duplicates.
         */
        GRID('G'),
        /**
         * Edge cases: empty set, single point, equal points, a non-dominated pair, dominance chains, a simplex.
         */
        EDGE('E'),
        /**
         * Points on two or on ten parallel layers.
         */
        LAYERS('P'),
        /**
         * NSGA-II population snapshots from the benchmark data sets, plus the recorded next point.
         */
        SNAPSHOT('R');

        private final char code;

        Family(final char code) {
            this.code = code;
        }

        /**
         * @param code the one-letter code as stored in the inputs file
         * @return the family with that code
         * @throws IllegalArgumentException for an unknown code
         */
        public static Family of(final String code) {
            for (final Family family : values()) {
                if (code.length() == 1 && code.charAt(0) == family.code) {
                    return family;
                }
            }
            throw new IllegalArgumentException("unknown input set family: " + code);
        }
    }

    /**
     * @return the number of points
     */
    public int n() {
        return points.length;
    }
}
