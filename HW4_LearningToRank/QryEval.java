/*
 *  Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.3.3.
 */

import java.io.*;
import java.util.*;

import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import static java.util.Comparator.*;

/**
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * It implements an unranked Boolean retrieval model, however it is
 * easily extended to other retrieval models.  For more information,
 * see the ReadMe.txt file.
 */
public class QryEval {

    //  --------------- Constants and variables ---------------------

    private static final String USAGE =
            "Usage:  java QryEval paramFile\n\n";

    private static final String[] TEXT_FIELDS =
            {"body", "title", "url", "inlink"};
    //private QryEval;


    //  --------------- Methods ---------------------------------------

    /**
     * @param args The only argument is the parameter file name.
     * @throws Exception Error accessing the Lucene index.
     */
    public static void main(String[] args) throws Exception {

        //  This is a timer that you may find useful.  It is used here to
        //  time how long the entire program takes, but you can move it
        //  around to time specific parts of your code.

        Timer timer = new Timer();
        timer.start();

        //  Check that a parameter file is included, and that the required
        //  parameters are present.  Just store the parameters.  They get
        //  processed later during initialization of different system
        //  components.

        if (args.length < 1) {
            throw new IllegalArgumentException(USAGE);
        }

        Map<String, String> parameters = readParameterFile(args[0]);

        //  Open the index and initialize the retrieval model.

        Idx.open(parameters.get("indexPath"));
        RetrievalModel model = initializeRetrievalModel(parameters);

        //  Perform experiments.

        if (model instanceof RetrievalModelLetor) {
            ((RetrievalModelLetor) model).mainTrain(parameters);
        } else {
            processQueryFile(parameters.get("queryFilePath"), model,
                    parameters.get("trecEvalOutputPath"), parameters.get("trecEvalOutputLength"), parameters);
        }
        //  Clean up.

        timer.stop();
        System.out.println("Time:  " + timer);
    }

    /**
     * Allocate the retrieval model and initialize it using parameters
     * from the parameter file.
     *
     * @return The initialized retrieval model
     * @throws IOException Error accessing the Lucene index.
     */
    private static RetrievalModel initializeRetrievalModel(Map<String, String> parameters)
            throws IOException {

        RetrievalModel model = null;
        String modelString = parameters.get("retrievalAlgorithm").toLowerCase();
        System.out.println(modelString);
        //parameter for bm25
        double k1;
        double b;
        double k3;
        //parameter for Indri
        double mu;
        double lambda;

        if (modelString.equals("unrankedboolean")) {
            model = new RetrievalModelUnrankedBoolean();
        } else if (modelString.equals("rankedboolean")) {
            model = new RetrievalModelRankedBoolean();
        } else if (modelString.equals("bm25")) {
            k1 = Double.valueOf(parameters.get("BM25:k_1"));
            b = Double.valueOf(parameters.get("BM25:b"));
            k3 = Double.valueOf(parameters.get("BM25:k_3"));
            model = new RetrievalModelBM25(k1, b, k3);
        } else if (modelString.equals("indri")) {
            mu = Double.valueOf(parameters.get("Indri:mu"));
            lambda = Double.valueOf(parameters.get("Indri:lambda"));
            model = new RetrievalModelIndri(lambda, mu);
        } else if (modelString.equals("letor")) {
            model = new RetrievalModelLetor();
            ((RetrievalModelLetor) model).setParam(parameters);
        } else {
            throw new IllegalArgumentException
                    ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
        }

        return model;
    }

    /**
     * Print a message indicating the amount of memory used. The caller can
     * indicate whether garbage collection should be performed, which slows the
     * program but reduces memory usage.
     *
     * @param gc If true, run the garbage collector before reporting.
     */
    public static void printMemoryUsage(boolean gc) {

        Runtime runtime = Runtime.getRuntime();

        if (gc)
            runtime.gc();

        System.out.println("Memory used:  "
                + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
    }

    /**
     * Process one query.
     *
     * @param qString A string that contains a query.
     * @param model   The retrieval model determines how matching and scoring is done.
     * @return Search results
     * @throws IOException Error accessing the index
     */
    static ScoreList processQuery(String qString, RetrievalModel model)
            throws IOException {

        String defaultOp = model.defaultQrySopName();
        qString = defaultOp + "(" + qString + ")";
        Qry q = QryParser.getQuery(qString);

        // Show the query that is evaluated

        System.out.println("    --> " + q);

        if (q != null) {

            ScoreList r = new ScoreList();

            if (q.args.size() > 0) {        // Ignore empty queries

                q.initialize(model);

                while (q.docIteratorHasMatch(model)) {
                    int docid = q.docIteratorGetMatch();
                    double score = ((QrySop) q).getScore(model);
                    r.add(docid, score);
                    q.docIteratorAdvancePast(docid);
                }
            }

            return r;
        } else
            return null;
    }

    /**
     * Process the query file.
     *
     * @param queryFilePath
     * @param model
     * @throws IOException Error accessing the Lucene index.
     */
    static void processQueryFile(String queryFilePath,
                                 RetrievalModel model,
                                 String exportFilePath,
                                 String maxRankLength,
                                 Map<String, String> parameters)
            throws Exception {

        BufferedReader input = null;

        try {
            String qLine = null;

            input = new BufferedReader(new FileReader(queryFilePath));

            //  Each pass of the loop processes one query.

            //export file as result output file
            PrintWriter writer = new PrintWriter(exportFilePath, "UTF-8");
            writer.println("QueryID Q0 DocID Rank Score RunID");

            //output expendedQuery File
            PrintWriter writer1 = null;
            if (parameters.containsKey("fbExpansionQueryFile")) {
                String outputExpandedQuery = parameters.get("fbExpansionQueryFile");
                writer1 = new PrintWriter(outputExpandedQuery, "UTF-8");
            }

            while ((qLine = input.readLine()) != null) {
                int d = qLine.indexOf(':');

                if (d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Missing ':' in query line.");
                }

                printMemoryUsage(false);

                String qid = qLine.substring(0, d);
                String query = qLine.substring(d + 1);

                System.out.println("Query " + qLine);

                ScoreList r = null;

                //expand query when fb is specify
                if (!parameters.containsKey("fb") || parameters.get("fb").equals("false")) {
                    r = processQuery(query, model);
                } else {

                    //get parameters needed from parameters map for query expansion
                    int fbDocs = Integer.valueOf(parameters.get("fbDocs"));
                    int fbTerms = Integer.valueOf(parameters.get("fbTerms"));
                    int fbMu = Integer.valueOf(parameters.get("fbMu"));
                    double fbOrigWeight = Double.valueOf(parameters.get("fbOrigWeight"));
                    if (parameters.containsKey("fbExpansionQueryFile")) {
                        String fbExpansionQueryFile = parameters.get("fbExpansionQueryFile");
                    }

                    //if the fbInitialRankingFile is being specified
                    if (parameters.containsKey("fbInitialRankingFile")) {
                        //read a document ranking in trec_eval input format from the fbInitialRankingFile;
                        String fbInitialRankingFile = parameters.get("fbInitialRankingFile");
                        BufferedReader br = null;
                        FileReader fr = null;

                        try {
                            fr = new FileReader(fbInitialRankingFile);
                            br = new BufferedReader(fr);
                            String strCurrentLine;
                            r = new ScoreList();
                            while ((strCurrentLine = br.readLine()) != null) {
                                String[] rankRow = strCurrentLine.split(" ");
                                String qid_new = rankRow[0];
                                String external_id = rankRow[2];
                                double score_new = Double.parseDouble(rankRow[4]);
                                //add existing scores into ScoreList based on ranked document
                                if (qid_new.equals(qid)) {
                                    r.add(Idx.getInternalDocid(external_id), score_new);
                                }
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                if (br != null) {
                                    br.close();
                                }
                                if (fr != null) {
                                    fr.close();
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }

                        }

                    } else {
                        //use the original query to retrieve documents
                        r = processQuery(query, model);
                    }

                    //Now we have the ScoreList r, now we start to  expand the query
                    //String newQuery = "#WAND(" + String.valueOf(fbOrigWeight) + " #AND(" + query + ") " + String.valueOf(1 - fbOrigWeight) + " ";

                    String learnedQuery = expandQuery(r, fbDocs, fbMu, fbTerms);
                    System.out.println(learnedQuery);
                    String expandedQuery = "#wand(" + fbOrigWeight + " " + "#and(" + query + ") " + (1 - fbOrigWeight) + " " + learnedQuery + ")";
                    System.out.println(expandedQuery);
                    r = processQuery(expandedQuery, model);
                    writer1.println(qid + ": " + learnedQuery);
                }

                if (r != null) {
                    printResults(qid, r, writer, Integer.valueOf(maxRankLength));
                    System.out.println();
                }
            }
            writer.close();
            if (parameters.containsKey("fbExpansionQueryFile")) {
                writer1.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
        }
    }

    static String expandQuery(ScoreList r, int fbDocs, int fbMu, int fbTerms) throws IOException {

        //sort ScoreList to get the top n documents
        r.sort();

        //Get terms collection
        ArrayList<String> termCollection = new ArrayList<>();
        HashMap<String, Double> ctfCollection = new HashMap<>();
        //loop through documents
        for (int i = 0; i < fbDocs; i++) {
            TermVector termVector = new TermVector(r.getDocid(i), "body");
            int termLength = termVector.stemsLength();
            //loop through terms to collect terms
            for (int t = 1; t < termLength; t++) {
                String term = termVector.stemString(t);
                double ctf = Double.valueOf(termVector.totalStemFreq(t));
                if ((!term.contains(".")) && (!term.contains(","))) {
                    termCollection.add(term);
                    ctfCollection.put(term, ctf);
                }
            }
        }

        //set a new collection for potential query
        HashMap<String, Double> newTermScore = new HashMap<>();
        //Calculate Scores for each terms from term collection
        for (String term : termCollection) {
            double score = 0.0;
            double ctf = ctfCollection.get(term);
            double idf = Math.log(Double.valueOf(Idx.getSumOfFieldLengths("body")) / ctf);

            //loop document to calculate score
            for (int i = 0; i < fbDocs; i++) {
                int docid_new = r.getDocid(i);
                double doc_score = r.getDocidScore(i);
                TermVector termVector = new TermVector(docid_new, "body");

                double tf = 0.0;
                if (termVector.indexOfStem(term) != -1) {
                    tf = (double) termVector.stemFreq(termVector.indexOfStem(term));
                }
                double docLength = (double) termVector.positionsLength();
                double p_MLE = ctf / (double) Idx.getSumOfFieldLengths("body");
                doc_score = doc_score * (tf + (double) fbMu * p_MLE) / (docLength + (double) fbMu);

                score = score + doc_score;
            }

            //save the new query term collection
            //double termScore = (double) Math.round(score * idf * 10000.0) / 10000.0;
            double termScore = score * idf;
            newTermScore.put(term, termScore);
        }

        //sort HashMap new Term Scores by value
        HashMap<String, Double> newTermScore_1 = sortByValue(newTermScore);

        //construct query
        String learnedQuery = "#wand(";
        int count = 1;
        for (Map.Entry<String, Double> entry : newTermScore_1.entrySet()) {
            String queryLine = String.format(" %f %s", (double) Math.round(entry.getValue() * 10000.0) / 10000.0, entry.getKey());
            learnedQuery += queryLine;
            count++;
            if (count > fbTerms) {
                break;
            }
        }
        learnedQuery += ")";
        return learnedQuery;
    }

    // function to sort hashmap by values, from high to low
    static HashMap<String, Double> sortByValue(HashMap<String, Double> hm) {
        // Create a list from elements of HashMap
        List<Map.Entry<String, Double>> list =
                new LinkedList<Map.Entry<String, Double>>(hm.entrySet());

        // Sort the list
        Collections.sort(list, (o1, o2) -> (o2.getValue()).compareTo(o1.getValue()));

        // put data from sorted list to hashmap
        HashMap<String, Double> temp = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }

    /**
     * Print the query results.
     * <p>
     * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
     * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
     * <p>
     * QueryID Q0 DocID Rank Score RunID
     *
     * @param queryName Original query.
     * @param result    A list of document ids and scores
     * @throws IOException Error accessing the Lucene index.
     */
    static void printResults(String queryName, ScoreList result, PrintWriter writer, int maxRankLength) throws IOException {
        result.sort();
        System.out.println(queryName + ":  ");
        if (result.size() < 1) {
            //if there is no result output, record the dummy output
            System.out.println("\tNo results.");
            String exportContent = queryName + " Q0 " + "dummy " + "1 0 " + "run-1";
            System.out.println(exportContent);
            writer.println(exportContent);
        } else {
            for (int i = 0; i < result.size(); i++) {
                if (i < maxRankLength) {
                    //        System.out.println("\t" + i + ":  " + Idx.getExternalDocid(result.getDocid(i)) + ", "
//            + result.getDocidScore(i));
                    String exportContent = queryName + " Q0 " + Idx.getExternalDocid(result.getDocid(i)) + " " +
                            (i + 1) + " " + result.getDocidScore(i) + " run-1";
                    System.out.println(exportContent);
                    writer.println(exportContent);
                }
            }
        }
    }

    /**
     * Read the specified parameter file, and confirm that the required
     * parameters are present.  The parameters are returned in a
     * HashMap.  The caller (or its minions) are responsible for processing
     * them.
     *
     * @return The parameters, in <key, value> format.
     */
    private static Map<String, String> readParameterFile(String parameterFileName)
            throws IOException {

        Map<String, String> parameters = new HashMap<String, String>();

        File parameterFile = new File(parameterFileName);

        if (!parameterFile.canRead()) {
            throw new IllegalArgumentException
                    ("Can't read " + parameterFileName);
        }

        Scanner scan = new Scanner(parameterFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split("=");
            parameters.put(pair[0].trim(), pair[1].trim());
        } while (scan.hasNext());

        scan.close();

        if (!(parameters.containsKey("indexPath") &&
                parameters.containsKey("queryFilePath") &&
                parameters.containsKey("trecEvalOutputPath") &&
                parameters.containsKey("retrievalAlgorithm"))) {
            throw new IllegalArgumentException
                    ("Required parameters were missing from the parameter file.");
        }

        return parameters;
    }

}
