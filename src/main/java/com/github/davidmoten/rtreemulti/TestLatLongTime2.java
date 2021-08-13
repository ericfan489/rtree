package com.github.davidmoten.rtreemulti;

import com.github.davidmoten.rtreemulti.Entry;
import com.github.davidmoten.rtreemulti.RTree;
import com.github.davidmoten.rtreemulti.geometry.Point;

import java.util.List;


public class TestLatLongTime2 {

    //private static final Precision precision = Precision.DOUBLE;

    private static final List<Entry<Object, Point>> entries = LatLongTime2.entriesList();
    private static final List<Entry<Object, Point>> entries2 = LatLongTime2.entriesList2();


    private static int size=10000;

    private static int maxChildren=4; //max nr of entries= pointers-1

    //private static final RTree<Object, Point> starTreeM4 = RTree.maxChildren(4).star().<Object, Point>create().add(entries);

    public static void main(String[] args) {

        //System.out.println("MINIMUM BOUNDING RECTANGLES CONSTRUCTED AS BELOW");
        //System.out.println("level count min1 max1 min2 max2 linearsum squaresum");

        RTree<Object, Point> tree = RTree.maxChildren(4).minChildren(2).dimensions(2).star().<Object, Point>create().add(entries);


        System.out.println(tree.asString3(tree, entries2));
      tree.visualize(600, 600)
       .save("target/rtreee-" + size + "-" + maxChildren + " segment403data_50000.png");
    }
}
