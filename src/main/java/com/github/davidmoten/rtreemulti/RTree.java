package com.github.davidmoten.rtreemulti;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.github.davidmoten.guavamini.Lists;
import com.github.davidmoten.guavamini.Preconditions;
import com.github.davidmoten.guavamini.annotations.VisibleForTesting;
import com.github.davidmoten.rtreemulti.geometry.Geometry;
import com.github.davidmoten.rtreemulti.geometry.HasGeometry;
import com.github.davidmoten.rtreemulti.geometry.Point;
import com.github.davidmoten.rtreemulti.geometry.Rectangle;
import com.github.davidmoten.rtreemulti.internal.Comparators;
import com.github.davidmoten.rtreemulti.internal.NodeAndEntries;
import com.github.davidmoten.rtreemulti.internal.util.BoundedPriorityQueue;

/**
 * Immutable in-memory 2D R-Tree with configurable splitter heuristic.
 * 
 * @param <T>
 *            the entry value type
 * @param <S>
 *            the entry geometry type
 */
public final class RTree<T, S extends Geometry> {

    private final Optional<? extends Node<T, S>> root;
    private final Context<T, S> context;

    /**
     * Benchmarks show that this is a good choice for up to O(10,000) entries when
     * using Quadratic splitter (Guttman).
     */
    public static final int MAX_CHILDREN_DEFAULT_GUTTMAN = 4;

    /**
     * Benchmarks show that this is the sweet spot for up to O(10,000) entries when
     * using R*-tree heuristics.
     */
    public static final int MAX_CHILDREN_DEFAULT_STAR = 4;

    /**
     * Current size in Entries of the RTree.
     */
    private final int size;

    /**
     * Constructor.
     * 
     * @param root
     *            the root node of the tree if present
     * @param context
     *            options for the R-tree
     */
    public RTree(Optional<? extends Node<T, S>> root, int size, Context<T, S> context) {
        this.root = root;
        this.size = size;
        this.context = context;
    }

    /**
     * Constructor.
     * 
     * @param root
     *            the root node of the R-tree
     * @param context
     *            options for the R-tree
     */
    private RTree(Node<T, S> root, int size, Context<T, S> context) {
        this(of(root), size, context);
    }

    /**
     * Returns a new Builder instance for a 2 dimensional {@link RTree}. Defaults to
     * maxChildren=128, minChildren=64, splitter=QuadraticSplitter.
     * 
     * @param <T>
     *            the value type of the entries in the tree
     * @param <S>
     *            the geometry type of the entries in the tree
     * @return a new RTree instance
     */
    public static <T, S extends Geometry> RTree<T, S> create() {
        return new Builder().create();
    }
    
    /**
     * Returns a new Builder instance for {@link RTree}. Defaults to
     * maxChildren=128, minChildren=64, splitter=QuadraticSplitter.
     * 
     * @param dimensions 
     *            the number of dimensions
     * @param <T>
     *            the value type of the entries in the tree
     * @param <S>
     *            the geometry type of the entries in the tree
     * @return a new RTree instance
     */
    public static <T, S extends Geometry> RTree<T, S> create(int dimensions) {
        return new Builder().dimensions(dimensions).create();
    }

    public static Builder dimensions(int dimensions) {
        return new Builder().dimensions(dimensions);
    }

    /**
     * The tree is scanned for depth and the depth returned. This involves recursing
     * down to the leaf level of the tree to get the current depth. Should be
     * <code>log(n)</code> in complexity.
     * 
     * @return depth of the R-tree
     */
    public int calculateDepth() {
        return calculateDepth(root);
    }

    private static <T, S extends Geometry> int calculateDepth(Optional<? extends Node<T, S>> root) {
        if (!root.isPresent())
            return 0;
        else
            return calculateDepth(root.get(), 0);
    }

    private static <T, S extends Geometry> int calculateDepth(Node<T, S> node, int depth) {
        if (node.isLeaf())
            return depth + 1;
        else
            return calculateDepth(((NonLeaf<T, S>) node).child(0), depth + 1);
    }

    // E IMJA
    public int calculateDepth2(Node<T, S> node, int depth) {
        if (node.isLeaf())
            return depth + 1;
        else
            return calculateDepth2(((NonLeaf<T, S>) node).child(0), depth + 1);
    }
    /**
     * When the number of children in an R-tree node drops below this number the
     * node is deleted and the children are added on to the R-tree again.
     * 
     * @param minChildren
     *            less than this number of children in a node triggers a node
     *            deletion and redistribution of its members
     * @return builder
     */
    public static Builder minChildren(int minChildren) {
        return new Builder().minChildren(minChildren);
    }

    /**
     * Sets the max number of children in an R-tree node.
     * 
     * @param maxChildren
     *            max number of children in an R-tree node
     * @return builder
     */
    public static Builder maxChildren(int maxChildren) { return new Builder().maxChildren(maxChildren); }

    /**
     * Sets the {@link Splitter} to use when maxChildren is reached.
     * 
     * @param splitter
     *            the splitter algorithm to use
     * @return builder
     */
    public static Builder splitter(Splitter splitter) {
        return new Builder().splitter(splitter);
    }

    /**
     * Sets the node {@link Selector} which decides which branches to follow when
     * inserting or searching.
     * 
     * @param selector
     *            determines which branches to follow when inserting or searching
     * @return builder
     */
    public static Builder selector(Selector selector) {
        return new Builder().selector(selector);
    }

    /**
     * Sets the splitter to {@link SplitterRStar} and selector to
     * {@link SelectorRStar} and defaults to minChildren=10.
     * 
     * @return builder
     */
    public static Builder star() {
        return new Builder().star();
    }

    /**
     * RTree Builder.
     */
    public static class Builder {

        /**
         * According to http://dbs.mathematik.uni-marburg.de/publications/myPapers
         * /1990/BKSS90.pdf (R*-tree paper), best filling ratio is 0.4 for both
         * quadratic split and R*-tree split.
         */
        private static final double DEFAULT_FILLING_FACTOR = 0.4;
        private static final double DEFAULT_LOADING_FACTOR = 0.7;
        private Optional<Integer> maxChildren = empty();
        private Optional<Integer> minChildren = empty();
        private Splitter splitter = SplitterQuadratic.INSTANCE;
        private Selector selector = SelectorMinimalVolumeIncrease.INSTANCE;
        private double loadingFactor = DEFAULT_LOADING_FACTOR;
        private boolean star = false;
        private Factory<Object, Geometry> factory = Factory.defaultFactory();
        private int dimensions = 2;

        private Builder() {
        }
        
        public Builder dimensions(int dimensions) {
            Preconditions.checkArgument(dimensions >= 2, "dimensions must be 2 or more");
            this.dimensions = dimensions;
            return this;
        }

        /**
         * The factor used as the fill ratio during bulk loading. Default is 0.7.
         * 
         * @param factor
         *            loading factor
         * @return this
         */
        public Builder loadingFactor(double factor) {
            this.loadingFactor = factor;
            return this;
        }

        /**
         * When the number of children in an R-tree node drops below this number the
         * node is deleted and the children are added on to the R-tree again.
         * 
         * @param minChildren
         *            less than this number of children in a node triggers a
         *            redistribution of its children.
         * @return builder
         */
        public Builder minChildren(int minChildren) {
            this.minChildren = of(minChildren);
            return this;
        }

        /**
         * Sets the max number of children in an R-tree node.
         * 
         * @param maxChildren
         *            max number of children in R-tree node.
         * @return builder
         */
        public Builder maxChildren(int maxChildren) {
            this.maxChildren = of(maxChildren);
            return this;
        }

        /**
         * Sets the {@link Splitter} to use when maxChildren is reached.
         * 
         * @param splitter
         *            node splitting method to use
         * @return builder
         */
        public Builder splitter(Splitter splitter) {
            this.splitter = splitter;
            return this;
        }

        /**
         * Sets the node {@link Selector} which decides which branches to follow when
         * inserting or searching.
         * 
         * @param selector
         *            selects the branch to follow when inserting or searching
         * @return builder
         */
        public Builder selector(Selector selector) {
            this.selector = selector;
            return this;
        }

        /**
         * Sets the splitter to {@link SplitterRStar} and selector to
         * {@link SelectorRStar} and defaults to minChildren=10.
         * 
         * @return builder
         */
        public Builder star() {
            selector = SelectorRStar.INSTANCE;
            splitter = SplitterRStar.INSTANCE;
            star = true;
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder factory(Factory<?, ? extends Geometry> factory) {
            // TODO could change the signature of Builder to have types to
            // support this method but would be breaking change for existing
            // clients
            this.factory = (Factory<Object, Geometry>) factory;
            return this;
        }

        /**
         * Builds the {@link RTree}.
         * 
         * @param <T>
         *            value type
         * @param <S>
         *            geometry type
         * @return RTree
         */
        @SuppressWarnings("unchecked")
        public <T, S extends Geometry> RTree<T, S> create() {
            setDefaultCapacity();

            return new RTree<T, S>(Optional.<Node<T, S>>empty(), 0, new Context<T, S>(dimensions, minChildren.get(),
                    maxChildren.get(), selector, splitter, (Factory<T, S>) factory));
        }

        /**
         * Create an RTree by bulk loading, using the STR method. STR: a simple and
         * efficient algorithm for R-tree packing
         * http://ieeexplore.ieee.org/abstract/document/582015/
         * <p>
         * Note: this method mutates the input entries, the internal order of the List
         * may be changed.
         * </p>
         * 
         * @param entries
         *            entries to be added to the r-tree
         * @return a loaded RTree
         */
        @SuppressWarnings("unchecked")
        public <T, S extends Geometry> RTree<T, S> create(List<Entry<T, S>> entries) {
            setDefaultCapacity();

            Context<T, S> context = new Context<T, S>(dimensions, minChildren.get(), maxChildren.get(), selector, splitter,
                    (Factory<T, S>) factory);
            return packingSTR(entries, true, entries.size(), context);
        }

        private void setDefaultCapacity() {
            if (!maxChildren.isPresent())
                if (star)
                    maxChildren = of(MAX_CHILDREN_DEFAULT_STAR);
                else
                    maxChildren = of(MAX_CHILDREN_DEFAULT_GUTTMAN);
            if (!minChildren.isPresent())
                minChildren = of((int) Math.round(maxChildren.get() * DEFAULT_FILLING_FACTOR));
        }

        @SuppressWarnings("unchecked")
        private <T, S extends Geometry> RTree<T, S> packingSTR(List<? extends HasGeometry> objects, boolean isLeaf,
                int size, Context<T, S> context) {
            int capacity = (int) Math.round(maxChildren.get() * loadingFactor);
            int nodeCount = (int) Math.ceil(1.0 * objects.size() / capacity);

            if (nodeCount == 0) {
                return create();
            } else if (nodeCount == 1) {
                Node<T, S> root;
                if (isLeaf) {
                    root = context.factory().createLeaf((List<Entry<T, S>>) objects, context);
                } else {
                    root = context.factory().createNonLeaf((List<Node<T, S>>) objects, context);
                }
                return new RTree<T, S>(of(root), size, context);
            }

            int nodePerSlice = (int) Math.ceil(Math.sqrt(nodeCount));
            int sliceCapacity = nodePerSlice * capacity;
            int sliceCount = (int) Math.ceil(1.0 * objects.size() / sliceCapacity);
            Collections.sort(objects, new MidComparator((short) 0));

            List<Node<T, S>> nodes = new ArrayList<Node<T, S>>(nodeCount);
            for (int s = 0; s < sliceCount; s++) {
                @SuppressWarnings("rawtypes")
                List slice = objects.subList(s * sliceCapacity, Math.min((s + 1) * sliceCapacity, objects.size()));
                Collections.sort(slice, new MidComparator((short) 1));

                for (int i = 0; i < slice.size(); i += capacity) {
                    if (isLeaf) {
                        List<Entry<T, S>> entries = slice.subList(i, Math.min(slice.size(), i + capacity));
                        Node<T, S> leaf = context.factory().createLeaf(entries, context);
                        nodes.add(leaf);
                    } else {
                        List<Node<T, S>> children = slice.subList(i, Math.min(slice.size(), i + capacity));
                        Node<T, S> nonleaf = context.factory().createNonLeaf(children, context);
                        nodes.add(nonleaf);
                    }
                }
            }
            return packingSTR(nodes, false, size, context);
        }

        private static final class MidComparator implements Comparator<HasGeometry> {
            private final int dimension; // leave space for multiple dimensions, 0 for x, 1 for y,
                                           // ...

            public MidComparator(int dimension) {
                this.dimension = dimension;
            }

            @Override
            public int compare(HasGeometry o1, HasGeometry o2) {
                return Double.compare(mid(o1), mid(o2));
            }

            private double mid(HasGeometry o) {
                Rectangle mbr = o.geometry().mbr();
                return (mbr.min(dimension) + mbr.max(dimension)) / 2;
            }
        }

    }

    /**
     * Returns an immutable copy of the RTree with the addition of given entry.
     * 
     * @param entry
     *            item to add to the R-tree.
     * @return a new immutable R-tree including the new entry
     */
    @SuppressWarnings("unchecked")
    public RTree<T, S> add(Entry<? extends T, ? extends S> entry) {
        Preconditions.checkArgument(dimensions() == entry.geometry().dimensions(),
                entry + " has wrong number of dimensions, expected " + dimensions());
        if (root.isPresent()) {
            List<Node<T, S>> nodes = root.get().add(entry);
            Node<T, S> node;
            if (nodes.size() == 1) {
                node = nodes.get(0);
            } else {
                node = context.factory().createNonLeaf(nodes, context);
            }
            return new RTree<T, S>(node, size + 1, context);
        } else {
            Leaf<T, S> node = context.factory().createLeaf(Lists.newArrayList((Entry<T, S>) entry), context);
            return new RTree<T, S>(node, size + 1, context);
        }
    }

    /**
     * Returns an immutable copy of the RTree with the addition of an entry
     * comprised of the given value and Geometry.
     * 
     * @param value
     *            the value of the {@link Entry} to be added
     * @param geometry
     *            the geometry of the {@link Entry} to be added
     * @return a new immutable R-tree including the new entry
     */
    public RTree<T, S> add(T value, S geometry) {
        return add(context.factory().createEntry(value, geometry));
    }

    /**
     * Returns an immutable RTree with the current entries and the additional
     * entries supplied as a parameter.
     * 
     * @param entries
     *            entries to add
     * @return R-tree with entries added
     */
    public RTree<T, S> add(Iterable<Entry<T, S>> entries) {
        RTree<T, S> tree = this;
        for (Entry<T, S> entry : entries) {
            tree = tree.add(entry);
        }
        return tree;
    }

    /**
     * Returns a new R-tree with the given entries deleted. If <code>all</code> is
     * false deletes only one if exists. If <code>all</code> is true deletes all
     * matching entries.
     * 
     * @param entries
     *            entries to delete
     * @param all
     *            if false deletes one if exists else deletes all
     * @return R-tree with entries deleted
     */
    public RTree<T, S> delete(Iterable<Entry<T, S>> entries, boolean all) {
        RTree<T, S> tree = this;
        for (Entry<T, S> entry : entries)
            tree = tree.delete(entry, all);
        return tree;
    }

    /**
     * Returns a new R-tree with the given entries deleted but only one matching
     * occurence of each entry is deleted.
     * 
     * @param entries
     *            entries to delete
     * @return R-tree with entries deleted up to one matching occurence per entry
     */
    public RTree<T, S> delete(Iterable<Entry<T, S>> entries) {
        RTree<T, S> tree = this;
        for (Entry<T, S> entry : entries)
            tree = tree.delete(entry);
        return tree;
    }

    /**
     * If <code>all</code> is false deletes one entry matching the given value and
     * Geometry. If <code>all</code> is true deletes all entries matching the given
     * value and geometry. This method has no effect if the entry is not present.
     * The entry must match on both value and geometry to be deleted.
     * 
     * @param value
     *            the value of the {@link Entry} to be deleted
     * @param geometry
     *            the geometry of the {@link Entry} to be deleted
     * @param all
     *            if false deletes one if exists else deletes all
     * @return a new immutable R-tree without one or many instances of the specified
     *         entry if it exists otherwise returns the original RTree object
     */
    public RTree<T, S> delete(T value, S geometry, boolean all) {
        return delete(context.factory().createEntry(value, geometry), all);
    }

    /**
     * Deletes maximum one entry matching the given value and geometry. This method
     * has no effect if the entry is not present. The entry must match on both value
     * and geometry to be deleted.
     * 
     * @param value
     *            the value to be matched for deletion
     * @param geometry
     *            the geometry to be matched for deletion
     * @return an immutable RTree without one entry (if found) matching the given
     *         value and geometry
     */
    public RTree<T, S> delete(T value, S geometry) {
        return delete(context.factory().createEntry(value, geometry), false);
    }

    /**
     * Deletes one or all matching entries depending on the value of
     * <code>all</code>. If multiple copies of the entry are in the R-tree only one
     * will be deleted if all is false otherwise all matching entries will be
     * deleted. The entry must match on both value and geometry to be deleted.
     * 
     * @param entry
     *            the {@link Entry} to be deleted
     * @param all
     *            if true deletes all matches otherwise deletes first found
     * @return a new immutable R-tree without one instance of the specified entry
     */
    public RTree<T, S> delete(Entry<? extends T, ? extends S> entry, boolean all) {
        if (root.isPresent()) {
            NodeAndEntries<T, S> nodeAndEntries = root.get().delete(entry, all);
            if (nodeAndEntries.node().isPresent() && nodeAndEntries.node().get() == root.get())
                return this;
            else
                return new RTree<T, S>(nodeAndEntries.node(),
                        size - nodeAndEntries.countDeleted() - nodeAndEntries.entriesToAdd().size(), context)
                                .add(nodeAndEntries.entriesToAdd());
        } else
            return this;
    }

    /**
     * Deletes one entry if it exists, returning an immutable copy of the RTree
     * without that entry. If multiple copies of the entry are in the R-tree only
     * one will be deleted. The entry must match on both value and geometry to be
     * deleted.
     * 
     * @param entry
     *            the {@link Entry} to be deleted
     * @return a new immutable R-tree without one instance of the specified entry
     */
    public RTree<T, S> delete(Entry<? extends T, ? extends S> entry) {
        return delete(entry, false);
    }

    /**
     * <p>
     * Returns an {@link Iterable} of {@link Entry} that satisfy the given
     * condition. Note that this method is well-behaved only if:
     *
     * 
     * <p>
     * {@code condition(g)} is true for {@link Geometry} g implies
     * {@code condition(r)} is true for the minimum bounding rectangles of the
     * ancestor nodes.
     * 
     * <p>
     * {@code distance(g) < D} is an example of such a condition.
     * 
     * 
     * @param condition
     *            return Entries whose geometry satisfies the given condition
     * @return sequence of matching entries
     */
    @VisibleForTesting
    Iterable<Entry<T, S>> search(Predicate<? super Geometry> condition) {
        if (root.isPresent())
            return Search.search(root.get(), condition);
        else
            return Collections.emptyList();
    }

    /**
     * Returns a predicate function that indicates if {@link Geometry} intersects
     * with a given rectangle.
     * 
     * @param r
     *            the rectangle to check intersection with
     * @return whether the geometry and the rectangle intersect
     */
    public static Predicate<Geometry> intersects(final Rectangle r) {
        return new Predicate<Geometry>() {
            @Override
            public boolean test(Geometry g) {
                return g.intersects(r);
            }
        };
    }

    /**
     * Returns the always true predicate. See {@link RTree#entries()} for example
     * use.
     */
    private static final Predicate<Geometry> ALWAYS_TRUE = new Predicate<Geometry>() {
        @Override
        public boolean test(Geometry rectangle) {
            return true;
        }
    };

    /**
     * Returns an {@link Iterable} sequence of all {@link Entry}s in the R-tree
     * whose minimum bounding rectangle intersects with the given rectangle.
     * 
     * @param r
     *            rectangle to check intersection with the entry mbr
     * @return entries that intersect with the rectangle r
     */
    public Iterable<Entry<T, S>> search(final Rectangle r) {
        return search(intersects(r));
    }

    /**
     * Returns an {@link Iterable} sequence of all {@link Entry}s in the R-tree
     * whose minimum bounding rectangle intersects with the given point.
     * 
     * @param p
     *            point to check intersection with the entry mbr
     * @return entries that intersect with the point p
     */
    public Iterable<Entry<T, S>> search(final Point p) {
        return search(p.mbr());
    }

    /**
     * Returns the intersections with the the given (arbitrary) geometry using an
     * intersection function to filter the search results returned from a search of
     * the mbr of <code>g</code>.
     * 
     * @param <R>
     *            type of geometry being searched for intersection with
     * @param g
     *            geometry being searched for intersection with
     * @param intersects
     *            function to determine if the two geometries intersect
     * @return a sequence of entries that intersect with g
     */
    public <R extends Geometry> Iterable<Entry<T, S>> search(final R g,
            final BiPredicate<? super S, ? super R> intersects) {
        return Iterables.filter(search(g.mbr()), entry -> intersects.test(entry.geometry(), g));
    }

    /**
     * Returns an {@link Iterable} sequence of all {@link Entry}s in the R-tree
     * whose minimum bounding rectangles are strictly less than maxDistance from the
     * given rectangle.
     * 
     * @param r
     *            rectangle to measure distance from
     * @param maxDistance
     *            entries returned must be within this distance from rectangle r
     * @return the sequence of matching entries
     */
    public Iterable<Entry<T, S>> search(final Rectangle r, final double maxDistance) {
        return search(new Predicate<Geometry>() {
            @Override
            public boolean test(Geometry g) {
                return g.distance(r) < maxDistance;
            }
        });
    }

    /**
     * Returns an {@link Iterable} sequence of all {@link Entry}s in the R-tree
     * whose minimum bounding rectangles are within maxDistance from the given
     * point.
     * 
     * @param p
     *            point to measure distance from
     * @param maxDistance
     *            entries returned must be within this distance from point p
     * @return the sequence of matching entries
     */
    public Iterable<Entry<T, S>> search(final Point p, final double maxDistance) {
        return search(p.mbr(), maxDistance);
    }

    /**
     * Returns all entries strictly less than <code>maxDistance</code> from the
     * given geometry. Because the geometry may be of an arbitrary type it is
     * necessary to also pass a distance function.
     * 
     * @param <R>
     *            type of the geometry being searched for
     * @param g
     *            geometry to search for entries within maxDistance of
     * @param maxDistance
     *            strict max distance that entries must be from g
     * @param distance
     *            function to calculate the distance between geometries of type S
     *            and R.
     * @return entries strictly less than maxDistance from g
     */
    public <R extends Geometry> Iterable<Entry<T, S>> search(final R g, final double maxDistance,
            BiFunction<? super S, ? super R, Double> distance) {
        return Iterables.filter( //
                search(entry -> entry.distance(g.mbr()) < maxDistance), // refine with distance function
                entry -> distance.apply(entry.geometry(), g) < maxDistance);
    }

    /**
     * Returns the nearest k entries (k=maxCount) to the given rectangle where the
     * entries are strictly less than a given maximum distance from the rectangle.
     * 
     * @param r
     *            rectangle
     * @param maxDistance
     *            max distance of returned entries from the rectangle
     * @param maxCount
     *            max number of entries to return
     * @return nearest entries to maxCount, in ascending order of distance
     */
    public Iterable<Entry<T, S>> nearest(final Rectangle r, final double maxDistance, int maxCount) {
        BoundedPriorityQueue<Entry<T, S>> q = new BoundedPriorityQueue<Entry<T, S>>(maxCount,
                Comparators.<T, S>ascendingDistance(r));
        for (Entry<T, S> entry : search(r, maxDistance)) {
            q.add(entry);
        }
        return q.asOrderedList();
    }

    /**
     * Returns the nearest k entries (k=maxCount) to the given point where the
     * entries are strictly less than a given maximum distance from the point.
     * 
     * @param p
     *            point
     * @param maxDistance
     *            max distance of returned entries from the point
     * @param maxCount
     *            max number of entries to return
     * @return nearest entries to maxCount, in ascending order of distance
     */
    public Iterable<Entry<T, S>> nearest(final Point p, final double maxDistance, int maxCount) {
        return nearest(p.mbr(), maxDistance, maxCount);
    }

    /**
     * Returns all entries in the tree as an {@link Iterable} sequence.
     * 
     * @return all entries in the R-tree
     */
    public Iterable<Entry<T, S>> entries() {
        return search(ALWAYS_TRUE);
    }

    /**
     * Returns a {@link Visualizer} for an image of given width and height and
     * restricted to the given view of the coordinates. The points in the view are
     * scaled to match the aspect ratio defined by the width and height.
     * 
     * @param width
     *            of the image in pixels
     * @param height
     *            of the image in pixels
     * @param view
     *            using the coordinate system of the entries
     * @return visualizer
     */
    @SuppressWarnings("unchecked")
    public Visualizer visualize(int width, int height, Rectangle view) {
        return new Visualizer((RTree<?, Geometry>) this, width, height, view);
    }

    /**
     * Returns a {@link Visualizer} for an image of given width and height and
     * restricted to the the smallest view that fully contains the coordinates. The
     * points in the view are scaled to match the aspect ratio defined by the width
     * and height.
     * 
     * @param width
     *            of the image in pixels
     * @param height
     *            of the image in pixels
     * @return visualizer
     */
    public Visualizer visualize(int width, int height) {
        return visualize(width, height, calculateMaxView(this));
    }

    private Rectangle calculateMaxView(RTree<T, S> tree) {
        Iterator<Entry<T, S>> it = tree.entries().iterator();
        Rectangle r = null;
        while (it.hasNext()) {
            Entry<T, S> entry = it.next();
            if (r != null)
                r = r.add(entry.geometry().mbr());
            else
                r = entry.geometry().mbr();
        }
        if (r == null) {
            double[] zero = new double[context.dimensions()];
            return Rectangle.create(zero, zero);
        } else {
            return r;
        }
    }

    public Optional<? extends Node<T, S>> root() {
        return root;
    }

    /**
     * If the RTree has no entries returns {@link Optional#empty} otherwise returns
     * the minimum bounding rectangle of all entries in the RTree.
     * 
     * @return minimum bounding rectangle of all entries in RTree
     */
    public Optional<Rectangle> mbr() {
        if (!root.isPresent())
            return empty();
        else
            return of(root.get().geometry().mbr());
    }

    /**
     * Returns true if and only if the R-tree is empty of entries.
     * 
     * @return is R-tree empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the number of entries in the RTree.
     * 
     * @return the number of entries
     */
    public int size() {
        return size;
    }

    /**
     * Returns a {@link Context} containing the configuration of the RTree at the
     * time of instantiation.
     * 
     * @return the configuration of the RTree prior to instantiation
     */
    public Context<T, S> context() {
        return context;
    }

    /**
     * Returns a human readable form of the RTree. Here's an example:
     * 
     * <pre>
     * mbr=Rectangle [x1=10.0, y1=4.0, x2=62.0, y2=85.0]
     *   mbr=Rectangle [x1=28.0, y1=4.0, x2=34.0, y2=85.0]
     *     entry=Entry [value=2, geometry=Point [x=29.0, y=4.0]]
     *     entry=Entry [value=1, geometry=Point [x=28.0, y=19.0]]
     *     entry=Entry [value=4, geometry=Point [x=34.0, y=85.0]]
     *   mbr=Rectangle [x1=10.0, y1=45.0, x2=62.0, y2=63.0]
     *     entry=Entry [value=5, geometry=Point [x=62.0, y=45.0]]
     *     entry=Entry [value=3, geometry=Point [x=10.0, y=63.0]]
     * </pre>
     * 
     * @return a string representation of the RTree
     */
    public String asString(RTree<T, S> tree) {
        if (!root.isPresent())
            return "";
        else
            return asString(tree, root.get(), "");
    }

    private final static String marginIncrement = "  ";

    private String asString(RTree<T, S> tree, Node<T, S> node, String margin) {

        int level=0;
        StringBuilder s = new StringBuilder();
        s.append(margin);
        s.append("mbr=");
        s.append(node.geometry());
        //s.append(node.geometry().mbr().volume());

        Iterable<Entry<T,S>> results =
                tree.search(Rectangle.create(node.geometry().mbr().min(0),node.geometry().mbr().min(1) , node.geometry().mbr().max(0), node.geometry().mbr().max(1)));
        //List<Entry<T, S>> list= StreamSupport.stream(results.spliterator(), false).collect(Collectors.toList());
        List<Entry<T, S>> list=new ArrayList<Entry<T,S>>();
        results.forEach(list::add);
        s.append(" Count of data points=");
        s.append(list.size());
        s.append(" ");
        s.append("Level:");
        s.append(tree.calculateDepth2(node,0));
        s.append(" ");

        s.append("Linear Sum: ");
        double sum = 0;
        double sum2 = 0;
        for(int i = 0; i < list.size(); i++) {
            sum += (Double)list.get(i).value();
            //sum += list.get(i).geometry().mbr().min(0);
            sum2 += (Double)list.get(i).value() * (Double)list.get(i).value();
            //sum2 += (list.get(i).geometry().mbr().min(0))*(list.get(i).geometry().mbr().min(0));}
        }
        s.append(sum);

        s.append(" Square Sum: ");
        s.append(sum2);

        s.append('\n');

        if (!node.isLeaf()) {

            NonLeaf<T, S> n = (NonLeaf<T, S>) node;

            for (int i = 0; i < n.count(); i++) {
                Node<T, S> child = n.child(i);
                s.append(asString(tree, child, margin + marginIncrement));


            }


        }
        else
            {
            Leaf<T, S> leaf = (Leaf<T, S>) node;

            for (Entry<T, S> entry : leaf.entries()) {

                s.append(margin);
                s.append(marginIncrement);
                //take this out later
                s.append("entry=");
                s.append(entry);
                s.append('\n');

            }
            s.append(margin);
            s.append(marginIncrement);
            s.append('\n');
        }



        return s.toString();

    }

    //un shtoj ketu
    public String asString2(RTree<T, S> tree, List<Entry<Object, Point>> listc) {
        if (!root.isPresent())
            return "";
        else
            return asString2(tree, root.get(), "", listc);
    }

    private final static String marginIncrement2 = "  ";

    private String asString2(RTree<T, S> tree, Node<T, S> node, String margin, List<Entry<Object, Point>> listc) {

        int level=0;
        StringBuilder s = new StringBuilder();
        s.append(margin);
        s.append("mbr=");
        s.append(node.geometry());

       // Iterable<Entry<T, S>> results =
         //       tree.search(Rectangle.create(node.geometry().mbr().min(0),node.geometry().mbr().min(1) , node.geometry().mbr().max(0), node.geometry().mbr().max(1)));
        //List<Entry<T, S>> list= StreamSupport.stream(results.spliterator(), false).collect(Collectors.toList());
        //s.append(tree.calculateDepth2(node,0)); //level
        //s.append(" ");
        //s.append(list.size()); //count of data points
        //s.append(" ");
        //s.append(node.geometry().mbr().min(0));
        //s.append(" ");
        //s.append(node.geometry().mbr().max(0));
        //s.append(" ");
        //s.append(node.geometry().mbr().min(1));
        //s.append(" ");
        //s.append(node.geometry().mbr().max(1));
       // s.append(" ");


        double sum = 0;
        double sum2=0;
        int countc=0;
        for(int i = 0; i < listc.size(); i++)
        {
        {if
        (     listc.get(i).geometry().mbr().min(0)>=node.geometry().mbr().min(0)
                        && listc.get(i).geometry().mbr().max(0)<=node.geometry().mbr().max(0)
                        && listc.get(i).geometry().mbr().min(1)>=node.geometry().mbr().min(1)
                        && listc.get(i).geometry().mbr().max(1)<=node.geometry().mbr().max(1))
        {
            sum += listc.get(i).geometry().mbr().min(2);
            sum2 += (listc.get(i).geometry().mbr().min(2))*(listc.get(i).geometry().mbr().min(2));
            countc++;}}}
        s.append(" ");
        s.append(countc); //square sum
        s.append(" ");
       s.append(sum); //linear sum of 1st dimension

        s.append(" ");
        s.append(sum2); //square sum

        s.append('\n');

        if (!node.isLeaf()) {

            NonLeaf<T, S> n = (NonLeaf<T, S>) node;

            for (int i = 0; i < n.count(); i++) {
                Node<T, S> child = n.child(i);
                s.append(asString2(tree, child, margin + marginIncrement2, listc));


            }


        }
        else
        {
            Leaf<T, S> leaf = (Leaf<T, S>) node;

            for (Entry<T, S> entry : leaf.entries()) {
                s.append(margin);
                s.append(marginIncrement);
                //take this out later
                s.append("entry=");
                s.append(entry);
                s.append('\n');


            }
           // s.append(margin);
            //s.append(marginIncrement2);
            //s.append('\n');
        }



        return s.toString();

    }

    public String asString3(RTree<T, S> tree, List<Entry<Object, Point>> listc) {
        if (!root.isPresent())
            return "";
        else
            return asString3(tree, root.get(), "", listc,0);
    }



    private String asString3(RTree<T, S> tree, Node<T, S> node, String margin, List<Entry<Object, Point>> listc, double lin_sum) {

        int level=0;
        StringBuilder s = new StringBuilder();
        s.append(margin);
        s.append(tree.calculateDepth2(node,0)); //level
        s.append(", ");
        s.append(node.geometry().mbr().min(0)); //min range dimension_1
        s.append(", ");
        s.append(node.geometry().mbr().min(1)); //min range dimension_2
        s.append(", ");
        s.append(node.geometry().mbr().min(2)); //min range dimension_3
        s.append(", ");
        s.append(node.geometry().mbr().max(0)); //max range dimension_1
        s.append(", ");
        s.append(node.geometry().mbr().max(1)); //max range dimension_2
        s.append(", ");
        s.append(node.geometry().mbr().max(2)); //max range dimension_3
        s.append(", ");
//        s.append(node.geometry().mbr().min(3)); //min range dimension_4
//        s.append(" ");
//        s.append(node.geometry().mbr().max(3)); //max range dimension_4
//        s.append(" ");
//        s.append(node.geometry().mbr().min(4)); //min range dimension_5
//        s.append(" ");
//        s.append(node.geometry().mbr().max(4)); //max range dimension_5
//        s.append(" ");
//        s.append(node.geometry().mbr().min(5)); //min range dimension_6
//        s.append(" ");
//        s.append(node.geometry().mbr().max(5)); //max range dimension_6
//        s.append(" ");
//        s.append(node.geometry().mbr().min(6)); //min range dimension_7
//        s.append(" ");
//        s.append(node.geometry().mbr().max(6)); //max range dimension_7
//        s.append(" ");


        double sum = 0;
        double sum2=0;
        int countc=0;
        int num = 0;
        double temp = 0;
        List<Double> averages = new ArrayList<Double>();

        for(int i = 0; i < listc.size(); i++)
        {
            {if
            (     listc.get(i).geometry().mbr().min(0)>=node.geometry().mbr().min(0)
                            && listc.get(i).geometry().mbr().max(0)<=node.geometry().mbr().max(0)
                            && listc.get(i).geometry().mbr().min(1)>=node.geometry().mbr().min(1)
                            && listc.get(i).geometry().mbr().max(1)<=node.geometry().mbr().max(1)
                            )
            {
                sum += (double)listc.get(i).value();
                temp += (double)listc.get(i).value();
                //sum += listc.get(i).geometry().mbr().min(2);
                //sum2 += (listc.get(i).geometry().mbr().min(2))*(listc.get(i).geometry().mbr().min(2));
                sum2 += (double)listc.get(i).value() * (double)listc.get(i).value();

                num++;
                countc++;}}}
        s.append(countc); //count
        s.append(", ");
        s.append(sum); //linear sum

        s.append(", ");
        s.append(sum2); //square sum

        s.append(", ");
        s.append(lin_sum);
        s.append('\n');

        if (!node.isLeaf()) {

            NonLeaf<T, S> n = (NonLeaf<T, S>) node;

            for (int i = 0; i < n.count(); i++) {
                Node<T, S> child = n.child(i);
                s.append(asString3(tree, child, margin + marginIncrement2, listc, sum));


            }


        }
        else
        { //use this only if you want to output individual entries
            Leaf<T, S> leaf = (Leaf<T, S>) node;

            for (Entry<T, S> entry : leaf.entries()) {
                //s.append(margin);
                //s.append(marginIncrement);
                //take this out later
                //s.append("entry=");
                //s.append(entry);
                //s.append('\n');


            }
            // s.append(margin);
            //s.append(marginIncrement2);
            //s.append('\n');
        }



        return s.toString();

    }


    public int dimensions() {
        return context.dimensions();
    }
    
    public void visit(Visitor<T, S> visitor) {
        if (root.isPresent()) {
        visit(root.get(), visitor);
        }
    }

    private void visit(Node<T, S> node, Visitor<T, S> visitor) {
        if (node.isLeaf()) {
            visit((Leaf<T,S>)node, visitor);
        } else {
            visit((NonLeaf<T,S>) node, visitor);
        }
    }
    
    private void visit(Leaf<T, S> leaf, Visitor<T, S> visitor) {
        visitor.leaf(leaf);
    }
    
    private void visit(NonLeaf<T, S> nonLeaf, Visitor<T, S> visitor) {
        visitor.nonLeaf(nonLeaf);
        for (Node<T, S> node: nonLeaf.children()) {
            visit(node, visitor);
        }
    }

    public static void main(String[] args) {

        Point sydney = Point.create( -33.86, 151.2094);
        Point canberra = Point.create( -35.3075, 149.1244);
        Point brisbane = Point.create( -27.4679, 153.0278);
        Point bungendore = Point.create( -35.2500, 149.4500);


        RTree<String, Point> tree = RTree.star().dimensions(2).create();
        tree = tree.add("Sydney", sydney);
        tree = tree.add("Brisbane", brisbane);
        Visualizer v = tree.visualize(200, 200);
        //v.save("/Users/user/Desktop/saved-image");

        final double distanceKm = 0;

        Iterable<Entry<String, Point>> entries = tree.search(canberra, 300.0);

        for (Object o: entries)
        { System.out.println(o);}
    }


}
