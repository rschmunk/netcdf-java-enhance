/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.ft2.coverage;

import ucar.nc2.Attribute;
import ucar.nc2.AttributeContainer;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.util.Indent;
import ucar.unidata.geoloc.LatLonRect;
import ucar.unidata.geoloc.ProjectionRect;
import javax.annotation.concurrent.Immutable;
import java.io.Closeable;
import java.io.IOException;
import java.util.*;

/**
 * A Collection of Coverages
 * Tracks unique coordinate systems.
 * Has a unique HorizCoordSys.
 * Has a unique Calendar.
 *
 * @author caron
 * @since 7/11/2015
 */
@Immutable
public class CoverageCollection implements Closeable, CoordSysContainer {

  private final String name;
  private final AttributeContainer atts;
  private final LatLonRect latLonBoundingBox;
  private final ProjectionRect projBoundingBox;
  private final CalendarDateRange calendarDateRange;

  private final List<CoordSysSet> coverageSets;
  private final List<CoverageCoordSys> coordSys;
  private final List<CoverageTransform> coordTransforms;
  private final List<CoverageCoordAxis> coordAxes;
  private final Map<String, Coverage> coverageMap = new HashMap<>();
  private final Map<String, CoverageCoordAxis> axisMap = new HashMap<>();

  private final FeatureType coverageType;
  protected final CoverageReader reader;
  protected final HorizCoordSys hcs;

  /**
   * Ctor
   * 
   * @param name CoverageCollection name
   * @param coverageType CoverageCollection type
   * @param atts CoverageCollection attributes
   * @param latLonBoundingBox if null, calculate
   * @param projBoundingBox if null, calculate
   * @param calendarDateRange need this to get the Calendar
   * @param coordSys list of coordinate systems
   * @param coordTransforms list of coordinate transforms
   * @param coordAxes list of coordinate axes
   * @param coverages list of coverages
   * @param reader delegate for reading
   */
  public CoverageCollection(String name, FeatureType coverageType, AttributeContainer atts,
      LatLonRect latLonBoundingBox, ProjectionRect projBoundingBox, CalendarDateRange calendarDateRange,
      List<CoverageCoordSys> coordSys, List<CoverageTransform> coordTransforms, List<CoverageCoordAxis> coordAxes,
      List<Coverage> coverages, CoverageReader reader) {
    this.name = name;
    this.atts = atts;
    this.calendarDateRange = calendarDateRange;
    this.coverageType = coverageType;

    this.coordSys = coordSys;
    this.coordTransforms = coordTransforms;
    this.coordAxes = coordAxes;

    this.coverageSets = wireObjectsTogether(coverages);
    this.hcs = wireHorizCoordSys();
    this.reader = reader;

    if (hcs.isProjection()) {
      if (projBoundingBox != null)
        this.projBoundingBox = projBoundingBox;
      else
        this.projBoundingBox = hcs.calcProjectionBoundingBox();
    } else {
      this.projBoundingBox = null;
    }

    if (latLonBoundingBox != null)
      this.latLonBoundingBox = latLonBoundingBox;
    else
      this.latLonBoundingBox = hcs.calcLatLonBoundingBox();
  }

  private List<CoordSysSet> wireObjectsTogether(List<Coverage> coverages) {
    for (CoverageCoordAxis axis : coordAxes)
      axisMap.put(axis.getName(), axis);
    for (CoverageCoordAxis axis : coordAxes)
      axis.setDataset(this);

    // wire dependencies
    Map<String, CoordSysSet> map = new HashMap<>();
    for (Coverage coverage : coverages) {
      coverageMap.put(coverage.getName(), coverage);
      CoordSysSet gset = map.get(coverage.getCoordSysName()); // duplicates get eliminated here
      if (gset == null) {
        CoverageCoordSys ccsys = findCoordSys(coverage.getCoordSysName());
        if (ccsys == null) {
          throw new IllegalStateException("Cant find " + coverage.getCoordSysName());
        }

        gset = new CoordSysSet(ccsys); // must use findByName because objects arent wired up yet
        map.put(coverage.getCoordSysName(), gset);
        gset.getCoordSys().setDataset(this); // wire dataset into coordSys
      }
      gset.addCoverage(coverage);
      coverage.setCoordSys(gset.getCoordSys()); // wire coordSys into coverage
    }

    // sort the coordsys sets
    List<CoordSysSet> csets = new ArrayList<>(map.values());
    csets.sort(Comparator.comparing(o -> o.getCoordSys().getName()));
    return csets;
  }

  private HorizCoordSys wireHorizCoordSys() {
    CoverageCoordSys csys1 = coordSys.get(0);
    HorizCoordSys hcs = csys1.makeHorizCoordSys();

    // we want them to share the same object for efficiency, esp 2D
    for (CoverageCoordSys csys : coordSys) {
      csys.setHorizCoordSys(hcs);
      csys.setImmutable();
    }
    return hcs;
  }

  public String getName() {
    return name;
  }

  /** Get the global attributes. */
  public AttributeContainer attributes() {
    return atts;
  }

  /** @deprecated use attributes() */
  @Deprecated
  public List<Attribute> getGlobalAttributes() {
    return atts.getAttributes();
  }

  /** @deprecated use attributes() */
  @Deprecated
  public String findAttValueIgnoreCase(String attName, String defaultValue) {
    return atts.findAttributeString(attName, defaultValue);
  }

  /** @deprecated use attributes() */
  @Deprecated
  public Attribute findAttribute(String attName) {
    return atts.findAttribute(attName);
  }

  /** @deprecated use attributes() */
  @Deprecated
  public Attribute findAttributeIgnoreCase(String attName) {
    return atts.findAttributeIgnoreCase(attName);
  }

  public LatLonRect getLatlonBoundingBox() {
    return latLonBoundingBox;
  }

  public ProjectionRect getProjBoundingBox() {
    return projBoundingBox;
  }

  public CalendarDateRange getCalendarDateRange() {
    return calendarDateRange;
  }

  public ucar.nc2.time.Calendar getCalendar() {
    if (calendarDateRange != null)
      return calendarDateRange.getStart().getCalendar(); // LOOK
    return ucar.nc2.time.Calendar.getDefault();
  }

  public Iterable<Coverage> getCoverages() {
    return coverageMap.values();
  }

  public int getCoverageCount() {
    return coverageMap.values().size();
  }

  public FeatureType getCoverageType() {
    return coverageType;
  }

  public List<CoordSysSet> getCoverageSets() {
    return coverageSets;
  }

  public List<CoverageCoordSys> getCoordSys() {
    return coordSys;
  }

  public List<CoverageTransform> getCoordTransforms() {
    return (coordTransforms != null) ? coordTransforms : new ArrayList<>();
  }

  public List<CoverageCoordAxis> getCoordAxes() {
    return coordAxes;
  }

  public HorizCoordSys getHorizCoordSys() {
    return hcs;
  }

  public CoverageReader getReader() {
    return reader;
  }

  @Override
  public String toString() {
    Formatter f = new Formatter();
    toString(f);
    return f.toString();
  }

  public void toString(Formatter f) {
    Indent indent = new Indent(2);
    f.format("%sGridDatasetCoverage %s%n", indent, name);
    f.format("%s Global attributes:%n", indent);
    for (Attribute att : atts) {
      f.format("%s  %s%n", indent, att);
    }
    f.format("%s Date Range:%s%n", indent, calendarDateRange);
    f.format("%s LatLon BoundingBox:%s%n", indent, latLonBoundingBox);
    if (projBoundingBox != null)
      f.format("%s Projection BoundingBox:%s%n", indent, projBoundingBox);

    f.format("%n%s Coordinate Systems:%n", indent);
    for (CoverageCoordSys cs : coordSys)
      cs.toString(f, indent);
    f.format("%s Coordinate Transforms:%n", indent);
    for (CoverageTransform t : coordTransforms)
      t.toString(f, indent);
    f.format("%s Coordinate Axes:%n", indent);
    for (CoverageCoordAxis a : coordAxes)
      a.toString(f, indent);

    f.format("%n%s Grids:%n", indent);
    for (Coverage grid : getCoverages())
      grid.toString(f, indent);
  }

  ////////////////////////////////////////////////////////////

  public Coverage findCoverage(String name) {
    return coverageMap.get(name);
  }

  public Coverage findCoverageByAttribute(String attName, String attValue) {
    for (Coverage cov : coverageMap.values()) {
      for (Attribute att : cov.attributes())
        if (attName.equals(att.getShortName()) && attValue.equals(att.getStringValue()))
          return cov;
    }
    return null;
  }

  public CoverageCoordSys findCoordSys(String name) {
    for (CoverageCoordSys gcs : coordSys)
      if (gcs.getName().equalsIgnoreCase(name))
        return gcs;
    return null;
  }

  public CoverageCoordAxis findCoordAxis(String name) {
    return axisMap.get(name);
  }

  public CoverageTransform findCoordTransform(String name) {
    for (CoverageTransform ct : coordTransforms)
      if (ct.getName().equalsIgnoreCase(name))
        return ct;
    return null;
  }

  public void close() throws IOException {
    try {
      reader.close();
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }
}
