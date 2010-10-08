package coref.hobbs;

import edu.berkeley.nlp.util.optionparser.Opt;

import java.util.Random;

public class HobbsGlobals {

  // Execution
  @Opt
  public static String fileList = null;  
  @Opt
  public static String outputDir = null;
  @Opt
  public static String typesToSupress = null;
  @Opt
  public static boolean writeRelns = false;
  @Opt
  public static boolean lowercaseHead = false;

  public static Random rand = new Random(0);

  @Opt
  public static String relnExt = ".relns";

  // Train Regime - Iterations
  @Opt
  public static int numJunkTypes = 7;
  @Opt
  public static int numIters = 10;
  @Opt
  public static int numNoProFactorIters = 5;
  @Opt
  public static int numSimpleHeuristicReferIters = 5;
  @Opt
  public static int numInnerIters = 3;
  @Opt
  public static int numReferHyps = 10;
  @Opt
  public static int numHeuristicReferIters = 5;
  @Opt
  public static double discourseDataFrac = 0.1;
  @Opt
  public static boolean useHeadAsMod = true;
  @Opt
  public static boolean useApposAsNomHead = false;  
  @Opt
  public static double headAsModAlpha = 0.1;
  @Opt
  public static double apposAsNomHeadAlpha = 0.01;
  // Process - How Many Threads
  @Opt
  public static int numThreads = Runtime.getRuntime().availableProcessors();

  // Hyperparams  Lambdas
  @Opt
  public static double phiLambda = 1000.0;
  @Opt
  public static double nonProHeadLambda = 1.0e-3;//1.0;
  @Opt
  public static double proHeadLambda = 1.0;
  @Opt
  public static double modLambda = 1.0;
  @Opt
  public static double mtsLambda = 1.0;
  @Opt
  public static double fertilityLambda = 1.0;
  @Opt
  public static double properAlpha = 10.0;


  // Hyperparams Alpha
  @Opt
  public static double headAlpha = 1.0e-3;
  @Opt
  public static double modAlpha = 1.0e-3;

  // Other
  @Opt
  public static int maxListSize = 21;
  @Opt
  public static int maxMods = 2;

  // Resources
  @Opt 
  public static String prototypePath;
  @Opt
  public static String vocabPath = null;
  @Opt
  public static String writeVocabPath = null;
  @Opt
  public static String discourseFeatsPath = null;
  @Opt
  public static String writeDiscourseFeatsPath = null;


}
