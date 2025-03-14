/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.nc2.geotiff;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.awt.Color;
import ucar.ma2.Array;
import ucar.ma2.ArrayByte;
import ucar.ma2.ArrayInt;
import ucar.ma2.ArrayFloat;
import ucar.ma2.ArrayShort;
import ucar.ma2.DataType;
import ucar.ma2.Index;
import ucar.ma2.IndexIterator;
import ucar.ma2.IsMissingEvaluator;
import ucar.ma2.MAMath;
import ucar.nc2.dataset.CoordinateAxis1D;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridDataset;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.ft2.coverage.CoverageCoordAxis1D;
import ucar.nc2.ft2.coverage.CoverageCoordSys;
import ucar.nc2.ft2.coverage.GeoReferencedArray;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.unidata.geoloc.Projection;
import ucar.unidata.geoloc.projection.AlbersEqualArea;
import ucar.unidata.geoloc.projection.LambertConformal;
import ucar.unidata.geoloc.projection.LatLonProjection;
import ucar.unidata.geoloc.projection.Mercator;
import ucar.unidata.geoloc.projection.Stereographic;
import ucar.unidata.geoloc.projection.TransverseMercator;
import ucar.unidata.geoloc.projection.proj4.AlbersEqualAreaEllipse;

/**
 * Write GeoTIFF files.
 * Regular data only
 *
 * @author caron, yuan
 */
public class GeotiffWriter implements Closeable {

  protected GeoTiff geotiff;
  protected short pageNumber = 1;
  protected int[] colorTable;

  /**
   * Constructor
   *
   * @param fileOut name of output file.
   */
  public GeotiffWriter(String fileOut) {
    geotiff = new GeoTiff(fileOut);
  }

  public void close() throws IOException {
    geotiff.close();
  }

  /**
   * Write GridDatatype data to the geotiff file.
   *
   * Greyscale mode will auto-normalize the data from 1 to 255 and save as unsigned bytes, with 0's used
   * for missing data. A color table can be applied if specified via `setColorTable()`.
   * Non-greyscale mode will save the data as floats, encoding missing data as the data minimum minus one.
   *
   * @param dataset grid in contained in this dataset
   * @param grid data is in this grid
   * @param data 2D array in YX order
   * @param greyScale if true, write greyScale image, else dataSample.
   * @throws IOException on i/o error
   */
  public void writeGrid(GridDataset dataset, GridDatatype grid, Array data, boolean greyScale) throws IOException {
    writeGrid(dataset, grid, data, greyScale, greyScale ? DataType.UBYTE : DataType.FLOAT);
  }

  /**
   * Write GridDatatype data to the geotiff file.
   *
   * Greyscale mode will auto-normalize the data from 1 to 255 and save as unsigned bytes, with 0's used
   * for missing data.
   * Non-greyscale mode with a floating point dtype will save the data as floats, encoding missing data
   * as the data's minimum minus one. Any other dtype will save the data coerced to the specified dtype.
   *
   * A color table can be applied if specified via `setColorTable()` and the dtype is UBYTE.
   *
   * @param dataset grid in contained in this dataset
   * @param grid data is in this grid
   * @param data 2D array in YX order
   * @param greyScale if true, write greyScale image, else dataSample.
   * @param dtype DataType for the output. See other writeGrid() documentation for more details.
   * @throws IOException on i/o error
   * @throws IllegalArgumentException if above assumptions not valid
   */
  public void writeGrid(GridDataset dataset, GridDatatype grid, Array data, boolean greyScale, DataType dtype)
      throws IOException, IllegalArgumentException {
    // This check has to be *before* resolving the dtype so that we are only
    // checking explicitly specified data types.
    if (greyScale && dtype != DataType.UBYTE) {
      throw new IllegalArgumentException("When greyScale is true, dtype must be UBYTE");
    }

    if (colorTable != null && colorTable.length > 0 && dtype != DataType.UBYTE) {
      throw new IllegalArgumentException("When using the color table, dtype must be UBYTE");
    }

    GridCoordSystem gcs = grid.getCoordinateSystem();

    if (!gcs.isRegularSpatial()) {
      throw new IllegalArgumentException("Must have 1D x and y axes for " + grid.getFullName());
    }

    CoordinateAxis1D xaxis = (CoordinateAxis1D) gcs.getXHorizAxis();
    CoordinateAxis1D yaxis = (CoordinateAxis1D) gcs.getYHorizAxis();

    // units may need to be scaled to meters
    double scaler = (xaxis.getUnitsString().equalsIgnoreCase("km")) ? 1000.0 : 1.0;

    // data must go from top to bottom
    double xStart = xaxis.getCoordEdge(0) * scaler;
    double yStart = yaxis.getCoordEdge(0) * scaler;
    double xInc = xaxis.getIncrement() * scaler;
    double yInc = Math.abs(yaxis.getIncrement()) * scaler;

    if (yaxis.getCoordValue(0) < yaxis.getCoordValue(1)) {
      data = data.flip(0);
      yStart = yaxis.getCoordEdge((int) yaxis.getSize()) * scaler;
    }

    if (!xaxis.isRegular() || !yaxis.isRegular()) {
      throw new IllegalArgumentException("Must be evenly spaced grid = " + grid.getFullName());
    }

    if (pageNumber > 1) {
      geotiff.initTags();
    }

    // write it out
    writeGrid(grid, data, greyScale, xStart, yStart, xInc, yInc, pageNumber, dtype);
    pageNumber++;
  }


  /**
   * Write Grid data to the geotiff file.
   * Grid currently must:
   * <ol>
   * <li>have a 1D X and Y coordinate axes.
   * <li>be lat/lon or Lambert Conformal Projection
   * <li>be equally spaced
   * </ol>
   *
   * Greyscale mode will auto-normalize the data from 1 to 255 and save as unsigned bytes, with 0's used
   * for missing data. A color table can be applied if specified via `setColorTable()`.
   * Non-greyscale mode will save the data as floats, encoding missing data as the data minimum minus one.
   *
   * @param grid original grid
   * @param data 2D array in YX order
   * @param greyScale if true, normalize the data before writing, otherwise, only handle missing data.
   * @param xStart starting x coord
   * @param yStart starting y coord
   * @param xInc increment x coord
   * @param yInc increment y coord
   * @param imageNumber used to write multiple images
   * @throws IOException on i/o error
   * @throws IllegalArgumentException if above assumptions not valid
   */
  void writeGrid(GridDatatype grid, Array data, boolean greyScale, double xStart, double yStart, double xInc,
      double yInc, int imageNumber) throws IOException {
    writeGrid(grid, data, greyScale, xStart, yStart, xInc, yInc, imageNumber,
        greyScale ? DataType.UBYTE : DataType.FLOAT);
  }

  /**
   * Write Grid data to the geotiff file.
   * Grid currently must:
   * <ol>
   * <li>have a 1D X and Y coordinate axes.
   * <li>be lat/lon or Lambert Conformal Projection
   * <li>be equally spaced
   * </ol>
   *
   * Greyscale mode will auto-normalize the data from 1 to 255 and save as unsigned bytes, with 0's used
   * for missing data.
   * Non-greyscale mode with a floating point dtype will save the data as floats, encoding missing data
   * as the data's minimum minus one. Any other dtype will save the data coerced to the specified dtype.
   *
   * A color table can be applied if specified via `setColorTable()` and the dtype is UBYTE.
   *
   * @param grid original grid
   * @param data 2D array in YX order
   * @param greyScale if true, normalize the data before writing, otherwise, only handle missing data.
   * @param xStart starting x coord
   * @param yStart starting y coord
   * @param xInc increment x coord
   * @param yInc increment y coord
   * @param imageNumber used to write multiple images
   * @param dtype if greyScale is false, then save the data in the given data type.
   *        Currently, this is a bit hobbled in order to avoid back-compatibility breaks.
   *        If dtype is DOUBLE, is is currenly downcasted to FLOAT.
   *        When dtype is floating point, missing data is encoded as the data's minimum minus one.
   *        If null, then use the datatype of the given array.
   * @throws IOException on i/o error
   * @throws IllegalArgumentException if above assumptions not valid
   */
  void writeGrid(GridDatatype grid, Array data, boolean greyScale, double xStart, double yStart, double xInc,
      double yInc, int imageNumber, DataType dtype) throws IOException, IllegalArgumentException {

    // This check has to be *before* resolving the dtype so that we are only
    // checking explicitly specified data types.
    if (greyScale && dtype != DataType.UBYTE) {
      throw new IllegalArgumentException("When greyScale is true, dtype must be UBYTE");
    }

    if (colorTable != null && colorTable.length > 0 && dtype != DataType.UBYTE) {
      throw new IllegalArgumentException("When using the color table, dtype must be UBYTE");
    }

    int nextStart;
    GridCoordSystem gcs = grid.getCoordinateSystem();

    // get rid of this when all projections are implemented
    if (!gcs.isLatLon() && !(gcs.getProjection() instanceof LambertConformal)
        && !(gcs.getProjection() instanceof Stereographic) && !(gcs.getProjection() instanceof Mercator)
        // && !(gcs.getProjection() instanceof TransverseMercator) LOOK broken ??
        && !(gcs.getProjection() instanceof AlbersEqualAreaEllipse)
        && !(gcs.getProjection() instanceof AlbersEqualArea)) {
      throw new IllegalArgumentException("Unsupported projection = " + gcs.getProjection().getClass().getName());
    }

    if (dtype == null) {
      dtype = data.getDataType();
      // Need to cap at single precision floats because that's what gets written for floating points
      if (dtype == DataType.DOUBLE) {
        dtype = DataType.FLOAT;
      }
    }

    // write the data first
    MAMath.MinMax dataMinMax = grid.getMinMaxSkipMissingData(data);
    if (greyScale) {
      data = replaceMissingValuesAndScale(grid, data, dataMinMax);
      nextStart = writeData(data, DataType.UBYTE);
    } else if (dtype == DataType.FLOAT) {
      // Backwards compatibility shim
      data = replaceMissingValues(grid, data, dataMinMax);
      nextStart = writeData(data, dtype);
    } else {
      data = coerceData(data, dtype);
      nextStart = writeData(data, dtype);
    }

    // set the width and the height
    int height = data.getShape()[0]; // Y
    int width = data.getShape()[1]; // X

    writeMetadata(greyScale, xStart, yStart, xInc, yInc, height, width, imageNumber, nextStart, dataMinMax,
        gcs.getProjection(), dtype);
  }

  private void writeMetadata(boolean greyScale, double xStart, double yStart, double xInc, double yInc, int height,
      int width, int imageNumber, int nextStart, MAMath.MinMax dataMinMax, Projection proj, DataType dtype)
      throws IOException {

    if (dtype == null) {
      throw new IllegalArgumentException("dtype can't be null in writeMetadata()");
    }

    if (greyScale && dtype != DataType.UBYTE) {
      throw new IllegalArgumentException("When greyScale is true, dtype must be UBYTE");
    }

    if (colorTable != null && colorTable.length > 0 && dtype != DataType.UBYTE) {
      throw new IllegalArgumentException("When using the color table, the dtype must be UBYTE");
    }

    int elemSize = dtype.getSize();

    geotiff.addTag(new IFDEntry(Tag.ImageWidth, FieldType.SHORT).setValue(width));
    geotiff.addTag(new IFDEntry(Tag.ImageLength, FieldType.SHORT).setValue(height));

    // set the multiple images tag
    int ff = 1 << 1;
    int page = imageNumber - 1;
    geotiff.addTag(new IFDEntry(Tag.NewSubfileType, FieldType.SHORT).setValue(ff));
    geotiff.addTag(new IFDEntry(Tag.PageNumber, FieldType.SHORT).setValue(page, 2));

    // just make it all one big "row"
    geotiff.addTag(new IFDEntry(Tag.RowsPerStrip, FieldType.SHORT).setValue(1)); // height));
    // the following changes to make it viewable in ARCMAP
    /*
     * int size = elemSize * height * width; // size in bytes
     * geotiff.addTag( new IFDEntry(Tag.StripByteCounts, FieldType.LONG).setValue( size));
     * // data starts here, header is written at the end
     * if( imageNumber == 1 )
     * geotiff.addTag( new IFDEntry(Tag.StripOffsets, FieldType.LONG).setValue( 8));
     * else
     * geotiff.addTag( new IFDEntry(Tag.StripOffsets, FieldType.LONG).setValue(nextStart));
     */

    int[] soffset = new int[height];
    int[] sbytecount = new int[height];
    if (imageNumber == 1) {
      soffset[0] = 8;
    } else {
      soffset[0] = nextStart;
    }
    sbytecount[0] = width * elemSize;
    for (int i = 1; i < height; i++) {
      soffset[i] = soffset[i - 1] + width * elemSize;
      sbytecount[i] = width * elemSize;
    }
    geotiff.addTag(new IFDEntry(Tag.StripByteCounts, FieldType.LONG, width).setValue(sbytecount));
    geotiff.addTag(new IFDEntry(Tag.StripOffsets, FieldType.LONG, width).setValue(soffset));
    // standard tags
    geotiff.addTag(new IFDEntry(Tag.Orientation, FieldType.SHORT).setValue(1));
    geotiff.addTag(new IFDEntry(Tag.Compression, FieldType.SHORT).setValue(1)); // no compression
    geotiff.addTag(new IFDEntry(Tag.Software, FieldType.ASCII).setValue("nc2geotiff"));
    geotiff.addTag(new IFDEntry(Tag.PlanarConfiguration, FieldType.SHORT).setValue(1));

    // standard tags for Greyscale images ( see TIFF spec, section 4)
    geotiff.addTag(new IFDEntry(Tag.BitsPerSample, FieldType.SHORT).setValue(elemSize * 8));
    geotiff.addTag(new IFDEntry(Tag.SamplesPerPixel, FieldType.SHORT).setValue(1));

    geotiff.addTag(new IFDEntry(Tag.XResolution, FieldType.RATIONAL).setValue(1, 1));
    geotiff.addTag(new IFDEntry(Tag.YResolution, FieldType.RATIONAL).setValue(1, 1));
    geotiff.addTag(new IFDEntry(Tag.ResolutionUnit, FieldType.SHORT).setValue(1));

    if (colorTable != null && colorTable.length > 0) {
      // standard tags for Palette-color images ( see TIFF spec, section 5)
      geotiff.addTag(new IFDEntry(Tag.PhotometricInterpretation, FieldType.SHORT).setValue(3));
      geotiff.addTag(new IFDEntry(Tag.ColorMap, FieldType.SHORT, colorTable.length).setValue(colorTable));
    } else {
      geotiff.addTag(new IFDEntry(Tag.PhotometricInterpretation, FieldType.SHORT).setValue(1)); // black is zero
    }

    // standard tags for SampleFormat ( see TIFF spec, section 19)
    if (dtype.isIntegral() && !greyScale) {
      geotiff.addTag(new IFDEntry(Tag.SampleFormat, FieldType.SHORT).setValue(dtype.isUnsigned() ? 1 : 2)); // UINT or
                                                                                                            // INT
      int min = (int) (dataMinMax.min);
      int max = (int) (dataMinMax.max);
      FieldType ftype;
      DataType sdtype = dtype.withSignedness(DataType.Signedness.SIGNED);
      if (sdtype == DataType.BYTE) {
        ftype = dtype.isUnsigned() ? FieldType.BYTE : FieldType.SBYTE;
      } else if (sdtype == DataType.SHORT) {
        ftype = dtype.isUnsigned() ? FieldType.SHORT : FieldType.SSHORT;
      } else if (sdtype == DataType.INT) {
        // A geotiff LONG/SLONG is really a 4-byte regular integer
        ftype = dtype.isUnsigned() ? FieldType.LONG : FieldType.SLONG;
      } else {
        throw new IllegalArgumentException("Unsupported dtype: " + dtype);
      }
      geotiff.addTag(new IFDEntry(Tag.SMinSampleValue, ftype).setValue(min));
      geotiff.addTag(new IFDEntry(Tag.SMaxSampleValue, ftype).setValue(max));
      // No GDALNoData tag is set as it is ambiguous what would be appropriate here.
    } else if (dtype.isFloatingPoint()) {
      geotiff.addTag(new IFDEntry(Tag.SampleFormat, FieldType.SHORT).setValue(3)); // IEEE Floating Point Type
      float min = (float) (dataMinMax.min);
      float max = (float) (dataMinMax.max);
      geotiff.addTag(new IFDEntry(Tag.SMinSampleValue, FieldType.FLOAT).setValue(min));
      geotiff.addTag(new IFDEntry(Tag.SMaxSampleValue, FieldType.FLOAT).setValue(max));
      geotiff.addTag(new IFDEntry(Tag.GDALNoData, FieldType.ASCII).setValue(String.valueOf(min - 1.f)));
    }

    /*
     * geotiff.addTag( new IFDEntry(Tag.Geo_ModelPixelScale, FieldType.DOUBLE).setValue(
     * new double[] {5.0, 2.5, 0.0} ));
     * geotiff.addTag( new IFDEntry(Tag.Geo_ModelTiepoint, FieldType.DOUBLE).setValue(
     * new double[] {0.0, 0.0, 0.0, -180.0, 90.0, 0.0 } ));
     * // new double[] {0.0, 0.0, 0.0, 183.0, 90.0, 0.0} ));
     * IFDEntry ifd = new IFDEntry(Tag.Geo_KeyDirectory, FieldType.SHORT).setValue(
     * new int[] {1, 1, 0, 4, 1024, 0, 1, 2, 1025, 0, 1, 1, 2048, 0, 1, 4326, 2054, 0, 1, 9102} );
     * geotiff.addTag( ifd);
     */

    // set the transformation from projection to pixel, add tie point tag
    geotiff.setTransform(xStart, yStart, xInc, yInc);

    if (proj instanceof LatLonProjection) {
      addLatLonTags();
    } else if (proj instanceof LambertConformal) {
      addLambertConformalTags((LambertConformal) proj, xStart, yStart);
    } else if (proj instanceof Stereographic) {
      addPolarStereographicTags((Stereographic) proj, xStart, yStart);
    } else if (proj instanceof Mercator) {
      addMercatorTags((Mercator) proj);
    } else if (proj instanceof TransverseMercator) {
      addTransverseMercatorTags((TransverseMercator) proj);
    } else if (proj instanceof AlbersEqualArea) {
      addAlbersEqualAreaTags((AlbersEqualArea) proj);
    } else if (proj instanceof AlbersEqualAreaEllipse) {
      addAlbersEqualAreaEllipseTags((AlbersEqualAreaEllipse) proj);
    } else {
      throw new IllegalArgumentException("Unsupported projection = " + proj.getClass().getName());
    }

    geotiff.writeMetadata(imageNumber);
  }

  /**
   * Get a copy of the current colormap as a 1-D 3*256 element array (or null).
   *
   * All 256 red values first, then green, then blue.
   *
   * For these RGB triplets, 0 is minimum intensity, 65535 is maximum intensity (due to geotiff conventions).
   * This function is intended for debugging and testing.
   */
  public int[] getColorTable() {
    if (colorTable == null) {
      return null;
    } else {
      return colorTable.clone();
    }
  }

  /**
   * Have the geotiff include a colormap in the form of a mapping of the pixel value to the rgb triplet.
   * Assumes an RGB of {0, 0, 0} for any values not specified.
   *
   * Pass null to unset the colorTable.
   *
   * For these RGB triplets, 0 is minimum intensity, 255 is maximum intensity. Values outside that range will
   * be floored/ceilinged to the [0, 255] range. The color table is also assumed to be for pixel values
   * between 0 and 255.
   *
   * In order for the color table to be properly included in the geotiff, the output data type must be unsigned bytes.
   * This works even for greyscale mode.
   */
  public void setColorTable(Map<Integer, Color> colorMap) {
    setColorTable(colorMap, new Color(0, 0, 0));
  }

  /**
   * Have the geotiff include a colormap in the form of a mapping of the pixel value to the rgb triplet.
   * Provide a default RGB triplet for any values not specified.
   *
   * Pass null to unset the colorTable.
   *
   * For these RGB triplets, 0 is minimum intensity, 255 is maximum intensity. Values outside that range will
   * be floored/ceilinged to the [0, 255] range. The color table is also assumed to be for pixel values
   * between 0 and 255.
   * In order for the color table to be properly included in the geotiff, the output data type must be unsigned bytes.
   * This works even for greyscale mode.
   */
  public void setColorTable(Map<Integer, Color> colorMap, Color defaultRGB) {
    if (colorMap == null) {
      colorTable = null;
      return;
    }

    // TIFF spec allows for 4 or 8 bits per sample (making for 16 or 256 entries).
    // Since we don't support saving data as 4 bits per sample, we'll force it to 256.
    colorTable = new int[3 * 256];
    for (int i = 0; i < 256; i++) {
      // Scale it up to [0, 65535], which is needed by the ColorMap tag.
      // It seems like 0 should map to 255, 1 to 511, and so forth, as that
      // seems to be the only way to get gdalinfo to report back the correct values.
      colorTable[i] = (colorMap.getOrDefault(i, defaultRGB).getRed() + 1) * 256 - 1;
      colorTable[256 + i] = (colorMap.getOrDefault(i, defaultRGB).getGreen() + 1) * 256 - 1;
      colorTable[512 + i] = (colorMap.getOrDefault(i, defaultRGB).getBlue() + 1) * 256 - 1;
    }
  }

  /**
   * Creates a colormap in the form of a mapping of the pixel value to the rgb color.
   *
   * @param flag_values is an array of values for each categorical
   * @param flag_colors is an array of octal strings (e.g., #00AAFF) of same length as flag_values.
   * @return Map of the flag values to Color objects the RGB color values,
   *         with 0 representing minimum intensity and 255 representing maximum intensity.
   * @throws IllegalArgumentException if above assumptions not valid
   * @throws NumberFormatException if a supplied color isn't parsable
   */
  public static HashMap<Integer, Color> createColorMap(int[] flag_values, String[] flag_colors)
      throws IllegalArgumentException, NumberFormatException {
    if (flag_values.length != flag_colors.length) {
      throw new IllegalArgumentException("flag_values and flag_colors must be of equal length");
    }
    HashMap<Integer, Color> colorMap = new HashMap<Integer, Color>();
    for (int i = 0; i < flag_values.length; i++) {
      colorMap.put(flag_values[i], Color.decode(flag_colors[i]));
    }
    return colorMap;
  }

  /**
   * Coerce a given data array into an array of bytes.
   * Always returns a copy. No data safety check is performed.
   *
   * @param data input data array (of any data type)
   * @param isUnsigned coerce to unsigned bytes
   * @return byte data array
   */
  static ArrayByte coerceByte(Array data, boolean isUnsigned) {
    ArrayByte array = (ArrayByte) Array.factory(isUnsigned ? DataType.UBYTE : DataType.BYTE, data.getShape());
    IndexIterator dataIter = data.getIndexIterator();
    IndexIterator resultIter = array.getIndexIterator();

    while (dataIter.hasNext()) {
      resultIter.setByteNext(dataIter.getByteNext());
    }

    return array;
  }

  /**
   * Coerce a given data array into an array of 16-bit integers.
   * Always returns a copy. No data safety check is performed.
   *
   * @param data input data array (of any data type)
   * @param isUnsigned coerce to unsigned integers
   * @return short integer data array
   */
  static ArrayShort coerceShort(Array data, boolean isUnsigned) {
    ArrayShort array = (ArrayShort) Array.factory(isUnsigned ? DataType.USHORT : DataType.SHORT, data.getShape());
    IndexIterator dataIter = data.getIndexIterator();
    IndexIterator resultIter = array.getIndexIterator();

    while (dataIter.hasNext()) {
      resultIter.setShortNext(dataIter.getShortNext());
    }

    return array;
  }


  /**
   * Coerce a given data array into an array of 32-bit integers.
   * Always returns a copy. No data safety check is performed.
   *
   * @param data input data array (of any data type)
   * @param isUnsigned coerce to unsigned integers
   * @return 32-bit integer data array
   */
  static ArrayInt coerceInt(Array data, boolean isUnsigned) {
    ArrayInt array = (ArrayInt) Array.factory(isUnsigned ? DataType.UINT : DataType.INT, data.getShape());
    IndexIterator dataIter = data.getIndexIterator();
    IndexIterator resultIter = array.getIndexIterator();

    while (dataIter.hasNext()) {
      resultIter.setIntNext(dataIter.getIntNext());
    }

    return array;
  }

  /**
   * Coerce a given data array into an array of 32-bit floats.
   * Always returns a copy. No data safety check is performed.
   *
   * @param data input data array (of any data type)
   * @return float data array
   */
  static ArrayFloat coerceFloat(Array data) {
    ArrayFloat array = (ArrayFloat) Array.factory(DataType.FLOAT, data.getShape());
    IndexIterator dataIter = data.getIndexIterator();
    IndexIterator resultIter = array.getIndexIterator();

    while (dataIter.hasNext()) {
      resultIter.setFloatNext(dataIter.getFloatNext());
    }

    return array;
  }

  /**
   * Replace missing values with dataMinMax.min - 1.0; return a floating point data array.
   *
   * @param grid GridDatatype
   * @param data input data array
   * @return floating point data array with missing values replaced.
   */
  private ArrayFloat replaceMissingValues(IsMissingEvaluator grid, Array data, MAMath.MinMax dataMinMax) {
    float minValue = (float) (dataMinMax.min - 1.0);

    ArrayFloat floatArray = (ArrayFloat) Array.factory(DataType.FLOAT, data.getShape());
    IndexIterator dataIter = data.getIndexIterator();
    IndexIterator floatIter = floatArray.getIndexIterator();
    while (dataIter.hasNext()) {
      float v = dataIter.getFloatNext();
      if (grid.isMissing((double) v)) {
        v = minValue;
      }
      floatIter.setFloatNext(v);
    }

    return floatArray;
  }

  /**
   * Replace missing values with 0; scale other values between 1 and 255, return a ubyte data array.
   *
   * @param grid GridDatatype
   * @param data input data array
   * @return byte data array with missing values replaced and data scaled from 1- 255.
   */
  private ArrayByte replaceMissingValuesAndScale(IsMissingEvaluator grid, Array data, MAMath.MinMax dataMinMax) {
    double scale = 254.0 / (dataMinMax.max - dataMinMax.min);

    ArrayByte byteArray = (ArrayByte) Array.factory(DataType.BYTE, data.getShape());
    IndexIterator dataIter = data.getIndexIterator();
    IndexIterator resultIter = byteArray.getIndexIterator();

    byte bv;
    while (dataIter.hasNext()) {
      double v = dataIter.getDoubleNext();
      if (grid.isMissing(v)) {
        bv = 0;
      } else {
        int iv = (int) ((v - dataMinMax.min) * scale + 1);
        bv = (byte) (iv & 0xff);
      }
      resultIter.setByteNext(bv);
    }

    return byteArray;
  }

  private void addLatLonTags1() {
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTModelTypeGeoKey, GeoKey.TagValue.ModelType_Geographic));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeogGeodeticDatumGeoKey, GeoKey.TagValue.GeogGeodeticDatum6267));
  }

  private void addLatLonTags() {
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTModelTypeGeoKey, GeoKey.TagValue.ModelType_Geographic));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTRasterTypeGeoKey, GeoKey.TagValue.RasterType_Area));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeographicTypeGeoKey, GeoKey.TagValue.GeographicType_WGS_84));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeogPrimeMeridianGeoKey, GeoKey.TagValue.GeogPrimeMeridian_GREENWICH));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeogAngularUnitsGeoKey, GeoKey.TagValue.GeogAngularUnits_DEGREE));
  }


  private void addPolarStereographicTags(Stereographic proj, double FalseEasting, double FalseNorthing) {
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTModelTypeGeoKey, GeoKey.TagValue.ModelType_Projected));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTRasterTypeGeoKey, GeoKey.TagValue.RasterType_Area));

    // define the "geographic Coordinate System"
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeographicTypeGeoKey, GeoKey.TagValue.GeographicType_WGS_84));
    // geotiff.addGeoKey( new GeoKey( GeoKey.Tag.GeogPrimeMeridianGeoKey, GeoKey.TagValue.GeogPrimeMeridian_GREENWICH));
    // geotiff.addGeoKey( new GeoKey( GeoKey.Tag.GeogAngularUnitsGeoKey, GeoKey.TagValue.GeogAngularUnits_DEGREE));

    // define the "coordinate transformation"
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjectedCSTypeGeoKey, GeoKey.TagValue.ProjectedCSType_UserDefined));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.PCSCitationGeoKey, "Snyder"));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjectionGeoKey, GeoKey.TagValue.ProjectedCSType_UserDefined));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjLinearUnitsGeoKey, GeoKey.TagValue.ProjLinearUnits_METER));
    // geotiff.addGeoKey( new GeoKey( GeoKey.Tag.ProjLinearUnitsSizeGeoKey, 1.0)); // units of km

    // the specifics for Polar Stereographic
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjCoordTransGeoKey, GeoKey.TagValue.ProjCoordTrans_Stereographic));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjCenterLongGeoKey, 0.0));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjNatOriginLatGeoKey, 90.0));
    // geotiff.addGeoKey( new GeoKey( GeoKey.Tag.ProjNatOriginLongGeoKey, proj.getTangentLon()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjScaleAtNatOriginGeoKey, 1.0));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjFalseEastingGeoKey, 0.0));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjFalseNorthingGeoKey, 0.0));
  }

  private void addLambertConformalTags(LambertConformal proj, double FalseEasting, double FalseNorthing) {
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTModelTypeGeoKey, GeoKey.TagValue.ModelType_Projected));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTRasterTypeGeoKey, GeoKey.TagValue.RasterType_Area));

    // define the "geographic Coordinate System"
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeographicTypeGeoKey, GeoKey.TagValue.GeographicType_WGS_84));
    // geotiff.addGeoKey( new GeoKey( GeoKey.Tag.GeogPrimeMeridianGeoKey, GeoKey.TagValue.GeogPrimeMeridian_GREENWICH));
    // geotiff.addGeoKey( new GeoKey( GeoKey.Tag.GeogAngularUnitsGeoKey, GeoKey.TagValue.GeogAngularUnits_DEGREE));

    // define the "coordinate transformation"
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjectedCSTypeGeoKey, GeoKey.TagValue.ProjectedCSType_UserDefined));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.PCSCitationGeoKey, "Snyder"));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjectionGeoKey, GeoKey.TagValue.ProjectedCSType_UserDefined));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjLinearUnitsGeoKey, GeoKey.TagValue.ProjLinearUnits_METER));
    // geotiff.addGeoKey( new GeoKey( GeoKey.Tag.ProjLinearUnitsSizeGeoKey, 1.0)); // units of km

    // the specifics for lambert conformal
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjCoordTransGeoKey, GeoKey.TagValue.ProjCoordTrans_LambertConfConic_2SP));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjStdParallel1GeoKey, proj.getParallelOne()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjStdParallel2GeoKey, proj.getParallelTwo()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjCenterLongGeoKey, proj.getOriginLon()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjNatOriginLatGeoKey, proj.getOriginLat()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjNatOriginLongGeoKey, proj.getOriginLon()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjScaleAtNatOriginGeoKey, 1.0));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjFalseEastingGeoKey, 0.0)); // LOOK why not FalseNorthing ??
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjFalseNorthingGeoKey, 0.0));
  }

  private void addMercatorTags(Mercator proj) {
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTModelTypeGeoKey, GeoKey.TagValue.ModelType_Projected));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTRasterTypeGeoKey, GeoKey.TagValue.RasterType_Area));

    // define the "geographic Coordinate System"
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeographicTypeGeoKey, GeoKey.TagValue.GeographicType_WGS_84));
    // geotiff.addGeoKey( new GeoKey( GeoKey.Tag.GeogLinearUnitsGeoKey, GeoKey.TagValue.ProjLinearUnits_METER));
    // geotiff.addGeoKey( new GeoKey( GeoKey.Tag.GeogAngularUnitsGeoKey, GeoKey.TagValue.GeogAngularUnits_DEGREE));

    // define the "coordinate transformation"
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjectedCSTypeGeoKey, GeoKey.TagValue.ProjectedCSType_UserDefined));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.PCSCitationGeoKey, "Mercator"));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjectionGeoKey, GeoKey.TagValue.ProjectedCSType_UserDefined));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjLinearUnitsGeoKey, GeoKey.TagValue.ProjLinearUnits_METER));
    // geotiff.addGeoKey( new GeoKey( GeoKey.Tag.ProjLinearUnitsSizeGeoKey, 1.0)); // units of km

    // the specifics for mercator
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjCoordTransGeoKey, GeoKey.TagValue.ProjCoordTrans_Mercator));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjNatOriginLongGeoKey, proj.getOriginLon()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjNatOriginLatGeoKey, proj.getParallel()));
    // geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjStdParallel1GeoKey, proj.getParallel()));
    // geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjScaleAtNatOriginGeoKey, 1));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjFalseEastingGeoKey, 0.0));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjFalseNorthingGeoKey, 0.0));
  }

  private void addTransverseMercatorTags(TransverseMercator proj) {
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTModelTypeGeoKey, GeoKey.TagValue.ModelType_Projected));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTRasterTypeGeoKey, GeoKey.TagValue.RasterType_Area));

    // define the "geographic Coordinate System"
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeographicTypeGeoKey, GeoKey.TagValue.GeographicType_WGS_84));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeogLinearUnitsGeoKey, GeoKey.TagValue.ProjLinearUnits_METER));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeogAngularUnitsGeoKey, GeoKey.TagValue.GeogAngularUnits_DEGREE));

    // define the "coordinate transformation"
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjectedCSTypeGeoKey, GeoKey.TagValue.ProjectedCSType_UserDefined));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.PCSCitationGeoKey, "Transvers Mercator"));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjectionGeoKey, GeoKey.TagValue.ProjectedCSType_UserDefined));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjLinearUnitsGeoKey, GeoKey.TagValue.ProjLinearUnits_METER));
    // geotiff.addGeoKey( new GeoKey( GeoKey.Tag.ProjLinearUnitsSizeGeoKey, 1.0)); // units of km

    // the specifics for mercator
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjCoordTransGeoKey, GeoKey.TagValue.ProjCoordTrans_TransverseMercator));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjNatOriginLatGeoKey, proj.getOriginLat()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjNatOriginLongGeoKey, proj.getTangentLon()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjScaleAtNatOriginGeoKey, proj.getScale()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjScaleAtNatOriginGeoKey, 1.0));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjFalseEastingGeoKey, 0.0));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjFalseNorthingGeoKey, 0.0));
  }

  private void addAlbersEqualAreaEllipseTags(AlbersEqualAreaEllipse proj) {
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTModelTypeGeoKey, GeoKey.TagValue.ModelType_Projected));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTRasterTypeGeoKey, GeoKey.TagValue.RasterType_Area));

    // define the "geographic Coordinate System"
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeographicTypeGeoKey, GeoKey.TagValue.GeographicType_WGS_84));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeogLinearUnitsGeoKey, GeoKey.TagValue.ProjLinearUnits_METER));

    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeogSemiMajorAxisGeoKey, proj.getEarth().getMajor()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeogSemiMinorAxisGeoKey, proj.getEarth().getMinor()));

    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeogAngularUnitsGeoKey, GeoKey.TagValue.GeogAngularUnits_DEGREE));

    // define the "coordinate transformation"
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjectedCSTypeGeoKey, GeoKey.TagValue.ProjectedCSType_UserDefined));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.PCSCitationGeoKey, "Albers Conial Equal Area"));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjectionGeoKey, GeoKey.TagValue.ProjectedCSType_UserDefined));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjLinearUnitsGeoKey, GeoKey.TagValue.ProjLinearUnits_METER));
    // geotiff.addGeoKey( new GeoKey( GeoKey.Tag.ProjLinearUnitsSizeGeoKey, 1.0)); // units of km

    // the specifics for mercator
    geotiff
        .addGeoKey(new GeoKey(GeoKey.Tag.ProjCoordTransGeoKey, GeoKey.TagValue.ProjCoordTrans_AlbersEqualAreaEllipse));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjNatOriginLatGeoKey, proj.getOriginLat()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjNatOriginLongGeoKey, proj.getOriginLon()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjStdParallel1GeoKey, proj.getParallelOne()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjStdParallel2GeoKey, proj.getParallelTwo()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjFalseEastingGeoKey, 0.0));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjFalseNorthingGeoKey, 0.0));
  }

  private void addAlbersEqualAreaTags(AlbersEqualArea proj) {
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTModelTypeGeoKey, GeoKey.TagValue.ModelType_Projected));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GTRasterTypeGeoKey, GeoKey.TagValue.RasterType_Area));

    // define the "geographic Coordinate System"
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeographicTypeGeoKey, GeoKey.TagValue.GeographicType_WGS_84));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeogLinearUnitsGeoKey, GeoKey.TagValue.ProjLinearUnits_METER));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.GeogAngularUnitsGeoKey, GeoKey.TagValue.GeogAngularUnits_DEGREE));

    // define the "coordinate transformation"
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjectedCSTypeGeoKey, GeoKey.TagValue.ProjectedCSType_UserDefined));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.PCSCitationGeoKey, "Albers Conial Equal Area"));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjectionGeoKey, GeoKey.TagValue.ProjectedCSType_UserDefined));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjLinearUnitsGeoKey, GeoKey.TagValue.ProjLinearUnits_METER));
    // geotiff.addGeoKey( new GeoKey( GeoKey.Tag.ProjLinearUnitsSizeGeoKey, 1.0)); // units of km

    // the specifics for mercator
    geotiff
        .addGeoKey(new GeoKey(GeoKey.Tag.ProjCoordTransGeoKey, GeoKey.TagValue.ProjCoordTrans_AlbersConicalEqualArea));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjNatOriginLatGeoKey, proj.getOriginLat()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjNatOriginLongGeoKey, proj.getOriginLon()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjStdParallel1GeoKey, proj.getParallelOne()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjStdParallel2GeoKey, proj.getParallelTwo()));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjFalseEastingGeoKey, 0.0));
    geotiff.addGeoKey(new GeoKey(GeoKey.Tag.ProjFalseNorthingGeoKey, 0.0));
  }

  private void dump(Array data, int col) {
    int[] shape = data.getShape();
    Index ima = data.getIndex();

    for (int j = 0; j < shape[0]; j++) {
      float dd = data.getFloat(ima.set(j, col));
      System.out.println(j + " value= " + dd);
    }
  }

  // LOOK WTF ?? is this the seam crossing ??
  private double geoShiftGetXstart(Array lon, double inc) {
    Index ilon = lon.getIndex();
    int[] lonShape = lon.getShape();
    IndexIterator lonIter = lon.getIndexIterator();
    double xlon;

    LatLonPoint p0 = LatLonPoint.create(0, lon.getFloat(ilon.set(0)));
    LatLonPoint pN = LatLonPoint.create(0, lon.getFloat(ilon.set(lonShape[0] - 1)));

    xlon = p0.getLongitude();
    while (lonIter.hasNext()) {
      float l = lonIter.getFloatNext();
      LatLonPoint pn = LatLonPoint.create(0, l);
      if (pn.getLongitude() < xlon) {
        xlon = pn.getLongitude();
      }
    }

    if (p0.getLongitude() == pN.getLongitude()) {
      xlon = xlon - inc;
    }

    return xlon;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  /**
   * Write GridCoverage data to the geotiff file.
   *
   * Greyscale mode will auto-normalize the data from 1 to 255 and save as unsigned bytes, with 0's used
   * for missing data. A color table can be applied if specified via `setColorTable()`.
   * Non-greyscale mode will save the data as floats, encoding missing data as the data minimum minus one.
   *
   * @param array GeoReferencedArray array in YX order
   * @param greyScale if true, write greyScale image, else dataSample.
   * @throws IOException on i/o error
   */
  public void writeGrid(GeoReferencedArray array, boolean greyScale) throws IOException {
    writeGrid(array, greyScale, greyScale ? DataType.UBYTE : DataType.FLOAT);
  }

  /**
   * Write GridCoverage data to the geotiff file.
   *
   * Greyscale mode will auto-normalize the data from 1 to 255 and save as unsigned bytes, with 0's used
   * for missing data.
   * Non-greyscale mode with a floating point dtype will save the data as floats, encoding missing data
   * as the data's minimum minus one. Any other dtype will save the data coerced to the specified dtype.
   *
   * A color table can be applied if specified via `setColorTable()` and the dtype is UBYTE.
   *
   * @param array GeoReferencedArray array in YX order
   * @param greyScale if true, write greyScale image, else dataSample.
   * @param dtype if greyScale is false, then save the data in the given data type.
   *        Currently, this is a bit hobbled in order to avoid back-compatibility breaks.
   *        If greyScale is true and this is not UBYTE, then an exception is thrown.
   *        If dtype is DOUBLE, it downcasted to FLOAT instead.
   *        If using the colorTable and this is not UBYTE, then an exception is thrown.
   *        If null, then use the datatype of the given array.
   * @throws IOException on i/o error
   * @throws IllegalArgumentException if data isn't regular or if contradicting the greyScale argument.
   */
  public void writeGrid(GeoReferencedArray array, boolean greyScale, DataType dtype)
      throws IOException, IllegalArgumentException {

    // This check has to be *before* resolving the dtype so that we are only
    // checking explicitly specified data types.
    if (greyScale && dtype != DataType.UBYTE) {
      throw new IllegalArgumentException("When greyScale is true, dtype must be UBYTE");
    }

    if (colorTable != null && colorTable.length > 0 && dtype != DataType.UBYTE) {
      throw new IllegalArgumentException("When using the colorTable, the dtype must be UBYTE");
    }

    CoverageCoordSys gcs = array.getCoordSysForData();
    if (!gcs.isRegularSpatial())
      throw new IllegalArgumentException("Must have 1D x and y axes for " + array.getCoverageName());

    Projection proj = gcs.getProjection();
    CoverageCoordAxis1D xaxis = (CoverageCoordAxis1D) gcs.getXAxis();
    CoverageCoordAxis1D yaxis = (CoverageCoordAxis1D) gcs.getYAxis();

    // latlon coord does not need to be scaled
    double scaler = (xaxis.getUnits().equalsIgnoreCase("km")) ? 1000.0 : 1.0;

    // data must go from top to bottom
    double xStart = xaxis.getCoordEdge1(0) * scaler;
    double yStart = yaxis.getCoordEdge1(0) * scaler;
    double xInc = xaxis.getResolution() * scaler;
    double yInc = Math.abs(yaxis.getResolution()) * scaler;

    Array data = array.getData().reduce();
    if (yaxis.getCoordMidpoint(0) < yaxis.getCoordMidpoint(1)) {
      data = data.flip(0);
      yStart = yaxis.getCoordEdgeLast() * scaler;
    }

    if (dtype == null) {
      dtype = data.getDataType();
      // Need to cap at single precision floats because that's what gets written for floating points
      if (dtype == DataType.DOUBLE) {
        dtype = DataType.FLOAT;
      }
    }

    /*
     * remove - i think unneeded, monotonic lon handled in CoordinateAxis1D. JC 3/18/2013
     * if (gcs.isLatLon()) {
     * Array lon = xaxis.read();
     * data = geoShiftDataAtLon(data, lon);
     * xStart = geoShiftGetXstart(lon, xInc);
     * //xStart = -180.0;
     * }
     */

    if (pageNumber > 1) {
      geotiff.initTags();
    }

    // write the data first
    int nextStart;
    MAMath.MinMax dataMinMax = MAMath.getMinMaxSkipMissingData(data, array);
    if (greyScale) {
      data = replaceMissingValuesAndScale(array, data, dataMinMax);
      nextStart = writeData(data, DataType.UBYTE);
    } else if (dtype == DataType.FLOAT) {
      // Backwards compatibility shim
      data = replaceMissingValues(array, data, dataMinMax);
      nextStart = writeData(data, dtype);
    } else {
      data = coerceData(data, dtype);
      nextStart = writeData(data, dtype);
    }

    // set the width and the height
    int height = data.getShape()[0]; // Y
    int width = data.getShape()[1]; // X

    writeMetadata(greyScale, xStart, yStart, xInc, yInc, height, width, pageNumber, nextStart, dataMinMax, proj, dtype);
    pageNumber++;
  }

  static Array coerceData(Array data, DataType dtype) {
    if (dtype == DataType.BYTE || dtype == DataType.UBYTE) {
      data = coerceByte(data, dtype.isUnsigned());
    } else if (dtype == DataType.SHORT || dtype == DataType.USHORT) {
      data = coerceShort(data, dtype.isUnsigned());
    } else if (dtype == DataType.INT || dtype == DataType.UINT) {
      data = coerceInt(data, dtype.isUnsigned());
    } else if (dtype.isFloatingPoint()) {
      data = coerceFloat(data);
    }
    return data;
  }

  private int writeData(Array data, DataType dtype) throws IOException {
    int nextStart;
    if (dtype == DataType.BYTE || dtype == DataType.UBYTE) {
      nextStart = geotiff.writeData((byte[]) data.getStorage(), pageNumber);
    } else if (dtype == DataType.SHORT || dtype == DataType.USHORT) {
      nextStart = geotiff.writeData((short[]) data.getStorage(), pageNumber);
    } else if (dtype == DataType.INT || dtype == DataType.UINT) {
      nextStart = geotiff.writeData((int[]) data.getStorage(), pageNumber);
    } else {
      nextStart = geotiff.writeData((float[]) data.getStorage(), pageNumber);
    }
    return nextStart;
  }
}

