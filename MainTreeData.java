package com.github.davidmoten.rtreemulti;

import com.github.davidmoten.rtreemulti.geometry.Point;
import org.davidmoten.kool.Stream;

import java.sql.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class MainTreeData {

    //Creates the list of entries for the tree with only the dimension attributes.
    public static Stream<Entry<Object, Point>> entries() {
        return Stream
                .using(() -> new GZIPInputStream(
                                //1. convert the .csv file to .txt
                                //2. compress the .txt file (gzip)
                                //3. drop the compressed file in the resources folder
                                MainTreeData.class.getResourceAsStream("/test_insertion4.txt.gz")),
                        in -> Stream.lines(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))))
                .flatMap(line -> {
                    if (line.trim().length() > 0) {
                        String[] items = line.split("\t");

                        //if there are more than 2 dimensions add them
                        double dimension_1 = Double.parseDouble(items[0]);
                        double dimension_2 = Double.parseDouble(items[1]);
//                        double dimension_3 = Double.parseDouble(items[2]);
//                        double dimension_4 = Double.parseDouble(items[2]);
//                        double dimension_5 = Double.parseDouble(items[2]);
//                        double dimension_6 = Double.parseDouble(items[2]);
//                        double dimension_7 = Double.parseDouble(items[2]);

                        Entry<Object, Point> entry;

                        //entry = Entry.entry(new Object(), Point.create( (double) dimension_1, (double)dimension_2,(double)dimension_3, (double)dimension_4, (double)dimension_5, (double)dimension_6, (double)dimension_7));
                        entry = Entry.entry((double) dimension_1, Point.create( (double) dimension_1, (double)dimension_2));
                        return Stream.of(entry);
                    } else
                        return Stream.empty();
                });
    }

    static List<Entry<Object, Point>> entriesList() {
        List<Entry<Object, Point>> result = entries().toList().get();
        return result;
    }

    //Creates the list of entries with all the attributes in the data
    public static Stream<Entry<Object, Point>> all_entries() {
        return Stream
                .using(() -> new GZIPInputStream(
                                //The same data path as in the entries method.
                                MainTreeData.class.getResourceAsStream("/test_insertion4.txt.gz")),
                        in -> Stream.lines(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))))
                .flatMap(line -> {
                    if (line.trim().length() > 0) {
                        String[] items = line.split("\t");

                        //add the dimensions of the tree + the attribute which is performing the linear/square sum upon. (in this case, attribute_3)
                        double attribute_1 = Double.parseDouble(items[0]);
                        double attribute_2 = Double.parseDouble(items[1]);
                        //double attribute_3 = Double.parseDouble(items[2]);

                        Entry<Object, Point> entry;

                        entry = Entry.entry((double) attribute_1, Point.create( (double) attribute_1, (double)attribute_2));
                        //entry = Entry.entry(new Object(), Point.create( (double) attribute_1, (double)attribute_2, (double)attribute_3));
                        return Stream.of(entry);
                    } else
                        return Stream.empty();
                });
    }

    static List<Entry<Object, Point>> entriesList2() {
        List<Entry<Object, Point>> result2 = all_entries().toList().get();
        return result2;
    }
}
