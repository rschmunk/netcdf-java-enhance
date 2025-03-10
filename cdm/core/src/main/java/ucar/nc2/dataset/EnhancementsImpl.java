/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.dataset;

import com.google.common.collect.ImmutableList;
import ucar.nc2.*;
import ucar.nc2.constants.CDM;
import ucar.nc2.constants.CF;
import java.util.*;

/**
 * Implementation of Enhancements for coordinate systems and standard attribute handling.
 * Factored out so that it can be used as a 'mixin' in VariablesDS and StructureDS.
 * 
 * @author caron
 * @deprecated do not use
 */
@Deprecated
class EnhancementsImpl implements Enhancements {

  private static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EnhancementsImpl.class);

  private Variable forVar;
  String desc, units;
  private List<CoordinateSystem> coordSys; // dont allocate unless its used

  /**
   * Constructor when there's no underlying, existing Variable.
   * You can access units, description and coordSys.
   * All missing and scale/offset flags are false.
   * 
   * @param forVar the Variable to decorate.
   * @param units set unit string.
   * @param desc set description.
   */
  EnhancementsImpl(Variable forVar, String units, String desc) {
    if (forVar instanceof StructureDS) {
      logger.trace("{} (1)", forVar.getShortName());
    }
    this.forVar = forVar;
    this.units = units;
    this.desc = desc;
  }

  /**
   * Constructor.
   * 
   * @param forVar the Variable to decorate.
   */
  EnhancementsImpl(Variable forVar) {
    if (forVar instanceof StructureDS) {
      logger.trace("{} (2)", forVar.getShortName());
    }
    this.forVar = forVar;
  }

  /**
   * Get the list of Coordinate Systems for this variable.
   * Normally this is empty unless you use ucar.nc2.dataset.NetcdfDataset.
   * 
   * @return list of type ucar.nc2.dataset.CoordinateSystem; may be empty not null.
   */
  public ImmutableList<CoordinateSystem> getCoordinateSystems() {
    if (forVar instanceof StructureDS) {
      logger.trace("{} has CS {}", forVar.getShortName(), (coordSys != null));
    }
    return (coordSys == null) ? ImmutableList.of() : ImmutableList.copyOf(coordSys);
  }

  /** Add a CoordinateSystem to the dataset. */
  public void addCoordinateSystem(CoordinateSystem cs) {
    if (forVar instanceof StructureDS) {
      logger.trace("{} add CS {}", forVar.getShortName(), cs);
    }
    if (cs == null) {
      throw new RuntimeException("Attempted to add null CoordinateSystem to var " + forVar.getFullName());
    }

    if (coordSys == null) {
      coordSys = new ArrayList<>(5);
    }
    coordSys.add(cs);
  }

  public void removeCoordinateSystem(ucar.nc2.dataset.CoordinateSystem p0) {
    if (coordSys != null) {
      coordSys.remove(p0);
    }
  }

  /**
   * Set the Description for this Variable.
   * 
   * @param desc description
   */
  public void setDescription(String desc) {
    if (forVar instanceof StructureDS) {
      logger.trace("{} setDescription {}", forVar.getShortName(), desc);
    }
    this.desc = desc;
  }

  /**
   * Get the description of the Variable.
   * Default is to look for attributes in this order: CDM.LONG_NAME, "description", "title", "standard_name".
   */
  public String getDescription() {
    if ((desc == null) && (forVar != null)) {
      Attribute att = forVar.findAttributeIgnoreCase(CDM.LONG_NAME);
      if ((att != null) && att.isString())
        desc = att.getStringValue();

      if (desc == null) {
        att = forVar.findAttributeIgnoreCase("description");
        if ((att != null) && att.isString())
          desc = att.getStringValue();
      }

      if (desc == null) {
        att = forVar.findAttributeIgnoreCase(CDM.TITLE);
        if ((att != null) && att.isString())
          desc = att.getStringValue();
      }

      if (desc == null) {
        att = forVar.findAttributeIgnoreCase(CF.STANDARD_NAME);
        if ((att != null) && att.isString())
          desc = att.getStringValue();
      }
    }
    return (desc == null) ? null : desc.trim();
  }

  /**
   * Set the Unit String for this Variable. Default is to use the CDM.UNITS attribute.
   * 
   * @param units unit string
   */
  public void setUnitsString(String units) {
    if (units != null) {
      units = units.trim();
    }
    this.units = units;
    forVar.addAttribute(new Attribute(CDM.UNITS, units));
  }

  /**
   * Get the Unit String for the Variable. May be set explicitly, else look for attribute CDM.UNITS.
   * 
   * @return the Unit String for the Variable, or null if none.
   */
  public String getUnitsString() {
    String result = null;
    if (forVar != null) {
      Attribute att = forVar.findAttributeIgnoreCase(CDM.UNITS);
      if ((att != null) && att.isString())
        result = att.getStringValue();
    }
    return (result == null) ? units : result.trim();
  }
}
