/*
    TunPRF2.java for NADI 2022
    Copyright 2022 The University of Helsinki
	Copyright 2022 Heidi Jauhiainen
	Copyright 2021 Tommi Jauhiainen

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

import java.io.*;
import java.util.*;
import java.lang.Math.*;
//import java.text.DecimalFormat;

class TunPRF2 {

// global table holding the language models for all the languages

	private static TreeMap<String, TreeMap<String, Float>> gramDictCap;
	private static TreeMap<String, TreeMap<Integer, Float>> typeAmounts;
	private static TreeMap<Integer, TreeMap<Integer, TreeMap<Float, Float>>> forkingResultTable;
	private static TreeMap<Integer, TreeMap<Integer, ArrayList<Float>>> forkingTodoTable;
//	private static DecimalFormat df;
    
    private static Map<String, String> blacklist;
	private static Float tenbestTotal;
	
	private static boolean onlyAlphabetic = false;
	
	private static String testIdentifier = ".nb2-20220908-50";
	private static String printDevelopmentResultsFile = "NADI.dev.labels" + testIdentifier;
	private static String printTestResultsFile = "NADI.test.labels" + testIdentifier;
	private static String printDevelopmentStatisticsFile = "NADI.dev.statistics" + testIdentifier;
	private static boolean printingDevelopmentResults = false;
	private static boolean identifyFinalMysteryText = true;

	public static void main(String[] args) {
	
//		df 0 new DecimalFormat("###.#");
		
		String trainFile = args[0];
		String developmentFile = args[1];
		String testFile = null;
		if (identifyFinalMysteryText) {
			testFile = args[2];
		}
	
		File file = new File(printDevelopmentResultsFile);
		if (file.exists()) {
			System.out.println(file);
			System.out.println("The specified outfile already exists, define a new file.");
			System.exit(0);
		}
		
		// reading the development file to memory
		
		ArrayList<String> developmentText = new ArrayList<>();
		BufferedReader reader = null;
		
		if (!identifyFinalMysteryText) {
			file = new File(developmentFile);
			try {
				reader = new BufferedReader(new FileReader(file));
				String line = "";
				while ((line = reader.readLine()) != null) {
					developmentText.add(line);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if (reader != null) {
						reader.close();
					}
				} catch (IOException e) {
				}
			}
		}
		
		ArrayList<String> testText = new ArrayList<>();
		
		if (identifyFinalMysteryText) {
			reader = null;
			file = new File(testFile);
			try {
				reader = new BufferedReader(new FileReader(file));
				String line = "";
				while ((line = reader.readLine()) != null) {
					testText.add(line);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if (reader != null) {
						reader.close();
					}
				} catch (IOException e) {
				}
			}
		}
         
		gramDictCap = new TreeMap<>();
		typeAmounts = new TreeMap<>();
		forkingResultTable = new TreeMap<>();
		
		TreeMap<Integer, String> tenbest;
		
        blacklist = new LinkedHashMap<String, String>();
		
		List<String> languageList = new ArrayList<String>();

// setting initial n-gram ranges
		
		int minCharNgram = 2;
		int maxCharNgram = 5;
		float smooth = (float) 1.5;

		if (!identifyFinalMysteryText) {
// setting initial parameter combinations for be tested first
		
			forkingTodoTable = new TreeMap<>();
			for (int k = 2 ; k <= 5 ; k++) {
				for (int i = 2 ; i <= 5 ; i++) {
					for (float j = (float) 1.0 ; j <= 2.5 ; j+=0.5) {
						if (k <= i) {
							forkingTodoTable = addToForkingTodoTable(forkingTodoTable, k, i, j);
						}
					}
				}
			}
		}
		if (identifyFinalMysteryText) {
			minCharNgram = 1;
			maxCharNgram = 4;
			smooth = (float) 1.4375;
		}
		
// creating initial character ngram models
		
		System.out.println("Next: creating character ngram models from " + minCharNgram + " to " + maxCharNgram + ".");
		System.out.println("Using file " + trainFile + " as training material.");
		
		languageList = createModels(trainFile,minCharNgram,maxCharNgram,onlyAlphabetic);
		
		System.out.println("Models created from " + minCharNgram + " to " + maxCharNgram);

		if (!identifyFinalMysteryText) {
			file = new File(developmentFile);
			
			processTodoTable(developmentText, languageList,forkingTodoTable);

	// find ten best results from all the evaluations so far
			
			tenbest = new TreeMap<>();
			tenbest = findTenBest(tenbest);
			
			printTenBest(tenbest);
			calculateTenBestTotal(tenbest);
					
			createNewTodoTable(tenbest,trainFile,languageList,minCharNgram,maxCharNgram);
			
			printTodoTable();

			processTodoTable(developmentText, languageList,forkingTodoTable);
			tenbest = new TreeMap<>();
			tenbest = findTenBest(tenbest);
			printTenBest(tenbest);
			calculateTenBestTotal(tenbest);
			
			Float oldTenbestTotal = (float)0;
			while (oldTenbestTotal < tenbestTotal) {
				oldTenbestTotal = tenbestTotal;
				createNewTodoTable(tenbest,trainFile,languageList,minCharNgram,maxCharNgram);
				printTodoTable();
				processTodoTable(developmentText, languageList,forkingTodoTable);
				tenbest = new TreeMap<>();
				tenbest = findTenBest(tenbest);
				printTenBest(tenbest);
				calculateTenBestTotal(tenbest);
			}
			int x = Integer.parseInt(tenbest.get(1).split(",")[1]);
			int y = Integer.parseInt(tenbest.get(1).split(",")[2]);
			smooth = Float.parseFloat(tenbest.get(1).split(",")[3]);
			printingDevelopmentResults = true;
			evaluateText(developmentText, languageList, x, y, smooth, onlyAlphabetic);
		}
		if (identifyFinalMysteryText) {
			evaluateFinal(testText, languageList, minCharNgram, maxCharNgram, smooth, onlyAlphabetic);
		}
	}
	
	private static void printTodoTable() {
		System.out.println("Printing forkingTodoTable:");
		
		for (Map.Entry<Integer, TreeMap<Integer, ArrayList<Float>>> entry : forkingTodoTable.entrySet()) {
			TreeMap<Integer, ArrayList<Float>> mxpms = new TreeMap<>();
			mxpms = entry.getValue();
			for (Map.Entry<Integer, ArrayList<Float>> entry2 : mxpms.entrySet()) {
				for (Float smooth : entry2.getValue()) {
					System.out.println("minCharNgram = " + entry.getKey() + ", maxCharNgram = " + entry2.getKey() + ", smooth = " +  smooth);
				}
			}
		}
	}
	
	private static void createNewTodoTable(TreeMap<Integer, String> tenbest, String trainFile, List<String> languageList, int minCharNgram, int maxCharNgram) {
		System.out.println("Starting to create new todo-table.");
	
		forkingTodoTable = new TreeMap<>();
		
		for (Map.Entry<Integer, String> entry : tenbest.entrySet()) {
			if (entry.getKey() < 11) {
				
				int x = Integer.parseInt(entry.getValue().split(",")[1]);
				int y = Integer.parseInt(entry.getValue().split(",")[2]);
				float smooth = Float.parseFloat(entry.getValue().split(",")[3]);
				
				System.out.println("Checking minCharNgram = " + x + ", maxCharNgram = " + y + ", smooth = " +  smooth);
				
	// Check the minCharNgram ranges
				
				TreeMap<Integer, ArrayList<Float>> mxpms = new TreeMap<>();
				ArrayList<Float> pms = new ArrayList<Float>();
				pms.add(smooth);
				mxpms.put(y,pms);
				
				if (x>1 && !forkingResultTable.containsKey(x-1)) {
					forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x-1, y, smooth);
					if (x-1 < minCharNgram) {
						minCharNgram = x-1;
						System.out.println("Next: creating models for " + minCharNgram);
						languageList = createModels(trainFile,minCharNgram,minCharNgram,onlyAlphabetic);
					}
				}
				else if (x>1 && !forkingResultTable.get(x-1).containsKey(y)) {
					forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x-1, y, smooth);
				}
				else if (x>1 && !forkingResultTable.get(x-1).get(y).containsKey(smooth)) {
					forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x-1, y, smooth);
				}
				
				if (x<y && !forkingResultTable.containsKey(x+1)) {
					forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x-1, y, smooth);
				}
				else if (x<y && !forkingResultTable.get(x+1).containsKey(y)) {
					forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x-1, y, smooth);
				}
				else if (x<y && !forkingResultTable.get(x+1).get(y).containsKey(smooth)) {
					forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x-1, y, smooth);
				}
				
	// Check the maxCharNgram ranges
				
				if (y>x && !forkingResultTable.get(x).containsKey(y-1)) {
					forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x, y-1, smooth);
				}
				else if (y>x && !forkingResultTable.get(x).get(y-1).containsKey(smooth)) {
					forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x, y-1, smooth);
				}
				
				if (!forkingResultTable.get(x).containsKey(y+1)) {
					forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x, y+1, smooth);
					if (y+1 > maxCharNgram) {
						maxCharNgram = y+1;
						System.out.println("Next: creating models for " + maxCharNgram);
						languageList = createModels(trainFile,maxCharNgram,maxCharNgram,onlyAlphabetic);
					}
				}
				else if (!forkingResultTable.get(x).get(y+1).containsKey(smooth)) {
					forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x, y+1, smooth);
				}

	// Check the smoothing values
				
				pms = new ArrayList<Float>();
				
	//				for (Float smooth : forkingResultTable.get(x).get(y).getKey()) {
				
				float nextsmallest = (float) 0;
				float nextbiggest = (float) 100;
				
				for (Map.Entry<Float, Float> entry4 : forkingResultTable.get(x).get(y).entrySet()) {
					if (entry4.getKey() < smooth && entry4.getKey() > nextsmallest) {
						nextsmallest = entry4.getKey();
					}
					if (entry4.getKey() > smooth && entry4.getKey() < nextbiggest) {
						nextbiggest = entry4.getKey();
					}
				}
				
				if (nextsmallest == 0) {
					if (smooth - 0.5 > 0) {
						forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x, y, smooth - (float) 0.5);
					}
				}
				else {
					if ((smooth - nextsmallest) > 0.1) {
						forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x, y, (smooth+nextsmallest)/2);
					}
				}
				
				if (nextbiggest == 100) {
					if (smooth + 0.5 < 10) {
						forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x, y, smooth + (float) 0.5);
					}
				}
				else {
					if ((nextbiggest - smooth) > 0.1) {
						forkingTodoTable = addToForkingTodoTable(forkingTodoTable, x, y, (smooth+nextbiggest)/2);
					}
				}
			}
			else {
				break;
			}
		}
	}
	
	private static void printTenBest(TreeMap<Integer, String> tenbest) {
		System.out.println("Printing the ten best results so far.");
		
		for (Map.Entry<Integer, String> entry : tenbest.entrySet()) {
			if (entry.getKey() < 11) {
				System.out.println(entry.getKey() + " " + entry.getValue());
			}
			else {
				break;
			}
		}
	}
	
	private static void calculateTenBestTotal(TreeMap<Integer, String> tenbest) {
		
		tenbestTotal = (float) 0;
		for (Map.Entry<Integer, String> entry : tenbest.entrySet()) {
			if (entry.getKey() < 11) {
				tenbestTotal = tenbestTotal + Float.parseFloat(entry.getValue().split(",")[0]);
			}
			else {
				break;
			}
		}
		System.out.println("The total sum of f-scores of the ten best results so far are: " + tenbestTotal);
	}
	
	private static TreeMap<Integer, String> findTenBest(TreeMap<Integer, String> tenbest) {
		for (Map.Entry<Integer, TreeMap<Integer, TreeMap<Float, Float>>> entry : forkingResultTable.entrySet()) {
			TreeMap<Float, Float> pmsc = new TreeMap<>();
			TreeMap<Integer, TreeMap<Float, Float>> mxpmsc = new TreeMap<>();
			mxpmsc = forkingResultTable.get(entry.getKey());
			for (Map.Entry<Integer, TreeMap<Float, Float>> entry2 : mxpmsc.entrySet()) {
				pmsc = mxpmsc.get(entry2.getKey());
				for (Map.Entry<Float, Float> entry3 : pmsc.entrySet()) {
					for (int top = 10 ; top >= 1; top--) {
						if (tenbest.containsKey(top)) {
							if (pmsc.get(entry3.getKey()) > Float.parseFloat(tenbest.get(top).split(",")[0])) {
								tenbest.put(top+1,tenbest.get(top));
							}
							else {
								String result = pmsc.get(entry3.getKey()) + "," + entry.getKey() + "," + entry2.getKey() + "," + entry3.getKey();
								tenbest.put(top+1,result);
								break;
							}
							if (top == 1) {
								String result = pmsc.get(entry3.getKey()) + "," + entry.getKey() + "," + entry2.getKey() + "," + entry3.getKey();
								tenbest.put(top,result);
							}
						}
						else {
							if (top == 1) {
								String result = pmsc.get(entry3.getKey()) + "," + entry.getKey() + "," + entry2.getKey() + "," + entry3.getKey();
								tenbest.put(top,result);
							}
						}
					}
					System.out.println("minCharNgram = " + entry.getKey() + ", maxCharNgram = " + entry2.getKey() + ", smooth = " +  entry3.getKey() + ", macro F1 = " + pmsc.get(entry3.getKey()));
				}
			}
		}
		return tenbest;
	}
	
	private static void processTodoTable(ArrayList<String> textToBeEvaluated, List<String> languageList, TreeMap<Integer, TreeMap<Integer, ArrayList<Float>>> forkingTodoTable) {
		for (Map.Entry<Integer, TreeMap<Integer, ArrayList<Float>>> entry : forkingTodoTable.entrySet()) {
			TreeMap<Integer, ArrayList<Float>> mxpms = new TreeMap<>();
			mxpms = entry.getValue();
			for (Map.Entry<Integer, ArrayList<Float>> entry2 : mxpms.entrySet()) {
				for (Float smooth : entry2.getValue()) {
					int x = entry.getKey();
					int y = entry2.getKey();
					System.out.println("Evaluating: minCharNgram = " + x + ", maxCharNgram = " + y + ", smooth = " +  smooth);
					evaluateToResultsTable(textToBeEvaluated, languageList, x, y, smooth);
				}
			}
		}
	}
	
	private static TreeMap<Integer, TreeMap<Integer, ArrayList<Float>>> addToForkingTodoTable(TreeMap<Integer, TreeMap<Integer, ArrayList<Float>>> forkingTodoTable, int minCharNgram, int maxCharNgram, float smooth) {

		TreeMap<Integer, ArrayList<Float>> mxpm = new TreeMap<>();
		ArrayList<Float> jlist = new ArrayList<>();
		if (forkingTodoTable.containsKey(minCharNgram)) {
			mxpm = forkingTodoTable.get(minCharNgram);
			if (mxpm.containsKey(maxCharNgram)) {
				jlist = mxpm.get(maxCharNgram);
			}
		}
		jlist.add(smooth);
		mxpm.put(maxCharNgram,jlist);
		forkingTodoTable.put(minCharNgram,mxpm);
 
		return forkingTodoTable;
	}
	
	private static void evaluateToResultsTable(ArrayList<String> textToBeEvaluated, List<String> languageList, int minCharNgram, int maxCharNgram, float smooth) {
		TreeMap<Float, Float> pmsc = new TreeMap<>();
		TreeMap<Integer, TreeMap<Float, Float>> mxpmsc = new TreeMap<>();

		if (forkingResultTable.containsKey(minCharNgram)) {
			mxpmsc = forkingResultTable.get(minCharNgram);
			if (mxpmsc.containsKey(maxCharNgram)) {
				pmsc = mxpmsc.get(maxCharNgram);
				pmsc.put(smooth,evaluateText(textToBeEvaluated,languageList,minCharNgram,maxCharNgram,smooth,onlyAlphabetic));
			}
			else {
				pmsc.put(smooth,evaluateText(textToBeEvaluated,languageList,minCharNgram,maxCharNgram,smooth,onlyAlphabetic));
			}
			mxpmsc.put(maxCharNgram,pmsc);
			 
		}
		else {
			pmsc.put(smooth,evaluateText(textToBeEvaluated,languageList,minCharNgram,maxCharNgram,smooth,onlyAlphabetic));
			mxpmsc.put(maxCharNgram,pmsc);
		}
		forkingResultTable.put(minCharNgram,mxpmsc);
	}

	private static float evaluateText(ArrayList<String> textToBeEvaluated, List<String> languageList, int minCharNgram, int maxCharNgram, double penaltymodifier, boolean onlyAlphabetic) {
		BufferedReader reader = null;
		float macroF1Score = 0;
		try {
//			reader = new BufferedReader(new FileReader(file));
			
//			String line = "";
			
			Map<String, Integer> langCorrect;
			Map<String, Integer> langWrong;
			Map<String, Integer> langShouldBe;

			langCorrect = new LinkedHashMap<String, Integer>();
			langWrong = new LinkedHashMap<String, Integer>();
			langShouldBe = new LinkedHashMap<String, Integer>();

			ListIterator languageiterator = languageList.listIterator();
			while(languageiterator.hasNext()) {
				Object element = languageiterator.next();
				String language = (String) element;
                langShouldBe.put(language,0);
                langCorrect.put(language,0);
                langWrong.put(language,0);
			}
			
			langWrong.put("xxx",0);

			float correct = 0;
			float wrong = 0;
			float total = 0;
			
			int totallinenumber = 0;
            int totallength = 0;
			
			
			File file2 = new File(printDevelopmentResultsFile);
			File file3 = new File(printDevelopmentStatisticsFile);
			BufferedWriter writer = null;
			BufferedWriter writer2 = null;
			
			if (printingDevelopmentResults == true) {
				file2.createNewFile();
				
				try {
					writer = new BufferedWriter(new FileWriter(file2, true));
				}
				catch (Exception e) {
					System.out.println("Error while creating writer: "+e.getMessage());
				}
				
				
				file3.createNewFile();
				
				try {
					writer2 = new BufferedWriter(new FileWriter(file3, true));
				}
				catch (Exception e) {
					System.out.println("Error while creating writer: "+e.getMessage());
				}
			}
			
			for (String line : textToBeEvaluated) {
//			while ((line = reader.readLine()) != null) {
				String mysterytext = line;
				String correctlanguage = line;
				
//				System.out.println(line);
				
				mysterytext = mysterytext.replaceAll(".*\t", "");

				correctlanguage = correctlanguage.replaceAll("\t.*", "");
				correctlanguage = correctlanguage.replaceAll("\n", "");
				correctlanguage = correctlanguage.replaceAll("\\W", "");

// Käyttäen pelkkiä kirjaimia
				if (onlyAlphabetic) {
					mysterytext = mysterytext.replaceAll("[^\\p{L}\\p{M}′'’´ʹािीुूृेैोौंँः् া ি ী ু ূ ৃ ে ৈ ো ৌ।্্্я̄\\u07A6\\u07A7\\u07A8\\u07A9\\u07AA\\u07AB\\u07AC\\u07AD\\u07AE\\u07AF\\u07B0\\u0A81\\u0A82\\u0A83\\u0ABC\\u0ABD\\u0ABE\\u0ABF\\u0AC0\\u0AC1\\u0AC2\\u0AC3\\u0AC4\\u0AC5\\u0AC6\\u0AC7\\u0AC8\\u0AC9\\u0ACA\\u0ACB\\u0ACC\\u0ACD\\u0AD0\\u0AE0\\u0AE1\\u0AE2\\u0AE3\\u0AE4\\u0AE5\\u0AE6\\u0AE7\\u0AE8\\u0AE9\\u0AEA\\u0AEB\\u0AEC\\u0AED\\u0AEE\\u0AEF\\u0AF0\\u0AF1]", " ");
				}
				mysterytext = mysterytext.replaceAll("  *", " ");
				mysterytext = mysterytext.replaceAll("^ ", "");
				mysterytext = mysterytext.replaceAll(" $", "");
//				System.out.println(mysterytext);
				mysterytext = mysterytext.replaceAll("^", " ");
				mysterytext = mysterytext.replaceAll("$", " ");
//				System.out.println(mysterytext);
//				mysterytext = mysterytext.toLowerCase();
                
                int pituus = mysterytext.length();
 //               System.out.println(pituus);
 //               if (pituus > 74) {
 //                   totallength = totallength + pituus;
 //               }
                

                String identifiedLanguage = "xxx";

				identifiedLanguage = identifyTextProdRelFreq(mysterytext,languageList,minCharNgram,maxCharNgram,penaltymodifier);
                
                langShouldBe.put(correctlanguage,langShouldBe.get(correctlanguage)+1);

                total++;
                if (identifiedLanguage.equals(correctlanguage)) {
                    correct++;
                    langCorrect.put(identifiedLanguage,langCorrect.get(identifiedLanguage)+1);
                }
                else {
                    wrong++;
//					System.out.println(identifiedLanguage);
//					System.out.println(mysterytext + "\t" + correctlanguage + "\t" +  identifiedLanguage);
                    langWrong.put(identifiedLanguage,langWrong.get(identifiedLanguage)+1);
                }
                totallinenumber++;
				if (printingDevelopmentResults == true) {
					try {
						writer.write(identifiedLanguage+"\n");
					}
					catch (Exception e) {
						System.out.println("Error while trying to write: "+e.getMessage());
					}
				}
			}
			
			if (printingDevelopmentResults == true) {
				try {
					if (writer != null) {
						writer.close();
					}
				}
				catch (Exception e) {
					System.out.println(e.getMessage());
				}
			}
            
  //          System.out.println("totallength: " + totallength + "\t" + "totallinenumber: " + totallinenumber);

			Float sumFscore = (float)0;
			Float weightedF1Score = (float)0;

			languageiterator = languageList.listIterator();
			int langamount = 0;
			while(languageiterator.hasNext()) {
				langamount++;
				Object element = languageiterator.next();
				String language = (String) element;
//				System.out.println(language);
				Float precision = (float)langCorrect.get(language) / (float)(langCorrect.get(language) + (float)langWrong.get(language));
				if (langWrong.get(language) < 1) {
					precision = (float)1;
				}
				Float recall = (float)langCorrect.get(language) / (float)langShouldBe.get(language);
				Float f1score = (float)0;
				if (precision+recall > 0) {
					f1score = 2*(precision*recall/(precision+recall));
				}
				sumFscore = sumFscore + f1score;
				weightedF1Score = weightedF1Score + f1score * (float)langShouldBe.get(language) / (float)totallinenumber;
// Tällä voi printata ulos kielikohtaiset tulokset.
				if (printingDevelopmentResults == true) {
					try {
						writer2.write("minCharNgram: " + minCharNgram + "\tmaxCharNgram: " + maxCharNgram + "\tIndividual - Language: " + language + "\tRecall: " + recall + "\tPrecision: " + precision + "\tF1-score: " + f1score+"\n");
					}
					catch (Exception e) {
						System.out.println("Error while trying to write: "+e.getMessage());
					}
				}
//				System.out.println("minCharNgram: " + minCharNgram + "\tmaxCharNgram: " + maxCharNgram + "\tIndividual - Language: " + language + "\tRecall: " + recall + "\tPrecision: " + precision + "\tF1-score: " + f1score);
			}
			

			
			macroF1Score = sumFscore / (float)langamount;
		
			Float totalPrecision = correct / (correct + wrong);
			Float totalRecall = correct / (correct + wrong);
			
			Float microF1Score = 2*(totalPrecision*totalRecall/(totalPrecision+totalRecall));
			
			if (printingDevelopmentResults == true) {
				try {
					writer2.write("Penaltymodifier: " + penaltymodifier + "\tminCharNgram: " + minCharNgram + "\tmaxCharNgram: " + maxCharNgram + "\tTotal - MacroF1: " + macroF1Score + "\n");
				}
				catch (Exception e) {
					System.out.println("Error while trying to write: "+e.getMessage());
				}
			}
			
			if (printingDevelopmentResults == true) {

				try {
					if (writer2 != null) {
						writer2.close();
					}
				}
				catch (Exception e) {
					System.out.println(e.getMessage());
				}
			}
			
//			System.out.println(penaltymodifier + " " + total + " " + correct + " " + wrong + " " + 100.0/total*correct + " " + microF1Score + macroF1Score);
//			System.out.println("Penaltymodifier: " + penaltymodifier + "Method: " + method + "\tminCharNgram: " + minCharNgram + "\tmaxCharNgram: " + maxCharNgram + "\tTotal - MacroF1: " + macroF1Score + "\tRecall: " + 100.0/total*correct + "\tMicroF1: " + microF1Score + "\tWeighted F1: " + weightedF1Score);
//			System.out.println("Penaltymodifier: " + penaltymodifier + "Method: " + method + "\tminCharNgram: " + minCharNgram + "\tmaxCharNgram: " + maxCharNgram + "\tTotal - MacroF1: " + macroF1Score);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
			}
		}
		return macroF1Score;
	}

	private static void evaluateFinal(ArrayList<String> textToBeEvaluated, List<String> languageList, int minCharNgram, int maxCharNgram, double penaltymodifier, boolean onlyAlphabetic) {
		BufferedReader reader = null;
		float macroF1Score = 0;
		try {
			int totallinenumber = 0;
			int totallength = 0;
			
			
			File file2 = new File(printTestResultsFile);

			BufferedWriter writer = null;
			
			file2.createNewFile();
			
			try {
				writer = new BufferedWriter(new FileWriter(file2, true));
			}
			catch (Exception e) {
				System.out.println("Error while creating writer: "+e.getMessage());
			}
			
			for (String line : textToBeEvaluated) {
//			while ((line = reader.readLine()) != null) {
				totallinenumber++;
//				System.out.println(totallinenumber);
				String mysterytext = line;

// Käyttäen pelkkiä kirjaimia
				if (onlyAlphabetic) {
					mysterytext = mysterytext.replaceAll("[^\\p{L}\\p{M}′'’´ʹािीुूृेैोौंँः् া ি ী ু ূ ৃ ে ৈ ো ৌ।্্্я̄\\u07A6\\u07A7\\u07A8\\u07A9\\u07AA\\u07AB\\u07AC\\u07AD\\u07AE\\u07AF\\u07B0\\u0A81\\u0A82\\u0A83\\u0ABC\\u0ABD\\u0ABE\\u0ABF\\u0AC0\\u0AC1\\u0AC2\\u0AC3\\u0AC4\\u0AC5\\u0AC6\\u0AC7\\u0AC8\\u0AC9\\u0ACA\\u0ACB\\u0ACC\\u0ACD\\u0AD0\\u0AE0\\u0AE1\\u0AE2\\u0AE3\\u0AE4\\u0AE5\\u0AE6\\u0AE7\\u0AE8\\u0AE9\\u0AEA\\u0AEB\\u0AEC\\u0AED\\u0AEE\\u0AEF\\u0AF0\\u0AF1]", " ");
				}
				mysterytext = mysterytext.replaceAll("  *", " ");
				mysterytext = mysterytext.replaceAll("^ ", "");
				mysterytext = mysterytext.replaceAll(" $", "");
//				System.out.println(mysterytext);
				mysterytext = mysterytext.replaceAll("^", " ");
				mysterytext = mysterytext.replaceAll("$", " ");
//				System.out.println(mysterytext);
//				mysterytext = mysterytext.toLowerCase();
				
				int pituus = mysterytext.length();
 //               System.out.println(pituus);
 //               if (pituus > 74) {
 //                   totallength = totallength + pituus;
 //               }
				
				String identifiedLanguage = "xxx";

				identifiedLanguage = identifyTextProdRelFreq(mysterytext,languageList,minCharNgram,maxCharNgram,penaltymodifier);

				try {
					writer.write(identifiedLanguage+"\n");
				}
				catch (Exception e) {
					System.out.println("Error while trying to write: "+e.getMessage());
				}
			}
			try {
				if (writer != null) {
					writer.close();
				}
			}
			catch (Exception e) {
				System.out.println(e.getMessage());
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
			}
		}
	}


// identifyText
	
	private static String identifyTextProdRelFreq(String mysteryText, List<String> languageList, int minCharNgram, int maxCharNgram, double penaltymodifier) {
		
		Map<String, Double> languagescores = new HashMap();

		ListIterator languageiterator = languageList.listIterator();
		while(languageiterator.hasNext()) {
			Object element = languageiterator.next();
			String kieli = (String) element;
			languagescores.put(kieli, 0.0);
		}

		int t = maxCharNgram;
		int gramamount = 0;

		while (t >= minCharNgram) {
			int pituus = mysteryText.length();
			int x = 0;
			if (pituus > (t-1)) {
				while (x < pituus - t + 1) {
					String gram = mysteryText.substring(x,x+t);
					gramamount = gramamount + 1;
					languageiterator = languageList.listIterator();
					while(languageiterator.hasNext()) {
						Object element = languageiterator.next();
						String kieli = (String) element;
						TreeMap <String, Float> kiepro = new TreeMap<>();
//						System.out.println("1: " + gram + " " + kieli);
						if (gramDictCap.containsKey(gram)) {
							kiepro = gramDictCap.get(gram);
//							System.out.println("2: " + gram + "löytyi");
							if (kiepro.containsKey(kieli)) {
//								System.out.println("3: " + kieli + " " + kiepro.get(kieli));
								double probability = -Math.log10(kiepro.get(kieli) / (typeAmounts.get(kieli).get(t)));
//								System.out.println("3: " + probability + " score oli: " + languagescores.get(kieli));
								languagescores.put(kieli, languagescores.get(kieli) +probability);
//								System.out.println("3:  score on: " + languagescores.get(kieli));
							}
							else {
								double penalty = -Math.log10(1/typeAmounts.get(kieli).get(t))*penaltymodifier;
								languagescores.put(kieli, languagescores.get(kieli) +penalty);
							}
						}
						else {
							double penalty = -Math.log10(1/typeAmounts.get(kieli).get(t))*penaltymodifier;
							languagescores.put(kieli, languagescores.get(kieli) +penalty);
						}
					}
					x = x + 1;
				}
			}
			t = t -1 ;
		}
		
		languageiterator = languageList.listIterator();
		while(languageiterator.hasNext()) {
			Object element = languageiterator.next();
			String language = (String) element;
			languagescores.put(language, (languagescores.get(language)/gramamount));
		}

		Double winningscore = 1000.0;
		String mysterylanguage = "xxx";

		languageiterator = languageList.listIterator();
		while(languageiterator.hasNext()) {
			Object element = languageiterator.next();
			String kieli = (String) element;
			if (languagescores.get(element) < winningscore) {
				winningscore = languagescores.get(element);
				mysterylanguage = kieli;
			}
		}
		return (mysterylanguage);
	}
	
	private static List createModels(String trainFile, int minCharNgram, int maxCharNgram, boolean onlyAlphabetic) {
	
		List<String> languageList = new ArrayList<String>();
	
		File file = new File(trainFile);
		
		int lineNumber = 0;
		int ngramNumber = 0;
		
		System.out.println(typeAmounts);
		
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			
			String line = "";
			String gram = "";
			
			Double aika = (double)System.currentTimeMillis();
			
			while ((line = reader.readLine()) != null) {
				String text = line;
				String language = line;
				
//				System.out.println("Line:" + lineNumber + " ngrams: " + ngramNumber);
				
				text = text.replaceAll(".*\t", "");
				
// Käyttäen pelkkiä kirjaimia
				if (onlyAlphabetic) {
					text = text.replaceAll("[^\\p{L}\\p{M}′'’´ʹािीुूृेैोौंँः् া ি ী ু ূ ৃ ে ৈ ো ৌ।্্্я̄\\u07A6\\u07A7\\u07A8\\u07A9\\u07AA\\u07AB\\u07AC\\u07AD\\u07AE\\u07AF\\u07B0\\u0A81\\u0A82\\u0A83\\u0ABC\\u0ABD\\u0ABE\\u0ABF\\u0AC0\\u0AC1\\u0AC2\\u0AC3\\u0AC4\\u0AC5\\u0AC6\\u0AC7\\u0AC8\\u0AC9\\u0ACA\\u0ACB\\u0ACC\\u0ACD\\u0AD0\\u0AE0\\u0AE1\\u0AE2\\u0AE3\\u0AE4\\u0AE5\\u0AE6\\u0AE7\\u0AE8\\u0AE9\\u0AEA\\u0AEB\\u0AEC\\u0AED\\u0AEE\\u0AEF\\u0AF0\\u0AF1]", " ");
				}
				text = text.replaceAll("  *", " ");
				text = text.replaceAll("^ ", "");
				text = text.replaceAll(" $", "");
//				System.out.println(text);
				text = text.replaceAll("^", " ");
				text = text.replaceAll("$", " ");
//				System.out.println(text);
//				text = text.toLowerCase();
				
				int pituus = text.length();
//				System.out.println("Text length = " + pituus);
				
				language = language.replaceAll("\t.*", "");
				language = language.replaceAll("\n", "");
				language = language.replaceAll("\\W", "");
				
				if (!languageList.contains(language)) {
					
					languageList.add(language);
					int x = maxCharNgram;
					TreeMap <Integer, Float> typam = new TreeMap<>();
					if (typeAmounts.containsKey(language)) {
						typam = typeAmounts.get(language);
					}
					while (x >= minCharNgram) {
						typam.put(x, (float) 0.0);
//						typeAmounts.put(language,x,0.0);
						x--;
					}
					typeAmounts.put(language,typam);
				}
				
				int t = maxCharNgram;
				
				while (t >= minCharNgram) {
					int x = 0;
					int typeAmountCounter = 0;
//					System.out.println(t + " " + text);
					if (pituus > (t-1)) {
						while (x < pituus - t + 1) {
							
							gram = text.substring(x,x+t);
							
//							System.out.println(x + " " + gram);
							
							if (gramDictCap.containsKey(gram)) {
								TreeMap <String, Float> kiepro = new TreeMap<>();
								kiepro = gramDictCap.get(gram);
								if (kiepro.containsKey(language)) {
//									System.out.println(gram + " " + language + " " + kiepro.get(language));
									kiepro.put(language, kiepro.get(language)+1);
//									System.out.println(gram + " " + language + " " + kiepro.get(language));
									gramDictCap.put(gram,kiepro);
								}
								else {
									kiepro.put(language, (float) 1.0);
									gramDictCap.put(gram, kiepro);
	//								System.out.println(gram + " " + language + " " + kiepro.get(language));
									ngramNumber++;

								}
							}
							else {
								TreeMap <String, Float> kiepro = new TreeMap<>();
								kiepro.put(language, (float) 1.0);
								gramDictCap.put(gram, kiepro);
//								System.out.println(gram + " " + language + " " + kiepro.get(language));
								ngramNumber++;

							}
							typeAmountCounter++;
							
							x = x + 1;
						}
					}
					TreeMap <Integer, Float> typam = new TreeMap<>();
					typam = typeAmounts.get(language);
					typam.put(t,typam.get(t)+typeAmountCounter);
					typeAmounts.put(language,typam);
					t = t -1 ;
				}
				lineNumber ++;
			}
			
			System.out.println(typeAmounts);
			
			Double aika2 = (double)System.currentTimeMillis();
//			System.out.println(aika2-aika);
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
			}
		}
		return (languageList);
	}
    
    private static void readBlacklist(String blacklistFile) {
        File file = new File(blacklistFile);
        
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            
            String line = "";
            String gram = "";

            while ((line = reader.readLine()) != null) {
                String text = line;
                String language = line;
                
                language = language.replaceAll("\t.*", "");
                text = text.replaceAll(".*\t", "");
                text = text.replaceAll("\n", "");
                
                blacklist.put(text,language);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
            }
        }
    }
    
    private static String useBlacklist(String mysterytext) {
        for (Map.Entry<String, String> entry : blacklist.entrySet()) {
            String key = entry.getKey();
//            System.out.println(key);
            if (mysterytext.contains(key)) {
//                System.out.println(key);
//                System.out.println(mysterytext);
                return blacklist.get(key);
            }
        }
        return "xxx";
    }
}
