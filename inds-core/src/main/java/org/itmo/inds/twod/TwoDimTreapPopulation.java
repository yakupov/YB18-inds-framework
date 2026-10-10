package org.itmo.inds.twod;

import java.util.*;

public class TwoDimTreapPopulation {
    private final Set<TwoDimPoint> individuals;
    private final List<TwoDimTreap> ranks;
    private final Random random = new Random();

    public int size() {
        return individuals.size();
    }

    public TwoDimTreapPopulation() {
        this(new HashSet<>(), new ArrayList<>());
    }

    public TwoDimTreapPopulation(Set<TwoDimPoint> individuals, List<TwoDimTreap> ranks) {
        this.individuals = individuals;
        this.ranks = ranks;
    }

    @Deprecated
    protected int determineRankStupid(TwoDimPoint nInd) {
        int currRank = 0;
        for (int i = 0; i < ranks.size(); ++i) {
            if (ranks.get(i).dominatedBySomebody(nInd))
                currRank = i + 1;
            else
                return currRank;
        }
        return currRank;
    }

    protected int determineRank(TwoDimPoint nInd) {
        int currRank = 0;
        int l = 0;
        int r = ranks.size() - 1;
        while (l <= r) {
            int i = (l + r) / 2;
            if (ranks.get(i).dominatedBySomebody(nInd)) {
                currRank = i + 1;
                l = i + 1;
            } else {
                r = i - 1;
            }
        }
        return currRank;
    }

    public void addPoint(TwoDimPoint nInd) {
        if (individuals.contains(nInd)) {
            return;
        } else {
            individuals.add(nInd);
        }
        int rank = determineRank(nInd);
        //System.err.println(rank + "_" + nInd.toString() + "_" + ranks.size());
        TwoDimTreap nTreap = new TwoDimTreap(nInd, random.nextInt(), null, null);

        if (rank >= ranks.size()) {
            ranks.add(nTreap);
        } else if (nInd.compareDom(ranks.get(rank).getMinP()) < 0) {
            ranks.add(rank, nTreap);
            return;
        } else {
            int i = 0;
            TwoDimPoint minP = nInd;
            TwoDimTreap cNext = nTreap;
            while (minP != null) {
                if (ranks.size() <= rank + i) {
                    ranks.add(cNext);
                    break;
                }

                boolean printTreaps = "Y".equals(System.getProperty("printTreaps"));
                printTreap(cNext, printTreaps);
                printTreap(ranks.get(rank + i), printTreaps);

                TwoDimTreap.Treaps t1 = ranks.get(rank + i).splitX(minP);
                TwoDimTreap.Treaps tr = new TwoDimTreap.Treaps();
                if (t1.r != null)
                    tr = t1.r.splitY(minP);

                printTreap(t1.l, printTreaps);
                printTreap(t1.r, printTreaps);
                printTreap(tr.l, printTreaps);
                printTreap(tr.r, printTreaps);

                TwoDimTreap res = TwoDimTreap.merge(t1.l, cNext);
                printTreap(res, printTreaps);
                res = TwoDimTreap.merge(res, tr.r);
                printTreap(res, printTreaps);
                ranks.set(rank + i, res);
                cNext = tr.l;
                printTreap(cNext, printTreaps);

                if (cNext == null)
                    break;
                minP = cNext.getMinP();
                i++;
            }
        }
    }

    private void printTreap(TwoDimTreap cNext, boolean sw) {
        if (sw)
            System.err.println(cNext);
    }

    public static class IndWithRank {
        TwoDimPoint ind;
        int rank;
        public IndWithRank(TwoDimPoint ind, int rank) {
            super();
            this.ind = ind;
            this.rank = rank;
        }

        public String toString() {
            return "Rank = " + rank + ", value = " + String.valueOf(ind);
        }
    }

    public IndWithRank getRandWithRank() {
        if (individuals.size() == 0)
            throw new RuntimeException("Can't get random individual from empty population");
        TwoDimPoint randInd = (TwoDimPoint) individuals.toArray()[random.nextInt(individuals.size())];
        return new IndWithRank(randInd, detRankOfExPoint(randInd));
    }

    protected int detRankOfExPoint(TwoDimPoint ind) {
        int r = ranks.size() - 1;
        int l = 0;
        int result = -1;
        while (l != r) {
            int curr = (l + r)/2;
            if (r == l + 1) {
                curr = l;
                boolean dominated = ranks.get(curr).dominatedBySomebody(ind);
                boolean dominates = ranks.get(curr).dominatesSomebody(ind);
                if (!dominates && !dominated)
                    return result < 0 ? curr : Math.min(curr, result);

                curr = r;
                dominated = ranks.get(curr).dominatedBySomebody(ind);
                dominates = ranks.get(curr).dominatesSomebody(ind);
                if (!dominates && !dominated)
                    return result < 0 ? curr : Math.min(curr, result);
            } else {
                boolean dominated = ranks.get(curr).dominatedBySomebody(ind);
                boolean dominates = ranks.get(curr).dominatesSomebody(ind);
                if (!dominates && !dominated) {
                    result = result < 0 ? curr : Math.min(curr, result);
                    r = curr;
                } else if (dominates)
                    r = curr;
                else
                    l = curr;
            }
        }
        boolean dominated = ranks.get(l).dominatedBySomebody(ind);
        boolean dominates = ranks.get(l).dominatesSomebody(ind);
        if (!dominates && !dominated)
            result = result < 0 ? l : Math.min(l, result);

        if (result > 0)
            return result;
        throw new RuntimeException("Can't determine rank for " + ind.toString());
    }


    protected int detRankOfExPointDumb(TwoDimPoint ind) {
        for (int l = 0; l < ranks.size(); ++l) {
            boolean dominated = ranks.get(l).dominatedBySomebody(ind);
            boolean dominates = ranks.get(l).dominatesSomebody(ind);
            if (!dominates && !dominated)
                return l;
        }
        throw new RuntimeException("Can't determine rank for " + ind.toString());
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ranks.size(); ++i) {
            sb.append(i);
            sb.append('\n');
            sb.append(ranks.get(i).toString());
            sb.append('\n');
        }
        return sb.toString();
    }

    public void validate() {
        for (int i = 0; i < ranks.size(); ++i) {
            checkDom(ranks.get(i), i);
        }
    }

    private void checkDom(TwoDimTreap t, int rk) {
        if (t == null)
            return;
        int cRankCalcd = 0;
        TwoDimPoint rankDeterminator = null;
        for (TwoDimPoint ind : individuals) {
            if (ind != t.x) {
                if (t.x.compareDom(ind) > 0) { //dominated
                    int newPossibleRank = detRankOfExPoint(ind) + 1;
                    if (newPossibleRank > cRankCalcd) {
                        cRankCalcd = newPossibleRank;
                        rankDeterminator = ind;
                    }
                }
            }
        }
        if (cRankCalcd != rk) {
            throw new RuntimeException("Population is sorted incorrectly. Point = " + t.x.toString() +
                    ", rk = " + rk + ", should be = " + cRankCalcd + ", determinator = " + rankDeterminator);
        }
        checkDom(t.left, rk);
        checkDom(t.right, rk);
    }
}