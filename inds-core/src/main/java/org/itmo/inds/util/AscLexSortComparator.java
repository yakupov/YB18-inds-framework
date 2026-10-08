package org.itmo.inds.util;

import org.itmo.inds.api.Individual;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Comparator;

@ThreadSafe
public class AscLexSortComparator implements Comparator<Individual> {
    private final static AscLexSortComparator instance = new AscLexSortComparator();

    private AscLexSortComparator() {
    }

    public static AscLexSortComparator getInstance() {
        return instance;
    }

    @Override
    public int compare(Individual o1, Individual o2) {
        final double[] o1Obj = o1.getObjectives();
        final double[] o2Obj = o2.getObjectives();
        for (int i = 0; i < o1Obj.length; ++i) {
            if (o1Obj[i] < o2Obj[i])
                return -1;
            else if (o1Obj[i] > o2Obj[i])
                return 1;
        }
        return 0;
    }
}
