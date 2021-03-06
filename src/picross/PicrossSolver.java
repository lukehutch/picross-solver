package picross;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Picross puzzle solver, written for GCHQ Director's Christmas puzzle challenge 2015:
 * 
 * http://www.gchq.gov.uk/press_and_media/news_and_features/Pages/Directors-Christmas-puzzle-2015.aspx
 * 
 * Solves the puzzle in about 2 seconds.
 * 
 * @author Luke Hutchison
 * @url https://github.com/lukehutch/picross-solver
 */
public class PicrossSolver {

    static class Problem {
        public final int numRows, numCols;
        public final String[] rowRuns, colRuns;
        public final int[][] initialConstraints;

        public Problem(String[] rowRuns, String[] colRuns, int[][] initialConstraints) {
            this.rowRuns = rowRuns;
            this.colRuns = colRuns;
            this.initialConstraints = initialConstraints;
            this.numRows = rowRuns.length;
            this.numCols = colRuns.length;
            if (initialConstraints.length != numRows || initialConstraints[0].length != numCols) {
                throw new RuntimeException("Wrong dimensions");
            }
        }
    }

    Problem problem;

    private enum Status {
        NOT_FINISHED, FINISHED_VALID, FINISHED_INVALID, COMPLETED;
    };

    private static final long startTime = System.currentTimeMillis();

    private static class Trace {
        String remainingRuns;
        String path;

        public Trace(String run, String path) {
            this.remainingRuns = run;
            this.path = path;
        }

        // Generate a new trace, removing one run from the prev trace 
        public Trace(Trace prev, int color) {
            if (color == 0) {
                this.remainingRuns = prev.remainingRuns.substring(1);
                int runLen = prev.nextRunLen();
                StringBuilder buf = new StringBuilder(runLen + 1);
                buf.append(prev.path);
                for (int i = 0; i < runLen; i++) {
                    buf.append('0');
                }
                if (!remainingRuns.isEmpty()) {
                    buf.append('1');
                }
                this.path = buf.toString();
            } else {
                this.remainingRuns = prev.remainingRuns;
                this.path = prev.path + '1';
            }
        }

        public int nextRunLen() {
            if (remainingRuns.isEmpty()) {
                return 0;
            }
            char c = remainingRuns.charAt(0);
            if (c >= '0' && c <= '9') {
                return (int) (c - '0');
            } else {
                return (int) (c - 'A') + 10;
            }
        }

        @Override
        public String toString() {
            return path + ":" + remainingRuns;
        }
    }

    private Status runOneIter(int[][] currConstraints) {
        ArrayList<ArrayList<ArrayList<Trace>>> hTraces = new ArrayList<>();
        ArrayList<ArrayList<ArrayList<Trace>>> vTraces = new ArrayList<>();
        for (int r = 0; r < problem.numRows + 1; r++) {
            ArrayList<ArrayList<Trace>> hRow = new ArrayList<>();
            hTraces.add(hRow);
            ArrayList<ArrayList<Trace>> vRow = new ArrayList<>();
            vTraces.add(vRow);
            for (int c = 0; c < problem.numCols + 1; c++) {
                hRow.add(new ArrayList<>());
                vRow.add(new ArrayList<>());
            }
        }

        for (int r = 0; r < problem.numRows; r++) {
            hTraces.get(r).get(0).add(new Trace(problem.rowRuns[r], ""));
        }
        for (int c = 0; c < problem.numCols; c++) {
            vTraces.get(0).get(c).add(new Trace(problem.colRuns[c], ""));
        }

        for (int r = 0; r <= problem.numRows; r++) {
            for (int c = 0; c < problem.numCols; c++) {
                boolean notDefinitelyBlack = r == problem.numRows || c == problem.numCols
                        || currConstraints[r][c] != 0;
                if (notDefinitelyBlack) {
                    // If this might be white, or is white, all gaps can be extended to the right and down,
                    // as long as we haven't run out of possibilities in one of the two dimensions already.
                    ArrayList<Trace> hts = hTraces.get(r).get(c);
                    ArrayList<Trace> vts = vTraces.get(r).get(c);
                    if (c < problem.numCols) {
                        ArrayList<Trace> htsRight = hTraces.get(r).get(c + 1);
                        for (Trace ht : hts) {
                            htsRight.add(new Trace(ht, 1));
                        }
                    }
                    if (r < problem.numRows) {
                        ArrayList<Trace> vtsDown = vTraces.get(r + 1).get(c);
                        for (Trace vt : vts) {
                            vtsDown.add(new Trace(vt, 1));
                        }
                    }
                }
                boolean notDefinitelyWhite = r < problem.numRows && c < problem.numCols
                        && currConstraints[r][c] != 1;
                if (notDefinitelyWhite) {
                    // If this can be black (or must be black), we can start a new run here, as long as
                    // the position immediately before or after the run is not definitely black.
                    for (Trace ht : hTraces.get(r).get(c)) {
                        int runLen = ht.nextRunLen();
                        if (runLen > 0) {
                            int runEnd = c + runLen;
                            if ((c == 0 || currConstraints[r][c - 1] != 0)
                                    && (runEnd == problem.numCols || (runEnd < problem.numCols && currConstraints[r][runEnd] != 0))) {
                                // Start a new run
                                int storePos = ht.remainingRuns.length() > 1 ? runEnd + 1 : runEnd;
                                if (storePos <= problem.numCols) {
                                    hTraces.get(r).get(storePos).add(new Trace(ht, 0));
                                }
                            }
                        }
                    }
                    for (Trace vt : vTraces.get(r).get(c)) {
                        int runLen = vt.nextRunLen();
                        if (runLen > 0) {
                            int runEnd = r + runLen;
                            if ((r == 0 || currConstraints[r - 1][c] != 0)
                                    && (runEnd == problem.numRows || (runEnd < problem.numRows && currConstraints[runEnd][c] != 0))) {
                                // Start a new run
                                int storePos = vt.remainingRuns.length() > 1 ? runEnd + 1 : runEnd;
                                if (storePos <= problem.numRows) {
                                    vTraces.get(storePos).get(c).add(new Trace(vt, 0));
                                }
                            }
                        }
                    }
                }
            }
        }
        // Check completed row and column traces for positions with the same coloring in all traces,
        // and use these to resolve ambiguous colors  
        int numResolved = 0, numUnresolved = 0;
        int numValidRows = 0;
        for (int r = 0; r < problem.numRows; r++) {
            List<Trace> finishedTraces = hTraces.get(r).get(problem.numCols).stream()
                    .filter(t -> t.remainingRuns.isEmpty()).collect(Collectors.toList());
            if (!finishedTraces.isEmpty()) {
                numValidRows++;
            } else {
                System.out.println("Empty row: " + r);
            }
            int[] numWhite = new int[problem.numCols], numBlack = new int[problem.numCols];
            for (Trace t : finishedTraces) {
                if (t.remainingRuns.isEmpty()) {
                    for (int c = 0; c < problem.numCols; c++) {
                        char color = t.path.charAt(c);
                        if (color == '0') {
                            numBlack[c]++;
                        } else {
                            numWhite[c]++;
                        }
                    }
                }
            }
            for (int c = 0; c < problem.numCols; c++) {
                if (numBlack[c] == 0 && numWhite[c] > 0) {
                    if (currConstraints[r][c] == -1) {
                        numResolved++;
                    }
                    if (currConstraints[r][c] == 0) {
                        System.out.println("Row " + r + " : definite white; conflict in position " + c + ": "
                                + finishedTraces);
                    }
                    currConstraints[r][c] = 1;
                } else if (numWhite[c] == 0 && numBlack[c] > 0) {
                    if (currConstraints[r][c] == -1) {
                        numResolved++;
                    }
                    if (currConstraints[r][c] == 1) {
                        System.out.println("Row " + r + " : definite black; conflict in position " + c + ": "
                                + finishedTraces);
                    }
                    currConstraints[r][c] = 0;
                }
            }
        }
        int numValidColumns = 0;
        for (int c = 0; c < problem.numCols; c++) {
            List<Trace> finishedTraces = vTraces.get(problem.numRows).get(c).stream()
                    .filter(t -> t.remainingRuns.isEmpty()).collect(Collectors.toList());
            if (!finishedTraces.isEmpty()) {
                numValidColumns++;
            } else {
                System.out.println("Empty column: " + c);
            }
            int[] numWhite = new int[problem.numRows], numBlack = new int[problem.numRows];
            for (Trace t : finishedTraces) {
                if (t.remainingRuns.isEmpty()) {
                    for (int r = 0; r < problem.numRows; r++) {
                        char color = t.path.charAt(r);
                        if (color == '0') {
                            numBlack[r]++;
                        } else {
                            numWhite[r]++;
                        }
                    }
                }
            }
            for (int r = 0; r < problem.numRows; r++) {
                if (numBlack[r] == 0 && numWhite[r] > 0) {
                    if (currConstraints[r][c] == -1) {
                        numResolved++;
                    }
                    if (currConstraints[r][c] == 0) {
                        System.out.println("Column " + c + " : definite white; conflict in position " + r + ": "
                                + finishedTraces);
                    }
                    currConstraints[r][c] = 1;
                } else if (numWhite[r] == 0 && numBlack[r] > 0) {
                    if (currConstraints[r][c] == -1) {
                        numResolved++;
                    }
                    if (currConstraints[r][c] == 1) {
                        System.out.println("Column " + c + " : definite black; conflict in position " + r + ": "
                                + finishedTraces);
                    }
                    currConstraints[r][c] = 0;
                }
                if (currConstraints[r][c] == -1) {
                    numUnresolved++;
                }
            }
        }
        // Print current state of board
        for (int r = 0; r < problem.numRows; r++) {
            for (int c = 0; c < problem.numCols; c++) {
                int constraint = currConstraints[r][c];
                char color = constraint == -1 ? '?' : constraint == 0 ? '#' : ' ';
                System.out.print(color);
            }
            System.out.println();
        }
        System.out.println(numResolved + " resolved; " + numUnresolved + " unresolved; num valid columns: "
                + numValidColumns + "; num valid rows: " + numValidRows);

        return numResolved > 0 ? Status.NOT_FINISHED : numValidRows == problem.numRows
                && numValidColumns == problem.numCols ? (numUnresolved == 0 ? Status.COMPLETED
                : Status.FINISHED_VALID) : Status.FINISHED_INVALID;
    }

    private Status runAllIters(int[][] currConstraints) {
        Status status = Status.NOT_FINISHED;
        for (int iter = 0; status == Status.NOT_FINISHED; iter++) {
            System.out.println("\nIter: " + iter);
            status = runOneIter(currConstraints);
        }
        return status;
    }

    private static int[][] dup(int[][] arr) {
        int[][] arrCopy = new int[arr.length][arr[0].length];
        for (int r = 0; r < arr.length; r++) {
            for (int c = 0; c < arr[0].length; c++) {
                arrCopy[r][c] = arr[r][c];
            }
        }
        return arrCopy;
    }

    private void recurse(int[][] currConstraints) {
        for (int r = 0; r < problem.numRows; r++) {
            for (int c = 0; c < problem.numCols; c++) {
                // For all remaining ambiguous entries 
                if (currConstraints[r][c] == -1) {
                    // Try substituting black for the ambiguous entry					
                    System.out.println("\nTrying black");
                    int[][] constraintsBlack = dup(currConstraints);
                    constraintsBlack[r][c] = 0;
                    Status blackStatus = runAllIters(constraintsBlack);
                    if (blackStatus == Status.COMPLETED) {
                        System.out.println("Solution found -- took " + (System.currentTimeMillis() - startTime)
                                + " msec");
                        System.exit(0);
                    }

                    // Try substituting white for the ambiguous entry					
                    System.out.println("\nTrying white");
                    int[][] constraintsWhite = dup(currConstraints);
                    constraintsWhite[r][c] = 1;
                    Status whiteStatus = runAllIters(constraintsWhite);
                    if (whiteStatus == Status.COMPLETED) {
                        System.out.println("Solution found -- took " + (System.currentTimeMillis() - startTime)
                                + " msec");
                        System.exit(0);
                    }

                    // No solution found -- if one of the colors resulted in a reduction in ambiguity,
                    // recursively search that branch of the solution space 
                    if (blackStatus == Status.FINISHED_VALID && whiteStatus == Status.FINISHED_INVALID) {
                        System.out.println("\nFilling in black at " + r + "," + c);
                        recurse(constraintsBlack);
                    } else if (blackStatus == Status.FINISHED_INVALID && whiteStatus == Status.FINISHED_VALID) {
                        System.out.println("\nFilling in white at " + r + "," + c);
                        recurse(constraintsWhite);
                    }
                }
            }
        }
    }

    public PicrossSolver(Problem problem) {
        this.problem = problem;

        // Iterate until no more constraints can be resolved
        int[][] currConstraints = dup(problem.initialConstraints);
        Status initialStatus = runAllIters(currConstraints);
        if (initialStatus != Status.FINISHED_VALID) {
            throw new RuntimeException("Failed");
        }
        recurse(currConstraints);
        System.out.println("No solution found -- took " + (System.currentTimeMillis() - startTime) + " msec");
    }
}
