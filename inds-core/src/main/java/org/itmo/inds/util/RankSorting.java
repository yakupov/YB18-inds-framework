package org.itmo.inds.util;

import java.util.Arrays;
import java.util.function.Function;

public class RankSorting<T> {
    private final int rank;
    private final T fitness;

    private RankSorting(int rank, T fitness) {
        this.rank = rank;
        this.fitness = fitness;
    }

    private int getRank() {
        return rank;
    }

    private T getFitness() {
        return fitness;
    }

    public static int[] sortRanksForLexSortedPopulation(int[] ranks, double[][] pop) {
        @SuppressWarnings("unchecked") final RankSorting<double[]>[] ri = new RankSorting[ranks.length];
        for (int i = 0; i < ranks.length; ++i) {
            ri[i] = new RankSorting<>(ranks[i], pop[i]);
        }

        Arrays.sort(ri, (o1, o2) -> {
            for (int i = 0; i < o1.getFitness().length; ++i) {
                if (o1.getFitness()[i] < o2.getFitness()[i])
                    return -1;
                else if (o1.getFitness()[i] > o2.getFitness()[i])
                    return 1;
            }
            return 0;
        });

        return Arrays.stream(ri).mapToInt(RankSorting::getRank).toArray();
    }

    public static <T> int[] sortRanksForLexSortedPopulation(int[] ranks, T[] pop, Function<T, double[]> objectivesExtractor) {
        @SuppressWarnings("unchecked") final RankSorting<T>[] ri = new RankSorting[ranks.length];
        for (int i = 0; i < ranks.length; ++i) {
            ri[i] = new RankSorting<>(ranks[i], pop[i]);
        }

        Arrays.sort(ri, (o1, o2) -> {
            final double[] o1Obj = objectivesExtractor.apply(o1.getFitness());
            final double[] o2Obj = objectivesExtractor.apply(o2.getFitness());
            for (int i = 0; i < o1Obj.length; ++i) {
                if (o1Obj[i] < o2Obj[i])
                    return -1;
                else if (o1Obj[i] > o2Obj[i])
                    return 1;
            }
            return 0;
        });

        return Arrays.stream(ri).mapToInt(RankSorting::getRank).toArray();
    }
}
