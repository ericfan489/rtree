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

public class true_positioncg_latitude_test {


    private final String url = "jdbc:postgresql://130.203.223.234:5432/nsf_roadtraffic_friction_v2";
    private final String user = "ivsg_db_user";
    private final String password = "ivsg@DB320";

    // Variables that can be changed to modify how the tree is built

    // User input max children per node
    private static int maxChildren=64;

    // User input min children per node
    private static int minChildren=32;

    // User input number of dimensions to build the tree
    private static int dimensions=2;

    // User input names of the attributes to use as dimensions to build the tree
    private static List<String> dimension_attributes = new ArrayList<String>(Arrays.asList("true_positioncg_n", "true_positioncg_e"));

    // User input name of the attribute used to calculate linear sum and square sum
    private String data =  "fl_friction_noisy";

    // User input String query to get data from the database
    private String query1 = "SELECT true_positioncg_n, true_positioncg_e, fl_friction_noisy FROM friction_measurement_uml_query_test ORDER BY true_positioncg_n, true_positioncg_e LIMIT 300000";

    // User input name of the file that the output is saved to
    public static String save_file_name = "true_positioncg_latitude_test.csv";

    public  List<Entry<Object, Point>> connect() {
        Connection con = null;
        // initialize list to store data points
        List<Entry<Object, Point>> result = new ArrayList<Entry<Object, Point>>();
        try {
            con = DriverManager.getConnection(url, user, password);

            // Create a statement for SQL query - move the cursor either forward or backward
            Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);

            //record the execution time
            long executionTime = 0;
            long start = System.currentTimeMillis();

           
            ResultSet rs1 = stmt.executeQuery(query1);

            while (rs1.next()) {
                List<Double> points = new ArrayList<Double>();
                for(int x = 0; x < dimension_attributes.size(); x++){
                    points.add(rs1.getDouble(dimension_attributes.get(x)));
                }
                //store data points in list
                result.add(Entry.entry(rs1.getDouble(data),Point.create(points)));
               
            }
            System.out.println("results queried...");

            // Record the time the query stops running
            long end = System.currentTimeMillis();
            // The difference in time from start to end of running
            executionTime = end - start;

            System.out.println("Execution time: " + executionTime + " milliseconds");

            // Close the connection object
            con.close();
            
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        //return the resulting list of data points
        return result;
    }

    //total number of data points
    private static int size=10000;

    //For testing purposes, the result will be saved in .csv file on IntelliJ
    public static void main(String[] args) {

        // Initialize a database driver object and use it to run the function
        true_positioncg_latitude_test temp = new true_positioncg_latitude_test();
        List<Entry<Object, Point>>attributes = temp.connect();
        RTree<Object, Point> test_tree = RTree.maxChildren(maxChildren).minChildren(minChildren).dimensions(dimensions).star().<Object,Point>create().add(attributes);

        // Run a search for all entries within the tree and print them to the screen
        Iterable<Entry<Object, Point>> search_test = test_tree.entries();
        Iterator my_iterator = search_test.iterator();
        while(my_iterator.hasNext()) {
            Object element = my_iterator.next();
            System.out.print(element + " ");
        }

        test_tree.visualize(600, 600).save("target/test_location300000_rtree_" + maxChildren + "_data.png");
        try {
            //create file to write output to
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
            //write the output of the rtree to a file
            FileWriter myWriter = new FileWriter(save_file_name);
            myWriter.append(test_tree.asString(test_tree));
            myWriter.flush();
            myWriter.close();
            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }
}
