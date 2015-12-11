package picross;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import picross.PicrossSolver.Problem;

/**
 * Solves monochrome picross problems from http://webpbn.com/ , fetching them directly from the AJAX server.
 * Currently only handles small problems. If the algorithm is seeded by some known "hint" cells, it's significantly
 * faster. Cannot solve some of the problems currently.
 */
public class WebPBNProblem {

    /** Solve a problem on http://webpbn.com/ */
    public static Problem loadProblem(int webpbnProbNum) {
        try {
            Pattern countMatcher = Pattern.compile("<count>([0-9]+)</count>");
            StringBuffer result = new StringBuffer();
            URL url = new URL("http://webpbn.com/XMLpuz.cgi?id=" + webpbnProbNum);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            ArrayList<String> rowRuns = new ArrayList<>(), colRuns = new ArrayList<>();
            boolean colLines = false;
            try (BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                for (String line; (line = rd.readLine()) != null;) {
                    if (line.equals("<clues type=\"columns\">")) {
                        colLines = true;
                    } else if (line.equalsIgnoreCase("<clues type=\"rows\">")) {
                        colLines = false;
                    } else if (line.startsWith("<line>")) {
                        StringBuilder run = new StringBuilder();
                        Matcher matcher = countMatcher.matcher(line);
                        while (matcher.find()) {
                            int num = Integer.parseInt(matcher.group(1));
                            if (num <= 0) {
                                throw new RuntimeException("Run length is zero or negative");
                            }
                            // TODO: Should probably encode these using ints, rather than chars
                            if (num < 10) {
                                run.append("" + num);
                            } else {
                                char chr = (char) ('A' + num - 10);
                                // if (chr > 126) {
                                //     throw new RuntimeException("Run length too big");
                                // }
                                run.append(chr);
                            }
                        }
                        (colLines ? colRuns : rowRuns).add(run.toString());
                    }
                    result.append(line);
                }
            }
            String[] rowRunsArr = rowRuns.toArray(new String[rowRuns.size()]);
            String[] colRunsArr = colRuns.toArray(new String[colRuns.size()]);
            int[][] emptyGrid = new int[rowRunsArr.length][colRunsArr.length];
            for (int r = 0; r < rowRunsArr.length; r++) {
                for (int c = 0; c < colRunsArr.length; c++) {
                    emptyGrid[r][c] = -1;
                }
            }
            return new Problem(rowRunsArr, colRunsArr, emptyGrid);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        // N.B. currently, this solver is not able to solve many of the problems in webpbn, because
        // it needs some pre-solved cells to be set in the initial grid to bound the search space.
        // However, here's one that works:
        new PicrossSolver(loadProblem(27));
    }
}
