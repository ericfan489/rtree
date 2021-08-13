package com.github.davidmoten.rtreemulti;

import com.github.davidmoten.rtreemulti.geometry.Point;
import org.davidmoten.kool.Stream;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class LatLongTime2 {

    public static Stream<Entry<Object, Point>> entries() {
        return Stream
                .using(() -> new GZIPInputStream(
                                //LatLongTime2.class.getResourceAsStream("/lat_long_time2b.txt.gz")),
                                LatLongTime2.class.getResourceAsStream("/segment403data.txt.gz")),
                        in -> Stream.lines(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))))
                .flatMap(line -> {
                    if (line.trim().length() > 0) {
                        String[] items = line.split("\t");
                        double lat = Double.parseDouble(items[0]);
                        double lon = Double.parseDouble(items[1]);



                        Entry<Object, Point> entry;

                       // entry = Entry.entry(new Object(), Point.create(  (int) lat, (int) lon ));
                        entry = Entry.entry(new Object(), Point.create( (double) lat, (double)lon));
                        return Stream.of(entry);
                    } else
                        return Stream.empty();
                });
    }

    static List<Entry<Object, Point>> entriesList() {
        List<Entry<Object, Point>> result = entries().toList().get();
        System.out.println("loaded tree into list");
        return result;
    }
    public static Stream<Entry<Object, Point>> entries2() {
        return Stream
                .using(() -> new GZIPInputStream(
                                //LatLongTime2.class.getResourceAsStream("/lat_long_time2b.txt.gz")),
                                LatLongTime2.class.getResourceAsStream("/segment403data.txt.gz")),
                        in -> Stream.lines(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))))
                .flatMap(line -> {
                    if (line.trim().length() > 0) {
                        String[] items = line.split("\t");
                        double lat = Double.parseDouble(items[0]);
                        double lon = Double.parseDouble(items[1]);
                        double fri = Double.parseDouble(items[2]);

                        Entry<Object, Point> entry;

                        // entry = Entry.entry(new Object(), Point.create(  (int) lat, (int) lon ));
                        entry = Entry.entry(new Object(), Point.create( (double) lat, (double)lon, (double)fri));
                        return Stream.of(entry);
                    } else
                        return Stream.empty();
                });
    }

    static List<Entry<Object, Point>> entriesList2() {
        List<Entry<Object, Point>> result2 = entries2().toList().get();
        System.out.println("loaded data+ friction into list");
        return result2;
    }

    public static Stream<Entry<Object, Point>> testRotate2() {

        double cx=0;
        double cy=0;
        double angle=45*Math.PI/180;
        double cos=Math.cos(angle);
        double sin=Math.sin(angle);
        final double[] temp = new double[1];

        return Stream
                .using(() -> new GZIPInputStream(
                                //LatLongTime2.class.getResourceAsStream("/lat_long_time2b.txt.gz")),
                                LatLongTime2.class.getResourceAsStream("/east_north_friction_676280.txt.gz")),
                        in -> Stream.lines(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))))
                .flatMap(line -> {
                    if (line.trim().length() > 0) {
                        String[] items = line.split("\t");
                        double lat = Double.parseDouble(items[0]);
                        double lon = Double.parseDouble(items[1]);


                        temp[0] =((lat-cx)*cos - (lon-cy)*sin)+cx;
                        lon=((lat-cx)*sin+(lon-cy)*cos)+cy;
                        lat=temp[0];



                        Entry<Object, Point> entry;

                        // entry = Entry.entry(new Object(), Point.create(  (int) lat, (int) lon ));
                        entry = Entry.entry(new Object(), Point.create( (double) lat, (double)lon));
                        return Stream.of(entry);
                    } else
                        return Stream.empty();
                });
    }

    static List<Entry<Object, Point>> rotate2() {
        List<Entry<Object, Point>> result2 = testRotate2().toList().get();
        System.out.println("loaded data+ friction into list");
        return result2;
    }

    public static Stream<Entry<Object, Point>> testRotate1() {

        double cx=0;
        double cy=0;
        double angle=45*Math.PI/180;
        double cos=Math.cos(angle);
        double sin=Math.sin(angle);
        final double[] temp = new double[1];

        return Stream
                .using(() -> new GZIPInputStream(
                                //LatLongTime2.class.getResourceAsStream("/lat_long_time2b.txt.gz")),
                                LatLongTime2.class.getResourceAsStream("/east_north_friction_676280.txt.gz")),
                        in -> Stream.lines(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))))
                .flatMap(line -> {
                    if (line.trim().length() > 0) {
                        String[] items = line.split("\t");
                        double lat = Double.parseDouble(items[0]);
                        double lon = Double.parseDouble(items[1]);
                        double fri = Double.parseDouble(items[2]);

                        temp[0] =((lat-cx)*cos - (lon-cy)*sin)+cx;
                        lon=((lat-cx)*sin+(lon-cy)*cos)+cy;
                        lat=temp[0];



                        Entry<Object, Point> entry;

                        // entry = Entry.entry(new Object(), Point.create(  (int) lat, (int) lon ));
                        entry = Entry.entry(new Object(), Point.create( (double) lat, (double)lon, (double)fri));
                        return Stream.of(entry);
                    } else
                        return Stream.empty();
                });
    }

    static List<Entry<Object, Point>> rotate1() {
        List<Entry<Object, Point>> result2 = testRotate1().toList().get();
        System.out.println("loaded data+ friction into list");
        return result2;
    }

}
