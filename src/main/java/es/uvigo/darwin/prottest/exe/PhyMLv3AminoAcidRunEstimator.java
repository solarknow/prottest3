package es.uvigo.darwin.prottest.exe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import pal.tree.ReadTree;
import pal.tree.TreeParseException;
import es.uvigo.darwin.prottest.exe.util.TemporaryFileManager;
import es.uvigo.darwin.prottest.global.ApplicationGlobals;
import es.uvigo.darwin.prottest.global.options.ApplicationOptions;
import es.uvigo.darwin.prottest.model.AminoAcidModel;
import es.uvigo.darwin.prottest.model.Model;
import es.uvigo.darwin.prottest.util.Utilities;
import es.uvigo.darwin.prottest.util.exception.ExternalExecutionException;
import es.uvigo.darwin.prottest.util.exception.OSNotSupportedException;
import es.uvigo.darwin.prottest.util.exception.ProtTestInternalException;
import es.uvigo.darwin.prottest.util.exception.StatsFileFormatException;
import es.uvigo.darwin.prottest.util.exception.TreeFormatException;
import es.uvigo.darwin.prottest.util.logging.ProtTestLogger;
import es.uvigo.darwin.prottest.util.printer.ProtTestPrinter;

/**
 * The Class PhyMLAminoAcidRunEstimator. It optimizes Amino-Acid
 * model parameters using PhyML 3.0.
 * 
 * @author Francisco Abascal
 * @author Diego Darriba
 * @since 3.0
 */
public class PhyMLv3AminoAcidRunEstimator extends AminoAcidRunEstimator {

    /** Suffix for temporary statistic files. */
    private static final String STATS_FILE_SUFFIX = "_phyml_stats.txt";
    /** Suffix for temporary tree files. */
    private static final String TREE_FILE_SUFFIX = "_phyml_tree.txt";
    private String workAlignment;

    /**
     * Instantiates a new optimizer for amino-acid models
     * using PhyML.
     * 
     * @param options the application options instance
     * @param model the amino-acid model to optimize
     */
    public PhyMLv3AminoAcidRunEstimator(ApplicationOptions options, Model model) {
        super(options, model);
        this.numberOfCategories = options.ncat;

        try {
            this.model = (AminoAcidModel) model;
        } catch (ClassCastException cce) {
            throw new ProtTestInternalException("Wrong model type");
        }
    }

    /* (non-Javadoc)
     * @see es.uvigo.darwin.prottest.exe.RunEstimator#optimizeModel(es.uvigo.darwin.prottest.global.options.ApplicationOptions)
     */
    public boolean runEstimator()
            throws OSNotSupportedException {
        //let's call Phyml with the proper command line

        this.workAlignment = TemporaryFileManager.getInstance().getAlignmentFilename(Thread.currentThread());

        PrintWriter err = options.getPrinter().getErrorWriter();

        String inv = "0.0";
        if (model.isInv()) {
            inv = "e";
        }

        String rateCathegories = "1";

        String alpha = "";
        if (model.isGamma()) {
            rateCathegories = "" + model.getNumberOfTransitionCategories();//options.ncat;
            alpha = "e";
        }
        String tr = "BIONJ";

        String F = "d";
        if (model.isPlusF()) {
            F = "e";
        }
        String topo = "lr";
        switch (options.strategyMode) {
            case ApplicationGlobals.OPTIMIZE_BIONJ:
                tr = "BIONJ";
                topo = "l";
                break;
            case ApplicationGlobals.OPTIMIZE_FIXED_BIONJ:
                if (TemporaryFileManager.getInstance().getTreeFilename(Thread.currentThread()) != null) {
                    tr = TemporaryFileManager.getInstance().getTreeFilename(Thread.currentThread());
                    topo = "l";
                } else {
                    topo = "r";
                }
                break;
            case ApplicationGlobals.OPTIMIZE_ML:
                topo = "tl";
                break;
            case ApplicationGlobals.OPTIMIZE_USER:
                tr = TemporaryFileManager.getInstance().getTreeFilename(Thread.currentThread());
                topo = "l";
        }
        try {
            Runtime runtime = Runtime.getRuntime();

            String str[] = new String[24];
            if (getPhymlVersion() != null) {
                //     phyml -i seq_file_name -d aa ¿-q? -f d/e (d para -F y e para +F) -v 0/e (para -I o +I) -a e (para estimar alpha)
                //         -c 0/4/8 (num rate categories) -u user_tree_file (opcional)
                //         -o tlr/lr (dependiendo de si optimizamos la topología o no)
                //         -m WAG (default) | JTT | MtREV | Dayhoff | DCMut | RtREV | CpREV | VT | Blosum62 | MtMam | MtArt | HIVw |  HIVb | custom
                //     También hay que modificar el parsing de los ficheros de resultados de phyml.
                //        El likelihood ya no viene en un fichero _phyml_lk.txt, sino en el fichero _phyml_stats.txt
                //        El fichero _phyml_stat.txt ahora se llama _phyml_stats.txt
                //        Respecto al contenido del fichero _phyml_stats.txt, es muy parecido. Comparar los ejemplos:
                //             secuencias_ortho_YLR053C.phy_phyml_stats.txt (NUEVO)
                //             2secuencias_ortho_YLR053C.phy_phyml_stat.txt (ANTIGUO)

                // command arg.
                java.io.File currentDir = new java.io.File("");
                str[0] = currentDir.getAbsolutePath() + "/bin/" + getPhymlVersion();
                str[1] = "";
                str[2] = "";
                str[3] = "";
//				str[0]  = "OMP_NUM_THREADS=4"; 
//				str[3]  = "";
//				str[2]  = "";
//				str[1]  = currentDir.getAbsolutePath() +"/bin/"+getPhymlVersion();
                // input alignment
                str[4] = "-i";
                str[5] = workAlignment;
                //number of rate categories
//				if (model.isGamma()) {
                str[6] = "-c";
                str[7] = rateCathegories;
//				} else
//					str[6] = str[7] = "";
                str[8] = "-m"; //the model
                str[9] = model.getMatrix();
                // proportion of invariable sites
                str[10] = "-v";
                str[11] = inv;
                // value of the gamma shape parameter (if gamma distribution)
                if (!alpha.equals("")) {
                    str[12] = "-a";
                    str[13] = alpha;
                } else {
                    str[12] = str[13] = "";
                }
                // topology optimization
                str[14] = "-o";
                str[15] = topo;
                // amino-acid frequencies
                str[16] = "-f";
                str[17] = F;
                // starting tree file
                if (!tr.equals("BIONJ")) {
                    str[18] = "-u";
                    str[19] = tr;
                } else {
                    str[18] = str[19] = "";
                }
                // data type
                str[20] = "-d";
                str[21] = "aa";
                // bootstrapping
                str[22] = "-b";
                str[23] = "0";

                model.setCommandLine(str);
                proc = runtime.exec(str);
                ExternalExecutionManager.getInstance().addProcess(proc);

            } else {
                OSNotSupportedException e =
                        new OSNotSupportedException();
                throw e;
            }
            proc.getOutputStream().close();
            //open options.log_file
            FileOutputStream tmp = new FileOutputStream(
                    TemporaryFileManager.getInstance().getLogFilename(Thread.currentThread()));
            PrintWriter log = new PrintWriter(tmp, true);
            StreamGobbler errorGobbler = new StreamGobbler(new InputStreamReader(proc.getErrorStream()), "Phyml-Error", true, log, options.getPrinter().getErrorWriter());
            StreamGobbler outputGobbler = new StreamGobbler(new InputStreamReader(proc.getInputStream()), "Phyml-Output", true, log, options.getPrinter().getErrorWriter());
            errorGobbler.start();
            outputGobbler.start();
            int exitVal = proc.waitFor();
            ExternalExecutionManager.getInstance().removeProcess(proc);

            if (options.isDebug()) {
                PrintWriter out = options.getPrinter().getOutputWriter();
                out.print("Phyml's command-line: ");
                for (int i = 0; i < str.length; i++) {
                    out.print(str[i] + " ");
                }
                out.println("");
            }
            if (exitVal != 0) {
                err.println("Phyml's exit value: " + exitVal + " (there was probably some error)");
                err.print("Phyml's command-line: ");
                for (int i = 0; i < str.length; i++) {
                    err.print(str[i] + " ");
                }
                err.println("");

                err.println("Please, take a look at the Phyml's log below:");
                String line;
                try {
                    FileReader input = new FileReader(TemporaryFileManager.getInstance().getLogFilename(Thread.currentThread()));
                    BufferedReader br = new BufferedReader(input);
                    while ((line = br.readLine()) != null) {
                        err.println(line);
                    }
                } catch (IOException e) {
                    err.println("Unable to read the log file: " + TemporaryFileManager.getInstance().getLogFilename(Thread.currentThread()));
                }
            }
        } catch (InterruptedException e) {
            throw new ExternalExecutionException("Interrupted execution: " + e.getMessage());
        } catch (IOException e) {
            throw new ExternalExecutionException("I/O error: " + e.getMessage());
        }

        if (!(readStatsFile(options.getPrinter(), options.isDebug()) && readTreeFile())) {
            return false;
        }

        return true;
    }

    /**
     * Read the temporary statistics file.
     * 
     * @return true, if successful
     */
    private boolean readStatsFile(ProtTestPrinter printer, boolean verbose) {
        String line;
        PrintWriter out = printer.getOutputWriter();
        PrintWriter err = printer.getErrorWriter();
        try {
            FileReader input = new FileReader(workAlignment + STATS_FILE_SUFFIX);
            BufferedReader br = new BufferedReader(input);
            while ((line = br.readLine()) != null) {
                if (verbose) {
                    out.println("[DEBUG] PHYML: " + line);
                }
                if (line.length() > 0) {
                    if (line.startsWith(". Model of amino acids substitution")) {
                        if (!model.getMatrix().equals(Utilities.lastToken(line))) {
                            String errorMsg = "Matrix names doesn't match";
                            err.println("PHYML: " + line);
                            err.println("Last token: " + Utilities.lastToken(line));
                            err.println("It should be: " + model.getMatrix());
                            err.println(errorMsg);
                            throw new StatsFileFormatException(errorMsg);
                        }
                    } else if (line.startsWith(". Log-likelihood")) {
                        model.setLk(Double.parseDouble(Utilities.lastToken(line)));
                    } else if (line.startsWith(". Discrete gamma model")) {
                        if (Utilities.lastToken(line).equals("Yes")) {
                            line = br.readLine();
                            if (verbose) {
                                System.out.println("[DEBUG] PHYML: " + line);
                            }
                            if (model.getNumberOfTransitionCategories() != Integer.parseInt(Utilities.lastToken(line))) {
                                String errorMsg = "There was some error in the number of transition categories: " + model.getNumberOfTransitionCategories() + " vs " + Integer.parseInt(Utilities.lastToken(line));
                                err.println(errorMsg);

                                throw new StatsFileFormatException(errorMsg);
                            //prottest.setCurrentModel(-2);
                            }
                            line = br.readLine();
                            if (verbose) {
                                out.println("[DEBUG] PHYML: " + line);
                            }
                            model.setAlpha(Double.parseDouble(Utilities.lastToken(line)));
                        }
                    } else if (line.startsWith(". Proportion of invariant:")) {
                        model.setInv(Double.parseDouble(Utilities.lastToken(line)));
                    } else if (line.startsWith(". Time used")) {
                        time = Utilities.lastToken(line);
                    }
                }
            }
        } catch (IOException e) {
            throw new StatsFileFormatException(e.getMessage());
        }
        return true;
    }

    /**
     * Read the temporary tree file.
     * 
     * @return true, if successful
     * 
     * @throws TreeFormatException the tree format exception
     */
    private boolean readTreeFile()
            throws TreeFormatException {

        try {
            model.setTree(new ReadTree(workAlignment + TREE_FILE_SUFFIX));
        } catch (TreeParseException e) {
            String errorMsg = "ProtTest: wrong tree format, exiting...";
            throw new TreeFormatException(errorMsg);
        } catch (IOException e) {
            String errorMsg = "Error: File not found (IO error), exiting...";
            throw new TreeFormatException(errorMsg);
        }
        return true;
    }

    /**
     * Delete temporary files.
     * 
     * @return true, if successful
     */
    protected boolean deleteTemporaryFiles() {
        File f;
        f = new File(workAlignment + STATS_FILE_SUFFIX);
        f.delete();
        f = new File(workAlignment + TREE_FILE_SUFFIX);
        f.delete();
        f = new File(TemporaryFileManager.getInstance().getLogFilename(Thread.currentThread()));
        f.delete();
        return true;
    }

    /**
     * Gets the PhyML executable name for the current Operating System.
     * 
     * @return the PhyML executable name
     */
    private String getPhymlVersion() {
        String os = System.getProperty("os.name");
        if (os.startsWith("Mac")) {
            String oa = System.getProperty("os.arch");
            if (oa.startsWith("ppc")) {
                return "phyml-prottest-macppc";
            } else {
                return "phyml-prottest-macintel";
            }
        } else if (os.startsWith("Linux")) {
            return "phyml-prottest-linux";
        } else if (os.startsWith("Window")) {
            return "phyml-prottest-windows.exe";
        } else {
            return null;
        }
    }

    private void print(String message) {
        ProtTestLogger.info(message, this.getClass());
    }

    private void println(String message) {
        ProtTestLogger.info(message + "\n", this.getClass());
    }

    private void fine(String message) {
        ProtTestLogger.fine(message, this.getClass());
    }

    private void fineln(String message) {
        ProtTestLogger.fine(message + "\n", this.getClass());
    }
}
