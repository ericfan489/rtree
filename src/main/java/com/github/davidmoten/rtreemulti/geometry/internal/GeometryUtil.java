package com.github.davidmoten.rtreemulti.geometry.internal;

import com.github.davidmoten.rtreemulti.geometry.Rectangle;

import java.util.*;
import java.util.ArrayList;

public final class GeometryUtil {

    private GeometryUtil() {
        // prevent instantiation
    }

    public static double max(double a, double b) {
        if (a < b)
            return b;
        else
            return a;
    }
    
    public static double distance(double x[], Rectangle r) {
        return distance(x, r.mins(), r.maxes());
    }

    public static double distance(double[] x, double[] a, double[] b) {
        return distance(x, x, a, b);
    }

    // distance between two rectangles
    public static double distance(double[] x, double[] y, double[] a, double[] b) {
        if (intersects(x, y, a, b)) {
            return 0;
        }

        double sum = 0;
        for (int i = 0; i < x.length; i++) {
            boolean xyMostLeft = x[i] < a[i];
            double mostLeftX1 = xyMostLeft ? x[i] : a[i];
            double mostRightX1 = xyMostLeft ? a[i] : x[i];
            double mostLeftX2 = xyMostLeft ? y[i] : b[i];
            double xDifference = max(0, mostLeftX1 == mostRightX1 ? 0 : mostRightX1 - mostLeftX2);
            sum += xDifference * xDifference;
        }
        return Math.sqrt(sum);
    }

    public static boolean intersects(double[] mins, double[] maxes, double [] minsOther, double[] maxesOther) {
        int temp[] = {mins.length,maxes.length, minsOther.length, maxesOther.length};
        Arrays.sort(temp);
        for (int i = 0;i < temp[0];i ++) {
            if (mins[i] > maxesOther[i] || maxes[i] < minsOther[i]) {
                return false;
            }
        }
        return true;
    }

    
    public static double[] min(double[] a, double[] b) {
        double[] p = new double[a.length];
        for (int i = 0; i < p.length; i++) {
            p[i] = Math.min(a[i], b[i]);
        }
        return p;
    }

    public static double[] max(double[] a, double[] b) {
        double[] p = new double[a.length];
        for (int i = 0; i < p.length; i++) {
            p[i] = Math.max(a[i], b[i]);
        }
        return p;
    }


}
