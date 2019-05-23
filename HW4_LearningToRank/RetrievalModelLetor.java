/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * An object that stores parameters for the unranked Boolean
 * retrieval model (there are none) and indicates to the query
 * operators how the query should be evaluated.
 */
public class RetrievalModelLetor extends RetrievalModel {

    public double k1;
    public double b;
    public double k3;
    public double lambda;
    public double mu;
    public double svmRankParamC;


    public String trainingQrelsFile;
    public String trainingQueryFile;
    public String trainingFeatureVectorsFile;
    public String svmRankLearnPath;
    public String svmRankClassifyPath;
    public String svmRankModelFile;
    public String testingFeatureVectorsFile;
    public String testingDocumentScores;
    public String featureDisable;
    public String trecEvalOutputPath;
    public String queryFilePath;
    public String trecEvalOutputLength;
    public String[] featureDisableArray;

    public RetrievalModelBM25 modelBM25;
    public RetrievalModelIndri modelIndri;
    public Feature feature;

    String[] featureArrayIdx = new String[18];
    public HashMap<String, HashMap<Integer, String>> relevance = new HashMap<>();

    //empty constructor
    public RetrievalModelLetor() {
    }

    //set parameters
    public void setParam(Map<String, String> parameters) {
        trainingQrelsFile = parameters.get("letor:trainingQrelsFile");
        trainingQueryFile = parameters.get("letor:trainingQueryFile");
        trainingFeatureVectorsFile = parameters.get("letor:trainingFeatureVectorsFile");
        svmRankLearnPath = parameters.get("letor:svmRankLearnPath");
        svmRankClassifyPath = parameters.get("letor:svmRankClassifyPath");
        svmRankModelFile = parameters.get("letor:svmRankModelFile");
        testingFeatureVectorsFile = parameters.get("letor:testingFeatureVectorsFile");
        testingDocumentScores = parameters.get("letor:testingDocumentScores");
        svmRankParamC = Double.valueOf(parameters.get("letor:svmRankParamC"));
        trecEvalOutputPath = parameters.get("trecEvalOutputPath");
        queryFilePath = parameters.get("queryFilePath");
        trecEvalOutputLength = parameters.get("trecEvalOutputLength");

        for (int i = 1; i <= 18; i++) {
            featureArrayIdx[i - 1] = String.valueOf(i);
        }
        //remove String
        if (parameters.containsKey("letor:featureDisable")) {
            featureDisable = parameters.get("letor:featureDisable").trim();
            featureDisableArray = featureDisable.split(",");
            for (int i = 0; i < featureDisableArray.length; i++) {
                //if we delete feature2, we will delete index 1 in feature list, which is feature2
                int idx = Integer.valueOf(featureDisableArray[i]);
                featureArrayIdx[idx - 1] = "";
            }
        }

        //construct feature object
        feature=new Feature(featureArrayIdx,parameters);

        k1 = Double.valueOf(parameters.get("BM25:k_1"));
        b = Double.valueOf(parameters.get("BM25:b"));
        k3 = Double.valueOf(parameters.get("BM25:k_3"));
        mu = Double.valueOf(parameters.get("Indri:mu"));
        lambda = Double.valueOf(parameters.get("Indri:lambda"));

        modelBM25 = new RetrievalModelBM25(k1, b, k3);
        modelIndri = new RetrievalModelIndri(lambda, mu);
    }

    //this method will go through the process of training
    public void mainTrain(Map<String, String> parameters) throws Exception {
        //generate training data
        System.out.println("read trainingQrelsFile");
        storeRelResult(trainingQrelsFile);

        //deal with features and training query
        System.out.println("Get train query and output feature vector");
        preTrainFeature(trainingQueryFile);

        //Call SVMrank to train a retrieval model;
        System.out.println("SVM train");
        svmLearn(svmRankLearnPath, svmRankParamC);

        //Prepare test data
        preTestFeature(queryFilePath, parameters);

        //Call SVMrank to calculate scores for test documents;
        svmTest(svmRankClassifyPath);

        //Read the scores produced by SVMrank; and
        //Write the final ranking in trec_eval input format
        output();

    }

    //Read relevance judgments from input files;
    public void storeRelResult(String file) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(file));
        HashMap<Integer, String> tmpRelMap = new HashMap<>();
        String line;
        String ex_qid = "-1";

        while ((line = br.readLine()) != null) {
            String[] seperateArray = line.trim().split("\\s+");
            String qid = seperateArray[0].trim();
            String extId = seperateArray[2].trim();
            int docId;
            try {
                docId = Idx.getInternalDocid(extId);
                String relScore = seperateArray[3].trim();

                if ((!qid.equals(ex_qid))) {
                    if ((!ex_qid.equals("-1"))) {
                        relevance.put(ex_qid, tmpRelMap);
                        tmpRelMap = new HashMap<Integer, String>();
                    }
                }
                tmpRelMap.put(docId, relScore);
                ex_qid = qid;
            } catch (Exception e) {
                System.out.println(e);
            }

        }
        relevance.put(ex_qid, tmpRelMap);
        br.close();
    }

    //train Preparation
    public void preTrainFeature(String trainingQueryFile) throws Exception {

        BufferedReader br = new BufferedReader(new FileReader(trainingQueryFile));
        String qline;

        ////Write the feature vectors to a file;
        System.out.println("Output features into trainingFeatureVectorFile");
        //Construct the file
        PrintWriter writer = new PrintWriter(trainingFeatureVectorsFile, "UTF-8");

        //deal with queries
        //each line is one query
        while ((qline = br.readLine()) != null) {
            int d = qline.indexOf(':');

            if (d < 0) {
                throw new IllegalArgumentException
                        ("Syntax error:  Missing ':' in query line.");
            }

            String qid = qline.substring(0, d);
            String query = qline.substring(d + 1);
            String[] terms = QryParser.tokenizeString(query);

            //Calculate feature vectors for training documents;
            //storing information. <docId, <>featureId, featureRelScore>
            HashMap<Integer, HashMap<Integer, Double>> trainFeatures = new HashMap<>();
            //first get a list of relevance document
            HashMap<Integer, String> docRel = relevance.get(qid);
            //compute
            for (Map.Entry<Integer, String> tmpMap : docRel.entrySet()) {
                int docId = tmpMap.getKey();
                //HashMap store score for each feature for a certain document
                HashMap<Integer, Double> featureScore = feature.constructFeatureMap(docId, terms);
                //store the feature score to docid using the existing HashMap
                trainFeatures.put(docId, featureScore);
            }


            //minMax for features
            HashMap<Integer, Double> minMap = new HashMap<>();
            HashMap<Integer, Double> maxMap = new HashMap<>();
            for (int i = 0; i < featureArrayIdx.length; i++) {
                //if the feature is not being disabled
                if (!featureArrayIdx[i].equals("")) {
                    minMap.put(i + 1, Double.MAX_VALUE);
                    maxMap.put(i + 1, Double.MIN_VALUE);
                }
            }
            //compare feature score with the minmaxMap, update minmaxMap
            for (Map.Entry<Integer, HashMap<Integer, Double>> docFeature : trainFeatures.entrySet()) {
                //<featureid, featurescore>
                HashMap<Integer, Double> tmpfeatureScore = docFeature.getValue();
                for (int j = 0; j < featureArrayIdx.length; j++) {
                    String featureid = featureArrayIdx[j];
                    if (featureid.equals("")) continue;
                    int tmpfid = Integer.valueOf(featureid);
                    double min_result = Math.min(minMap.get(tmpfid), tmpfeatureScore.get(tmpfid));
                    double max_result = Math.max(maxMap.get(tmpfid), tmpfeatureScore.get(tmpfid));
                    minMap.put(Integer.valueOf(featureid), min_result);
                    maxMap.put(Integer.valueOf(featureid), max_result);
                }
            }
            HashMap<Integer, Double> range = new HashMap<>();
            for (int j = 0; j < featureArrayIdx.length; j++) {
                String featureid = featureArrayIdx[j];
                if (featureid.equals("")) continue;
                int tmpfid = Integer.valueOf(featureid);
                range.put(Integer.valueOf(featureid), maxMap.get(tmpfid) - minMap.get(tmpfid));
            }
            //normalize
            for (Map.Entry<Integer, HashMap<Integer, Double>> docFeature : trainFeatures.entrySet()) {
                //<featureid, featurescore>
                HashMap<Integer, Double> tmpfeatureScore = docFeature.getValue();
                String line = relevance.get(qid).get(docFeature.getKey()) + " qid:" + qid;

                for (int j = 0; j < featureArrayIdx.length; j++) {
                    String featureid = featureArrayIdx[j];
                    double normalScore = 0.0;
                    if (!featureid.equals("")) {
                        int tmpfid = Integer.valueOf(featureid);
                        if ((range.get(tmpfid) != 0.0)) {
                            normalScore = (tmpfeatureScore.get(tmpfid) - minMap.get(tmpfid)) / range.get(tmpfid);
                        }
                    }
                    line += " " + (j + 1) + ":" + normalScore;
                }
                String outputLine = line + "  # " + Idx.getExternalDocid(docFeature.getKey());
                writer.println(outputLine);
            }
        }
        writer.close();
    }

    //SVM train
    public void svmLearn(String execPath, double FEAT_GEN_c) throws Exception {
        // runs svm_rank_learn from within Java to train the model
        // execPath is the location of the svm_rank_learn utility,
        // which is specified by letor:svmRankLearnPath in the parameter file.
        // FEAT_GEN.c is the value of the letor:c parameter.
        String qrelsFeatureOutputFile = trainingFeatureVectorsFile;
        String modelOutputFile = svmRankModelFile;
        Process cmdProc = Runtime.getRuntime().exec(
                new String[]{execPath, "-c", String.valueOf(FEAT_GEN_c), qrelsFeatureOutputFile,
                        modelOutputFile});

        // The stdout/stderr consuming code MUST be included.
        // It prevents the OS from running out of output buffer space and stalling.

        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            System.out.println(line);
        }

        // get the return value from the executable. 0 means success, non-zero
        // indicates a problem
        int retValue = cmdProc.waitFor();
        if (retValue != 0) {
            throw new Exception("SVM Rank crashed.");
        }
    }

    //testQuery
    public void preTestFeature(String testQueryFile, Map<String, String> parameters) throws Exception {
        System.out.println("Read Query File and run BM25");
        //run BM25 to create an initial ranking (on body field)
        QryEval.processQueryFile(testQueryFile, modelBM25, trecEvalOutputPath, trecEvalOutputLength, parameters);
        storeRelResult(trecEvalOutputPath);

        BufferedReader br = new BufferedReader(new FileReader(testQueryFile));
        String qline;

        ////Write the feature vectors to a file;
        System.out.println("Output features into testingFeatureVectorFile");

        //Construct the file
        PrintWriter writer = new PrintWriter(testingFeatureVectorsFile, "UTF-8");

        //deal with queries
        //each line is one query
        while ((qline = br.readLine()) != null) {
            int d = qline.indexOf(':');

            if (d < 0) {
                throw new IllegalArgumentException
                        ("Syntax error:  Missing ':' in query line.");
            }

            String qid = qline.substring(0, d);
            String query = qline.substring(d + 1);
            String[] terms = QryParser.tokenizeString(query);

            //Calculate feature vectors for training documents;
            //storing information. <docId, <>featureId, featureRelScore>
            HashMap<Integer, HashMap<Integer, Double>> trainFeatures = new HashMap<>();
            //first get a list of relevance document
            HashMap<Integer, String> docRel = relevance.get(qid);
            //compute
            for (Map.Entry<Integer, String> tmpMap : docRel.entrySet()) {
                int docId = tmpMap.getKey();
                //HashMap store score for each feature for a certain document
                HashMap<Integer, Double> featureScore = feature.constructFeatureMap(docId, terms);
                //store the feature score to docid using the existing HashMap
                trainFeatures.put(docId, featureScore);
            }

            //minMax for features
            HashMap<Integer, Double> minMap = new HashMap<>();
            HashMap<Integer, Double> maxMap = new HashMap<>();
            for (int i = 0; i < featureArrayIdx.length; i++) {
                //if the feature is not being disabled
                if (!featureArrayIdx[i].equals("")) {
                    minMap.put(i + 1, Double.MAX_VALUE);
                    maxMap.put(i + 1, Double.MIN_VALUE);
                }
            }
            //compare feature score with the minmaxMap, update minmaxMap
            for (Map.Entry<Integer, HashMap<Integer, Double>> docFeature : trainFeatures.entrySet()) {
                //<featureid, featurescore>
                HashMap<Integer, Double> tmpfeatureScore = docFeature.getValue();
                for (int j = 0; j < featureArrayIdx.length; j++) {
                    String featureid = featureArrayIdx[j];
                    if (featureid.equals("")) continue;
                    int tmpfid = Integer.valueOf(featureid);
                    double min_result = Math.min(minMap.get(tmpfid), tmpfeatureScore.get(tmpfid));
                    double max_result = Math.max(maxMap.get(tmpfid), tmpfeatureScore.get(tmpfid));
                    minMap.put(Integer.valueOf(featureid), min_result);
                    maxMap.put(Integer.valueOf(featureid), max_result);
                }
            }
            HashMap<Integer, Double> range = new HashMap<>();
            for (int j = 0; j < featureArrayIdx.length; j++) {
                String featureid = featureArrayIdx[j];
                if (featureid.equals("")) continue;
                int tmpfid = Integer.valueOf(featureid);
                range.put(Integer.valueOf(featureid), maxMap.get(tmpfid) - minMap.get(tmpfid));
            }
            //normalize
            for (Map.Entry<Integer, HashMap<Integer, Double>> docFeature : trainFeatures.entrySet()) {
                //<featureid, featurescore>
                HashMap<Integer, Double> tmpfeatureScore = docFeature.getValue();
                int docId = docFeature.getKey();
                //String line = relevance.get(qid).get(docId) + " qid:" + qid;
                String line = "0 qid:" + qid;

                for (int j = 0; j < featureArrayIdx.length; j++) {
                    String featureid = featureArrayIdx[j];
                    double normalScore = 0.0;
                    if (!featureid.equals("")) {
                        int tmpfid = Integer.valueOf(featureid);
                        if (range.get(tmpfid) != 0.0) {
                            normalScore = (tmpfeatureScore.get(tmpfid) - minMap.get(tmpfid)) / range.get(tmpfid);
                        }
                    }
                    line += " " + (j + 1) + ":" + normalScore;
                }
                String outputLine = line + "  # " + Idx.getExternalDocid(docId);
                writer.println(outputLine);
            }
        }
        writer.close();
    }

    //SVM test
    public void svmTest(String execPath) throws Exception {
        // runs svm_rank_learn from within Java to learn the model
        // execPath is the location of the svm_rank_learn utility,
        // which is specified by letor:svmRankLearnPath in the parameter file.
        // FEAT_GEN.c is the value of the letor:c parameter.
        String qrelsFeatureOutputFile = testingFeatureVectorsFile;
        String modelOutputFile = svmRankModelFile;
        String predict = testingDocumentScores;
        Process cmdProc = Runtime.getRuntime().exec(
                new String[]{execPath, qrelsFeatureOutputFile,
                        modelOutputFile, predict});

        // The stdout/stderr consuming code MUST be included.
        // It prevents the OS from running out of output buffer space and stalling.

        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            System.out.println(line);
        }

        // get the return value from the executable. 0 means success, non-zero
        // indicates a problem
        int retValue = cmdProc.waitFor();
        if (retValue != 0) {
            throw new Exception("SVM Rank crashed.");
        }
    }

    //test and output rank
    //read in the svmrank scores and re-rank the initial ranking based on the scores
    public void output() throws Exception {
        BufferedReader br_feature = new BufferedReader(new FileReader(testingFeatureVectorsFile));
        BufferedReader br_score = new BufferedReader(new FileReader(testingDocumentScores));
        PrintWriter writer1 = new PrintWriter(new FileWriter(trecEvalOutputPath));

        ScoreList r = new ScoreList();
        String line_feature;
        String line_score;
        String curQid = "-1";

        while ((line_feature = br_feature.readLine()) != null && (line_score = br_score.readLine()) != null) {
            String[] splitArr = line_feature.split(" ");
            String qid = (splitArr[1].trim().split(":"))[1].trim();
            String extId = splitArr[splitArr.length - 1].trim();
            int docId = Idx.getInternalDocid(extId);
            double score = Double.valueOf(line_score.trim());

            //every time when query id change, build a new scorelist and print and expot the current results
            if (!curQid.equals(qid)) {
                r.sort();
                if (!curQid.equals("-1")) {
                    printResults(curQid, r, writer1, Integer.valueOf(trecEvalOutputLength));
                    r = new ScoreList();
                }
                curQid = qid;
            }

            r.add(docId, score);
        }
        r.sort();
        if (r != null) {
            printResults(curQid, r, writer1, Integer.valueOf(trecEvalOutputLength));
        }

        writer1.close();

        br_feature.close();
        br_score.close();

    }

    public void printResults(String queryName, ScoreList result, PrintWriter writer, int maxRankLength) throws IOException {
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
                    String exportContent = queryName + " Q0 " + Idx.getExternalDocid(result.getDocid(i)) + " " +
                            (i + 1) + " " + result.getDocidScore(i) + " run-1";
                    System.out.println(exportContent);
                    writer.println(exportContent);
                }
            }
        }
    }

    public String defaultQrySopName() {
        return new String("#and");
    }

}
