package org.itmo.inds.golden;

/**
 * A legacy build the golden records were recorded with; each has one golden records file. Every {@link Target} is
 * compared with the build its ported class comes from.
 */
enum Source {
    /**
     * nds2018 {@code r2019.2} ({@code dfe4374}): the sorters, Level, Coarse, the 2019 Fine and the ENLU population.
     */
    NDS2018_R2019_2("nds2018-r2019.2"),
    /**
     * nds2018 {@code r2018.9.1-cd1} ({@code bb69f72}), the version cited in the thesis, Chapter 4: CAS and the thesis
     * Fine.
     */
    NDS2018_R2018_9_1_CD1("nds2018-r2018.9.1-cd1"),
    /**
     * nds2016 {@code master} ({@code 06430e7}), cited in the thesis, Chapter 3: the 2D treap.
     */
    NDS2016("nds2016");

    private final String buildName;

    Source(final String buildName) {
        this.buildName = buildName;
    }

    /**
     * @return the build's name as used in file names and test names, e.g. {@code nds2018-r2019.2}
     */
    String buildName() {
        return buildName;
    }

    /**
     * @return the name of the golden records file, a test resource of this package, e.g. {@code nds2018-r2019.2.txt.gz}
     */
    String recordsFile() {
        return buildName + ".txt.gz";
    }
}
