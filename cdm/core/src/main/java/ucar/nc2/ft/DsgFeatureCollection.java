/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.ft;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import ucar.nc2.Variable;
import ucar.nc2.dataset.CoordinateAxis;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.time.CalendarDateUnit;
import java.util.List;

/**
 * A collection of FeatureTypes.
 * Will either be a PointFeatureCollection, PointFeatureCC, or PointFeatureCCC
 *
 * @author caron
 * @since Mar 20, 2008
 */
public interface DsgFeatureCollection {
  /**
   * Get the name of this feature collection.
   * 
   * @return the name of this feature collection
   */
  @Nonnull
  String getName();

  /**
   * All features in this collection have this feature type
   * 
   * @return the feature type
   */
  @Nonnull
  ucar.nc2.constants.FeatureType getCollectionFeatureType();

  /**
   * The name of time unit, if there is a time axis.
   *
   * @return name of time unit string, may be null
   */
  @Nullable
  String getTimeName();


  /**
   * The time unit, if there is a time axis.
   * 
   * @return time unit, may be null
   */
  @Nullable
  CalendarDateUnit getTimeUnit();

  /**
   * The altitude name string if it exists.
   *
   * @return altitude name string, may be null
   */
  @Nullable
  String getAltName();

  /**
   * The altitude unit string if it exists.
   * 
   * @return altitude unit string, may be null
   */
  @Nullable
  String getAltUnits();

  /**
   * The list of coordinate variables in the collection
   *
   * @return the list of coordinate variables, may be empty but not null;
   */
  @Nonnull
  List<CoordinateAxis> getCoordinateVariables();

  /**
   * Other variables needed for completeness, eg joined coordinate variables
   * 
   * @return list of extra variables, may be empty not null
   */
  @Nonnull
  List<Variable> getExtraVariables();

  /**
   * Calendar date range for the FeatureCollection. May not be known until after iterating through the collection.
   *
   * @return the calendar date range for the entire collection, or null if unknown
   */
  @Nullable
  CalendarDateRange getCalendarDateRange();

  /**
   * The boundingBox for the FeatureCollection. May not be known until after iterating through the collection.
   *
   * @return the lat/lon boundingBox for the entire collection, or null if unknown.
   */
  @Nullable
  ucar.unidata.geoloc.LatLonRect getBoundingBox();

  /**
   * The number of Features in the collection. May not be known until after iterating through the collection.
   * 
   * @return number of elements in the collection, or -1 if not known.
   */
  int size();

}
