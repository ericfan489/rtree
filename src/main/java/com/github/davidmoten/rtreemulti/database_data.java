package com.github.davidmoten.rtreemulti;

import com.github.davidmoten.rtreemulti.geometry.Geometry;
import com.github.davidmoten.rtreemulti.geometry.Point;
import com.github.davidmoten.rtreemulti.geometry.Rectangle;

import java.io.FileWriter;
import java.sql.*;
import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.File;  // Import the File class
import java.io.IOException; // Import the IOException class to handle exceptions
import java.util.Observable;

public class database_data {

    //Credentials to connect to database
    private final String url = "jdbc:postgresql://130.203.223.234:5432/nsf_roadtraffic_friction_v2";
    private final String user = "ivsg_db_user";
    private final String password = "ivsg@DB320";

    // Variables that can be changed to modify how the tree is built

    // User input max children per node
    private static int maxChildren=256;

    // User input min children per node
    private static int minChildren= maxChildren/2;

    // User input number of dimensions to build the tree
    private static int dimensions=10;

    // User input names of the attributes to use as dimensions to build the tree
    private static List<String> dimension_attributes = new ArrayList<String>(Arrays.asList("true_positioncg_e","true_positioncg_n","true_positioncg_u","true_positioncg_latitude", "true_positioncg_longitude", "true_positioncg_altitude","fl_friction_noisy", "fr_friction_noisy", "rl_friction_noisy", "rr_friction_noisy" ));

    // name of the attribute used to calculate linear sum and square sum
    private String data =  "fl_friction_noisy";

    // String query to get data from the database
    private String query1 = "SELECT true_positioncg_e, true_positioncg_n, true_positioncg_u, true_positioncg_latitude, true_positioncg_longitude, true_positioncg_altitude, fl_friction_noisy, fr_friction_noisy, rl_friction_noisy, rr_friction_noisy FROM friction_measurement_uml_query_test LIMIT 10000";

    // name of the file that the output is saved to
    public static String save_file_name = "database_data.txt";

    public  List<Entry<Object, Point>> connect() {
        Connection con = null;
        List<Entry<Object, Point>> result = new ArrayList<Entry<Object, Point>>();
        try {
            con = DriverManager.getConnection(url, user, password);

            // Create a statement for SQL query - move the cursor either forward or backward
            Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);

            //record the execution time
            long executionTime = 0;
            long start = System.currentTimeMillis();

            //String query1 = "SELECT true_positioncg_latitude, true_positioncg_longitude, true_positioncg_altitude, fl_friction_noisy, fr_friction_noisy, rl_friction_noisy, rr_friction_noisy FROM friction_measurement_uml_query_test LIMIT 10000";
            //String query1 ="SELECT true_positioncg_latitude, true_positioncg_longitude, true_positioncg_altitude, fl_friction_noisy, fr_friction_noisy, rl_friction_noisy, rr_friction_noisy FROM friction_measurement_uml_query_test LIMIT 100";
            ResultSet rs1 = stmt.executeQuery(query1);

            while (rs1.next()) {
                List<Double> points = new ArrayList<Double>();
                for(int x = 0; x < dimension_attributes.size(); x++){
                    points.add(rs1.getDouble(dimension_attributes.get(x)));
                }
                result.add(Entry.entry(rs1.getDouble(data),Point.create(points)));
                //result.add(Entry.entry(rs1.getDouble("fl_friction_noisy"), Point.create(rs1.getDouble("true_positioncg_latitude"),rs1.getDouble("true_positioncg_longitude"),rs1.getDouble("true_positioncg_altitude"),rs1.getDouble("fl_friction_noisy"),rs1.getDouble("fr_friction_noisy"),rs1.getDouble("rl_friction_noisy"),rs1.getDouble("rr_friction_noisy"))));
                //result.add(Entry.entry(rs1.getDouble("fl_friction_noisy"), Point.create(rs1.getDouble("true_positioncg_latitude"),rs1.getDouble("true_positioncg_longitude"))));
            }
            System.out.println("results queried...");

            // Record the time the query stops running
            long end = System.currentTimeMillis();
            // The difference in time from start to end of running
            executionTime = end - start;

            System.out.println("Execution time: " + executionTime + " milliseconds");

            // Close the connection object
            con.close();

            //System.out.println("Connected to the PostgreSQL server successfully.");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return result;
    }

    //total number of data points
    private static int size=10000;

    //max nr of children

    //Create an R* tree with the dimension entries. Change the maxChildren, minChildren, dimensions as required.

    //For testing purposes, the result will be saved in .csv file on IntelliJ
    public static void main(String[] args) {

        // The below output will be saved in a .csv file
        // IntelliJ->Run->Configurations->Logs->Save console output to .csv file

        database_data temp = new database_data();
        List<Entry<Object, Point>>attributes = temp.connect();
        RTree<Object, Point> test_tree = RTree.maxChildren(maxChildren).minChildren(minChildren).dimensions(dimensions).star().<Object, Point>create().add(attributes);

        Iterable<Entry<Object, Point>> search_test = test_tree.entries();
        Iterator my_iterator = search_test.iterator();
        while(my_iterator.hasNext()) {
            Object element = my_iterator.next();
            System.out.print(element + " ");
        }
        System.out.println(test_tree.asString(test_tree));
        //Creates the rectangles representation in the target folder.
        //test_tree.visualize(600, 600).save("target/test_rtree_" + size + "_" + maxChildren + "_data.png");
        try {
            File myObj = new File(save_file_name);
            if (myObj.createNewFile()) {
                System.out.println("File created successfully.");
            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

        try {
            FileWriter myWriter = new FileWriter(save_file_name);
            myWriter.write(test_tree.asString(test_tree));
            myWriter.close();
            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }
}
