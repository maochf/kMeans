package edu.stanford.cs246.kMeans;

import java.io.*;
import java.util.Arrays;



//import org.apache.hadoop.classification.InterfaceAudience;
//import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class kMeans extends Configured implements Tool {
//   private final static int NUM_DIM = 58;
//   private final static int K = 10;
  private final static int NUM_DIM = 2;
   private final static int K = 2;
   private static double sum_cost;
   private static double[][] centroids;
   public static void main(String[] args) throws Exception {
      System.out.println(Arrays.toString(args));
      Configuration conf = new Configuration();
      int res = -1;
      for (int i = 0; i < 6; i++){
    	  if (i == 0) {
    		  conf.set("centroid_file", args[2]);
    	  }
    	  else {
    		  conf.set("centroid_file", "output"+Integer.toString(i - 1)+"/part-r-00000");
    	  }
    	  conf.set("output_dir", "output"+Integer.toString(i));
    	  res = ToolRunner.run(conf, new kMeans(), args);
      }
      
      
      System.exit(res);
   }

   @SuppressWarnings("deprecation")
@Override
   public int run(String[] args) throws Exception {
      System.out.println(Arrays.toString(args));
      sum_cost = 0;
      
      Configuration conf = getConf();
      
      BufferedReader br = new BufferedReader(new FileReader(conf.get("centroid_file")));
  	  String line = null;
  	  centroids = new double[K][NUM_DIM];
  	  
  	  int centroid_count = 0;
  	  while ((line = br.readLine()) != null) {
  		String[] val_str_array = line.split("\\s");
        for (int i = 0; i < NUM_DIM; i++) {
        	centroids[centroid_count][i] = Double.parseDouble(val_str_array[i]);
        }
        centroid_count += 1;
      }
  	  
  	  
  	  br.close();
      Job job = new Job(conf, "kMeans");
      job.setJarByClass(kMeans.class);

      job.setMapperClass(Map.class);
      job.setReducerClass(Reduce.class);

      job.setInputFormatClass(TextInputFormat.class);
      job.setOutputFormatClass(TextOutputFormat.class);
      
      job.setOutputKeyClass(NullWritable.class);
      job.setOutputValueClass(Text.class);
 
      job.setMapOutputKeyClass(IntWritable.class);
      job.setMapOutputValueClass(DoubleArrayWritable.class);
      
      FileInputFormat.addInputPath(job, new Path(args[0]));
      FileOutputFormat.setOutputPath(job, new Path(conf.get("output_dir")));

      job.waitForCompletion(true);
      try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("total_cost.txt", true)))) {
    	    out.println(sum_cost);
    	}catch (IOException e) {
    	    //exception handling left as an exercise for the reader
    	}
      return 0;
   }
   
   private static class DoubleArrayWritable implements Writable {
    private double[] values;

    public DoubleArrayWritable() {

    }

    public DoubleArrayWritable(double[] data) {
       this.values = data;
    }

    public double[] get() {
       return values;
    }
    
    public void set(double[] data) {
       this.values = data;
    }
    
    @Override
    public void write(DataOutput out) throws IOException {
       int length = values.length;
       out.writeInt(length);
       for(int i = 0; i < length; i++) {
            out.writeDouble(values[i]);
       }
    }
    
    @Override
    public void readFields(DataInput in) throws IOException {
       int length = in.readInt();

       values = new double[length];

       for(int i = 0; i < length; i++) {
            values[i] = in.readDouble();
       }
    }
  }

   public static class Map extends Mapper<LongWritable, Text, IntWritable, DoubleArrayWritable> {
      private final static IntWritable ONE = new IntWritable(1);
      private Text word = new Text();

      @Override
      public void map(LongWritable key, Text value, Context context)
              throws IOException, InterruptedException {
         String[] val_str_array = value.toString().split("\\s");
         double[] val_array = new double[NUM_DIM]; // store the cost at the end of the array 
         for (int i = 0; i < NUM_DIM; i++) {
         	val_array[i] = Double.parseDouble(val_str_array[i]);
         }
         double min_cost = Double.MAX_VALUE;
         int c = -1;
         for (int i = 0; i < K; i++) {
             double cost = euclideanDistanceCost(centroids[i], val_array);
             if (cost < min_cost) {
             	min_cost = cost;
             	c = i;
             }
         }
         sum_cost += min_cost;
         DoubleArrayWritable outputArray = new DoubleArrayWritable(val_array);
         context.write(new IntWritable(c), outputArray);
      }
      
      private double euclideanDistanceCost(double[] centroid, double[] val_array){
    	  double cost = 0;
    	  for (int i = 0; i < NUM_DIM; i++) {
    		  cost += Math.pow(centroid[i] - val_array[i], 2);
    	  }
    	  return cost;
      }
      
      private double manhattanDistanceCost(double[] centroid, double[] val_array){
    	  double cost = 0;
    	  for (int i = 0; i < NUM_DIM; i++) {
    		  cost += Math.abs(centroid[i] - val_array[i]);
    	  }
    	  return cost;
      }
   }

   public static class Reduce extends Reducer<IntWritable, DoubleArrayWritable, NullWritable, Text> {
      @Override
      public void reduce(IntWritable key, Iterable<DoubleArrayWritable> values, Context context)
              throws IOException, InterruptedException {
         double[] new_centroid = new double[NUM_DIM];
         int num_points = 0;
         for (DoubleArrayWritable val : values) {
        	 double[] val_array = val.get();
        	 for (int i = 0; i < NUM_DIM; i++) {
        		 new_centroid[i] += val_array[i];
        	 }
        	 num_points += 1;
         }
         String new_centroid_str = "";
         for (int i = 0; i < NUM_DIM; i++) {
            if (i > 0) {
            	new_centroid_str += " ";
            }
            new_centroid_str += Double.toString(new_centroid[i]/num_points);
         }
         try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("centroids.txt", true)))) {
     	    out.println(new_centroid_str);
     	}catch (IOException e) {
     	    //exception handling left as an exercise for the reader
     	}
         context.write(NullWritable.get(), new Text(new_centroid_str));
      }
   }
   
}
