package org.itmo.inds.twod;

import java.util.Map;

public class TwoDimTreap<T> {
    public static class Treaps<T> {
        TwoDimTreap<T> l, r;
        public Treaps() {};
        public Treaps(TwoDimTreap<T> l, TwoDimTreap<T> r) {
            super();
            this.l = l;
            this.r = r;
        }

        public String toString() {
            return "l=" + String.valueOf(l) + ", r=" + String.valueOf(r);
        }
    }

    public TwoDimBucket<T> x;
    public int y;

    public TwoDimTreap<T> left;
    public TwoDimTreap<T> right;

    public TwoDimTreap(TwoDimBucket<T> x, int y, TwoDimTreap<T> left, TwoDimTreap<T> right) {
        this.x = x;
        this.y = y;
        this.left = left;
        this.right = right;
    }

    public static <T> TwoDimTreap<T> merge(TwoDimTreap<T> l, TwoDimTreap<T> r) {
        if (l == null) return r;
        if (r == null) return l;

        if (l.y > r.y) {
            TwoDimTreap<T> newR = merge(l.right, r);
            return new TwoDimTreap<>(l.x, l.y, l.left, newR);
        } else {
            TwoDimTreap<T> newL = merge(l, r.left);
            return new TwoDimTreap<>(r.x, r.y, newL, r.right);
        }
    }

    public Treaps<T> splitX(TwoDimBucket<T> x) {
        Treaps<T> res = new Treaps<>();
        Treaps<T> t = new Treaps<>();
        if (this.x.compareX1(x) < 0) {
            if (right == null) {
                res.r = null;
            } else {
                t = right.splitX(x);
                res.r = t.r;
            }
            res.l = new TwoDimTreap<>(this.x, y, left, t.l);
        } else {
            if (left == null) {
                res.l = null;
            } else {
                t = left.splitX(x);
                res.l = t.l;
            }
            res.r = new TwoDimTreap<>(this.x, y, t.r, right);
        }
        //System.err.println("SPL: " + String.valueOf(this.x) + "_" + String.valueOf(left) + "_" + String.valueOf(right) + " : " + res.toString());
        return res;
    }

    /**
     * same as split X - members of r have greater x1 than members of l
     */
    public Treaps<T> splitY(TwoDimBucket<T> x) {
        Treaps<T> res = new Treaps<>();
        Treaps<T> t = new Treaps<>();
        if (this.x.compareX2(x) >= 0) {
            if (right == null) {
                res.r = null;
            } else {
                t = right.splitY(x);
                res.r = t.r;
            }
            res.l = new TwoDimTreap<>(this.x, y, left, t.l);
        } else {
            if (left == null) {
                res.l = null;
            } else {
                t = left.splitY(x);
                res.l = t.l;
            }
            res.r = new TwoDimTreap<>(this.x, y, t.r, right);
        }
        return res;
    }

    public boolean dominatedBySomebody(TwoDimBucket<T> nInd) {
        Treaps<T> split = splitX(nInd);
        //System.err.println("DBS: " + split);
        //System.err.println("_DBS: " + this);

        if (split.l != null && nInd.compareDom(split.l.getMax()) > 0)
            return true;
        if (split.r != null && nInd.compareDom(split.r.getMin()) > 0)
            return true;
        return false;
    }

    /**
     * @return a search key (no members) from the smallest {@code x1} and the smallest {@code x2} of this level: it
     * dominates or equals every member
     */
    public TwoDimBucket<T> getMinP() {
        TwoDimTreap<T> l = this;
        while (l.left != null)
            l = l.left;
        TwoDimTreap<T> r = this;
        while (r.right != null)
            r = r.right;
        return new TwoDimBucket<>(l.x.getX1(), r.x.getX2());
    }

    public TwoDimBucket<T> getMin() {
        TwoDimTreap<T> l = this;
        while (l.left != null)
            l = l.left;
        return l.x;
    }

    public TwoDimBucket<T> getMax() {
        TwoDimTreap<T> r = this;
        while (r.right != null)
            r = r.right;
        return r.x;
    }

    /**
     * @return the bucket with the largest {@code x1} smaller than {@code key}'s, or {@code null}
     */
    public TwoDimBucket<T> predecessor(TwoDimBucket<?> key) {
        TwoDimBucket<T> result = null;
        TwoDimTreap<T> node = this;
        while (node != null) {
            if (node.x.compareX1(key) < 0) {
                result = node.x;
                node = node.right;
            } else {
                node = node.left;
            }
        }
        return result;
    }

    /**
     * @return the bucket with the smallest {@code x1} larger than {@code key}'s, or {@code null}
     */
    public TwoDimBucket<T> successor(TwoDimBucket<?> key) {
        TwoDimBucket<T> result = null;
        TwoDimTreap<T> node = this;
        while (node != null) {
            if (node.x.compareX1(key) > 0) {
                result = node.x;
                node = node.left;
            } else {
                node = node.right;
            }
        }
        return result;
    }

    /**
     * @return this treap without the node of {@code bucket}, sharing the other nodes; {@code null} if none are left
     */
    TwoDimTreap<T> remove(TwoDimBucket<T> bucket) {
        final int byX1 = x.compareX1(bucket);
        if (byX1 == 0) {
            return merge(left, right);
        }
        return byX1 < 0
                ? new TwoDimTreap<>(x, y, left, right.remove(bucket))
                : new TwoDimTreap<>(x, y, left.remove(bucket), right);
    }

    /**
     * @return a treap of the same shape and priorities whose nodes hold the copies of the buckets
     */
    TwoDimTreap<T> copy(Map<TwoDimBucket<T>, TwoDimBucket<T>> bucketCopies) {
        return new TwoDimTreap<>(bucketCopies.get(x), y,
                left == null ? null : left.copy(bucketCopies),
                right == null ? null : right.copy(bucketCopies));
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
