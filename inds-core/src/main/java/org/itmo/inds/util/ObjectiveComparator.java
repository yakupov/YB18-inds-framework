package org.itmo.inds.util;

import org.itmo.inds.api.Individual;

import java.util.Comparator;

public class ObjectiveComparator implements Comparator<Individual> {
	private final int objective;

	/**
	 * @param objective the objective to be compared, starting from zero
	 */
	public ObjectiveComparator(int objective) {
		this.objective = objective;
	}

	@Override
	public int compare(Individual i1, Individual i2) {
		return Double.compare(i1.getObjectives()[objective], i2.getObjectives()[objective]);
	}
}
