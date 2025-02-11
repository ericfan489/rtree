package com.github.davidmoten.rtreemulti;

import java.util.ArrayList;
import java.util.List;

import com.github.davidmoten.rtreemulti.geometry.Geometry;
import com.github.davidmoten.rtreemulti.geometry.HasGeometry;
import com.github.davidmoten.rtreemulti.internal.NodeAndEntries;

public interface Node<T, S extends Geometry> extends HasGeometry {

    List<Node<T, S>> add(Entry<? extends T, ? extends S> entry);

    NodeAndEntries<T, S> delete(Entry<? extends T, ? extends S> entry, boolean all);

    int count();

    double linear_sum=0;

    double sqr_sum=0;

    List<Double> averages = new ArrayList<Double>();

    Context<T, S> context();
    
    boolean isLeaf();

}
