package org.itmo.inds.golden;

/**
 * What a golden block records about an input set (package documentation, "Scenarios"). Each scenario has the id under
 * which it appears in the golden records.
 */
enum Scenario {
    /**
     * Sort all points at once with DC: result order and ranks.
     */
    DC_SORT("dc-sort"),
    /**
     * Sort the prefix with DC, insert the remaining points one by one with DC1.
     */
    DC1_INSERT("dc1-insert"),
    /**
     * As {@link #DC1_INSERT}, with DC2.
     */
    DC2_INSERT("dc2-insert"),
    /**
     * Add a whole level of addends to a level ({@code addRankedMembers}).
     */
    ADD_RANKED_MEMBERS("add-ranked-members"),
    /**
     * Insert all points one by one into a population.
     */
    POPULATION("population"),
    /**
     * As {@link #POPULATION}, but clone the population after the prefix and insert the rest into the clone.
     */
    POPULATION_CLONE("population-clone"),
    /**
     * Insert all points of a two-objective set one by one into the 2D treap population.
     */
    TREAP_2D("treap-2d");

    private final String id;

    Scenario(final String id) {
        this.id = id;
    }

    /**
     * @return the scenario id in the golden records, e.g. {@code dc-sort}
     */
    String id() {
        return id;
    }
}
