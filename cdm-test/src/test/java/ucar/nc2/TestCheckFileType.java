/*
 * Copyright (c) 1998-2020 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.nc2;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.nc2.iosp.NCheader;
import ucar.unidata.io.RandomAccessFile;
import ucar.unidata.util.test.TestDir;
import ucar.unidata.util.test.UnitTestCommon;
import ucar.unidata.util.test.category.NeedsContentRoot;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

/**
 * Test that NCheader.checkFileType can recognize various file types
 */
@Category(NeedsContentRoot.class)
@RunWith(Parameterized.class)
public class TestCheckFileType extends UnitTestCommon {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  static final String PREFIX = "thredds/public/testdata/";

  @Parameterized.Parameters(name = "{1}")
  public static List<Object[]> getTestParameters() {
    List<Object[]> result = new ArrayList<>();
    result.add(new Object[] {NCheader.NC_FORMAT_NETCDF3, "testData.nc"});
    result.add(new Object[] {NCheader.NC_FORMAT_64BIT_OFFSET, "nc_test_cdf2.nc"});
    result.add(new Object[] {NCheader.NC_FORMAT_64BIT_DATA, "nc_test_cdf5.nc"});
    result.add(new Object[] {NCheader.NC_FORMAT_HDF5, "group.test2.nc"}); // aka netcdf4
    result.add(new Object[] {NCheader.NC_FORMAT_HDF4, "nc_test_hdf4.hdf4"});
    return result;
  }

  @After
  public void cleanup() {
    super.unbindstd();
  }

  @Parameterized.Parameter(0)
  public int kind;

  @Parameterized.Parameter(1)
  public String filename;

  @Test
  public void testCheckFileType() throws Exception {
    String location = canonjoin(TestDir.cdmTestDataDir, canonjoin(PREFIX, filename));
    try (RandomAccessFile raf = RandomAccessFile.acquire(location)) {
      // Verify type
      int found = NCheader.checkFileType(raf);
      String foundname = NCheader.formatName(found);
      String kindname = NCheader.formatName(kind);
      System.err.println("Testing format: " + kindname);
      Assert.assertTrue(String.format("***Fail: expected=%s found=%s%n", kindname, foundname), kind == found);
    }
  }

}
