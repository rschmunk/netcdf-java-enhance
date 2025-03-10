package ucar.unidata.util.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;
import ucar.nc2.util.AliasTranslator;
import ucar.unidata.io.RandomAccessFile;
import ucar.unidata.util.StringUtil2;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.*;

/**
 * Manage the test data directories and servers.
 *
 * @author caron
 * @since 3/23/12
 *
 *        <p>
 *        <table>
 *        <tr>
 *        <th colspan="3">-D Property Names
 *        <tr>
 *        <th>Static Variable
 *        <th>Property Name(s)
 *        <th>Description
 *        <tr>
 *        <td>testdataDirPropName
 *        <td>unidata.testdata.path
 *        <td>Property name for the path to the Unidata test data directory,
 *        e.g unidata.testdata.path=/share/testdata
 *        </table>
 *        <p>
 *        <table>
 *        <tr>
 *        <th colspan="4">Computed Paths
 *        <tr>
 *        <th>Static Variable
 *        <th>Property Name(s) (-d)
 *        <th>Default Value
 *        <th>Description
 *        <tr>
 *        <td>cdmUnitTestDir
 *        <td>NA
 *        <td>NA
 *        <td>New test data directory. Do not put temporary files in here.
 *        Migrate all test data here eventually.
 *        <tr>
 *        <td>cdmLocalTestDataDir
 *        <td>NA
 *        <td>../cdm/src/test/data
 *        <td>Level 1 test data directory (distributed with code and MAY be used in Unidata nightly testing).
 *        </table>
 *
 */
public class TestDir {
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** Property name for the path to the Unidata test data directory, e.g "unidata.testdata.path=/share/testdata". */
  private static String testdataDirPropName = "unidata.testdata.path";

  /** Path to the Unidata test data directory. */
  public static String testdataDir;

  /**
   * New test data directory. do not put temporary files in here. migrate all test data here eventually
   * Unidata "//fileserver/data/testdata2/cdmUnitTest" directory.
   */
  public static String cdmUnitTestDir;

  /** The cdm-core local test directory, from any cdm submodule. */
  public static String cdmLocalTestDataDir = "../core/src/test/data/";

  /** The module local test directory. Assumes pwd = top of module. */
  public static String localTestDataDir = "src/test/data/";

  /** The cdm-core local test directory, from cdm-test submodule. */
  public static String cdmLocalFromTestDataDir = "../cdm/core/src/test/data/";

  /** cdm-test data directory (distributed with code but depends on data not in github) */
  public static String cdmTestDataDir = "../cdm-test/src/test/data/";

  static {
    testdataDir = System.getProperty(testdataDirPropName); // Check the system property.

    // Use default paths if needed.
    if (testdataDir == null) {
      testdataDir = "/share/testdata/";
      logger.warn("No '{}' property found; using default value '{}'.", testdataDirPropName, testdataDir);
    }

    // Make sure paths ends with a slash.
    testdataDir = testdataDir.replace('\\', '/'); // canonical
    if (!testdataDir.endsWith("/"))
      testdataDir += "/";

    cdmUnitTestDir = testdataDir + "cdmUnitTest/";

    File file = new File(cdmUnitTestDir);
    if (!file.exists() || !file.isDirectory()) {
      logger.warn("cdmUnitTest directory does not exist: {}", file.getAbsolutePath());
    }

    AliasTranslator.addAlias("${cdmUnitTest}", cdmUnitTestDir);
  }

  public static NetcdfFile open(String filename) throws IOException {
    logger.debug("**** Open {}", filename);
    NetcdfFile ncfile = NetcdfFiles.open(filename, null);
    logger.debug("open {}", ncfile);

    return ncfile;
  }

  public static NetcdfFile openFileLocal(String filename) throws IOException {
    return open(TestDir.cdmLocalTestDataDir + filename);
  }

  public static long checkLeaks() {
    if (RandomAccessFile.getOpenFiles().size() > 0) {
      logger.debug("RandomAccessFile still open:");
      for (String filename : RandomAccessFile.getOpenFiles()) {
        logger.debug("  open= {}", filename);
      }
    } else {
      logger.debug("RandomAccessFile: no leaks");
    }

    logger.debug("RandomAccessFile: count open={}, max={}", RandomAccessFile.getOpenFileCount(),
        RandomAccessFile.getMaxOpenFileCount());
    return RandomAccessFile.getOpenFiles().size();
  }

  ////////////////////////////////////////////////

  // Calling routine passes in an action.
  public interface Act {
    /**
     * @param filename file to act on
     * @return count
     */
    int doAct(String filename) throws IOException;
  }

  public static class FileFilterFromSuffixes implements FileFilter {
    String[] suffixes;

    public FileFilterFromSuffixes(String suffixes) {
      this.suffixes = suffixes.split(" ");
    }

    @Override
    public boolean accept(File file) {
      for (String s : suffixes)
        if (file.getPath().endsWith(s))
          return true;
      return false;
    }
  }

  public static FileFilter FileFilterSkipSuffix(String suffixes) {
    return new FileFilterNoWant(suffixes);
  }

  private static class FileFilterNoWant implements FileFilter {
    String[] suffixes;

    FileFilterNoWant(String suffixes) {
      this.suffixes = suffixes.split(" ");
    }

    @Override
    public boolean accept(File file) {
      for (String s : suffixes) {
        if (file.getPath().endsWith(s)) {
          return false;
        }
      }
      return true;
    }
  }

  /** Call act.doAct() on each file in dirName that passes the file filter, recurse into subdirs. */
  public static int actOnAll(String dirName, FileFilter ff, Act act) throws IOException {
    return actOnAll(dirName, ff, act, true);
  }

  /**
   * Call act.doAct() on each file in dirName passing the file filter
   *
   * @param dirName recurse into this directory
   * @param ff for files that pass this filter, may be null
   * @param act perform this acction
   * @param recurse recurse into subdirectories
   * @return count
   * @throws IOException on IO error
   */
  public static int actOnAll(String dirName, FileFilter ff, Act act, boolean recurse) throws IOException {
    int count = 0;

    logger.debug("---------------Reading directory {}", dirName);
    File allDir = new File(dirName);
    File[] allFiles = allDir.listFiles();
    if (null == allFiles) {
      logger.debug("---------------INVALID {}", dirName);
      throw new FileNotFoundException("Cant open " + dirName);
    }

    List<File> flist = Arrays.asList(allFiles);
    Collections.sort(flist);

    for (File f : flist) {
      String name = f.getAbsolutePath();
      if (f.isDirectory()) {
        continue;
      }
      if (((ff == null) || ff.accept(f)) && !name.endsWith(".exclude")) {
        name = StringUtil2.substitute(name, "\\", "/");
        logger.debug("----acting on file {}", name);
        count += act.doAct(name);
      }
    }

    if (!recurse) {
      return count;
    }

    for (File f : allFiles) {
      if (f.isDirectory() && !f.getName().equals("exclude") && !f.getName().equals("problem")) {
        count += actOnAll(f.getAbsolutePath(), ff, act);
      }
    }

    return count;
  }

  ////////////////////////////////////////////////////////////////////////////

  /** Make list of filenames that pass the file filter, recurse true. */
  public static int actOnAllParameterized(String dirName, FileFilter ff, Collection<Object[]> filenames)
      throws IOException {
    return actOnAll(dirName, ff, new ListAction(filenames), true);
  }

  /** Make list of filenames that pass the file filter, recurse set by user. */
  public static int actOnAllParameterized(String dirName, FileFilter ff, Collection<Object[]> filenames,
      boolean recurse) throws IOException {
    return actOnAll(dirName, ff, new ListAction(filenames), recurse);
  }

  private static class ListAction implements Act {
    Collection<Object[]> filenames;

    ListAction(Collection<Object[]> filenames) {
      this.filenames = filenames;
    }

    @Override
    public int doAct(String filename) {
      filenames.add(new Object[] {filename});
      return 0;
    }
  }

  ////////////////////////////////////////////////////////////////////////////

  public static void readAll(String filename) throws IOException {
    ReadAllVariables act = new ReadAllVariables();
    act.doAct(filename);
  }

  private static class ReadAllVariables implements Act {
    @Override
    public int doAct(String filename) throws IOException {
      try (NetcdfFile ncfile = NetcdfFiles.open(filename)) {
        return readAllData(ncfile);
      }
    }
  }

  private static int max_size = 1000 * 1000 * 10;

  static Section makeSubset(Variable v) {
    int[] shape = v.getShape();
    shape[0] = 1;
    Section s = new Section(shape);
    long size = s.computeSize();
    shape[0] = (int) Math.max(1, max_size / size);
    return new Section(shape);
  }

  public static int readAllData(NetcdfFile ncfile) throws IOException {
    logger.debug("------Reading ncfile {}", ncfile.getLocation());
    try {
      for (Variable v : ncfile.getVariables()) {
        if (v.getSize() > max_size) {
          Section s = makeSubset(v);
          logger.debug("  Try to read variable {} size={} section={}", v.getNameAndDimensions(), v.getSize(), s);
          v.read(s);
        } else {
          logger.debug("  Try to read variable {} size={}", v.getNameAndDimensions(), v.getSize());
          v.read();
        }
      }

      return 1;
    } catch (InvalidRangeException e) {
      throw new RuntimeException(e);
    }
  }
}
