package org.itmo.inds.twod;

public class TwoDimTreap {
    public static class Treaps {
        TwoDimTreap l, r;
        public Treaps() {};
        public Treaps(TwoDimTreap l, TwoDimTreap r) {
            super();
            this.l = l;
            this.r = r;
        }

        public String toString() {
            return "l=" + String.valueOf(l) + ", r=" + String.valueOf(r);
        }
    }

    public TwoDimPoint x;
    public int y;

    public TwoDimTreap left;
    public TwoDimTreap right;

    public TwoDimTreap(TwoDimPoint x, int y, TwoDimTreap left, TwoDimTreap right) {
        this.x = x;
        this.y = y;
        this.left = left;
        this.right = right;
    }

    public static TwoDimTreap merge(TwoDimTreap l, TwoDimTreap r) {
        if (l == null) return r;
        if (r == null) return l;

        if (l.y > r.y) {
            TwoDimTreap newR = merge(l.right, r);
            return new TwoDimTreap(l.x, l.y, l.left, newR);
        } else {
            TwoDimTreap newL = merge(l, r.left);
            return new TwoDimTreap(r.x, r.y, newL, r.right);
        }
    }

    public Treaps splitX(TwoDimPoint x) {
        Treaps res = new Treaps();
        Treaps t = new Treaps();
        if (this.x.compareX1(x) < 0) {
            if (right == null) {
                res.r = null;
            } else {
                t = right.splitX(x);
                res.r = t.r;
            }
            res.l = new TwoDimTreap(this.x, y, left, t.l);
        } else {
            if (left == null) {
                res.l = null;
            } else {
                t = left.splitX(x);
                res.l = t.l;
            }
            res.r = new TwoDimTreap(this.x, y, t.r, right);
        }
        //System.err.println("SPL: " + String.valueOf(this.x) + "_" + String.valueOf(left) + "_" + String.valueOf(right) + " : " + res.toString());
        return res;
    }

    /**
     * same as split X - members of r have greater x1 than members of l
     */
    public Treaps splitY(TwoDimPoint x) {
        Treaps res = new Treaps();
        Treaps t = new Treaps();
        if (this.x.compareX2(x) >= 0) {
            if (right == null) {
                res.r = null;
            } else {
                t = right.splitY(x);
                res.r = t.r;
            }
            res.l = new TwoDimTreap(this.x, y, left, t.l);
        } else {
            if (left == null) {
                res.l = null;
            } else {
                t = left.splitY(x);
                res.l = t.l;
            }
            res.r = new TwoDimTreap(this.x, y, t.r, right);
        }
        return res;
    }

    public boolean dominatesSomebody(TwoDimPoint nInd) {
        Treaps split = splitX(nInd);
        if (split.l != null && nInd.compareDom(split.l.getMax()) < 0)
            return true;
        if (split.r != null && nInd.compareDom(split.r.getMin()) < 0)
            return true;
        return false;
    }

    public boolean dominatedBySomebody(TwoDimPoint nInd) {
        Treaps split = splitX(nInd);
        //System.err.println("DBS: " + split);
        //System.err.println("_DBS: " + this);

        if (split.l != null && nInd.compareDom(split.l.getMax()) > 0)
            return true;
        if (split.r != null && nInd.compareDom(split.r.getMin()) > 0)
            return true;
        return false;
    }

    public TwoDimPoint getMinP() {
        TwoDimTreap l = this;
        while (l.left != null)
            l = l.left;
        TwoDimTreap r = this;
        while (r.right != null)
            r = r.right;
        return new TwoDimPoint(l.x.getX1(), r.x.getX2());
    }

    public TwoDimPoint getMin() {
        TwoDimTreap l = this;
        while (l.left != null)
            l = l.left;
        return l.x;
    }

    public TwoDimPoint getMax() {
        TwoDimTreap r = this;
        while (r.right != null)
            r = r.right;
        return r.x;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

    protected void toString(StringBuilder sb) {
        sb.append("[" + x.toString() + "]; ");
        if (left != null)
            left.toString(sb);
        if (right != null)
            right.toString(sb);
    }
}
