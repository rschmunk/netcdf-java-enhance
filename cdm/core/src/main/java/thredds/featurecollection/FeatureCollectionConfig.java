/*
 * Copyright (c) 1998 - 2010. University Corporation for Atmospheric Research/Unidata
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation. Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package thredds.featurecollection;

import org.jdom2.Element;
import org.jdom2.Namespace;
import thredds.inventory.*;
import ucar.nc2.time.CalendarPeriod;
import ucar.unidata.util.StringUtil2;
import java.util.*;

/**
 * FeatureCollection configuration
 *
 * @author caron
 * @since Mar 30, 2010
 */
public class FeatureCollectionConfig {
  // keys for storing AuxInfo objects
  // static public final String AUX_GRIB_CONFIG = "gribConfig";
  public static final String AUX_CONFIG = "fcConfig";

  public enum ProtoChoice {
    First, Random, Latest, Penultimate, Run
  }

  public enum FmrcDatasetType {
    TwoD, Best, Files, Runs, ConstantForecasts, ConstantOffsets
  }

  public enum PointDatasetType {
    cdmrFeature, Files
  }

  public enum GribDatasetType {
    TwoD, Best, Analysis, Files, Latest, LatestFile
  }

  public enum PartitionType {
    none, directory, file, timePeriod, all
  }

  private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FeatureCollectionConfig.class);

  //////////////////////////////////////////////

  public String name, path, spec, collectionName, dateFormatMark, olderThan;
  private String rootDir, regExp;
  public FeatureCollectionType type;
  public PartitionType ptype = PartitionType.directory;
  public CalendarPeriod timePeriod;
  public UpdateConfig tdmConfig = new UpdateConfig();
  public UpdateConfig updateConfig = new UpdateConfig();
  public ProtoConfig protoConfig = new ProtoConfig();
  public FmrcConfig fmrcConfig = new FmrcConfig();
  public PointConfig pointConfig = new PointConfig();
  public GribConfig gribConfig = new GribConfig();
  public Element innerNcml;
  public Optional<Boolean> filesSortIncreasing = Optional.empty();

  public FeatureCollectionConfig() {}

  public FeatureCollectionConfig(String name, String path, FeatureCollectionType fcType, String spec,
      String collectionName, String dateFormatMark, String olderThan, String timePartition, Element innerNcml) {
    this.name = name;
    this.path = StringUtil2.trim(path, '/');
    this.type = fcType;
    this.spec = spec;
    this.collectionName = collectionName == null ? name : collectionName;
    this.dateFormatMark = dateFormatMark;
    this.olderThan = olderThan;
    if (null != timePartition) {
      if (timePartition.equalsIgnoreCase("none"))
        ptype = PartitionType.none;
      else if (timePartition.equalsIgnoreCase("directory"))
        ptype = PartitionType.directory;
      else if (timePartition.equalsIgnoreCase("file"))
        ptype = PartitionType.file;
      else if (timePartition.equalsIgnoreCase("all"))
        ptype = PartitionType.all;
      else {
        timePeriod = CalendarPeriod.of(timePartition);
        ptype = PartitionType.timePeriod;
        if (timePeriod == null)
          throw new IllegalArgumentException("Illegal timePeriod= " + timePartition);
      }
    }
    this.innerNcml = innerNcml;
  }

  public void setFilter(String rootDir, String regExp) {
    this.rootDir = rootDir;
    this.regExp = regExp;
  }

  public void setFilesSort(Element filesSortElem) {
    if (filesSortElem == null)
      return;
    String increasingS = filesSortElem.getAttributeValue("increasing");
    if (increasingS != null) {
      if (increasingS.equalsIgnoreCase("true"))
        filesSortIncreasing = Optional.of(true);
      else if (increasingS.equalsIgnoreCase("false"))
        filesSortIncreasing = Optional.of(false);
    }
  }

  public boolean getSortFilesAscending() {
    if (filesSortIncreasing.isPresent())
      return filesSortIncreasing.get();
    if (gribConfig != null && gribConfig.filesSortIncreasing.isPresent())
      return gribConfig.filesSortIncreasing.get();
    return true; // default true
  }

  public CollectionSpecParserAbstract getCollectionSpecParserAbstract(Formatter errlog) {
    if (rootDir != null) {
      return CollectionSpecParsers.create(rootDir, regExp, errlog);
    } else {
      return CollectionSpecParsers.create(spec, errlog);
    }
  }

  public CollectionSpecParser getCollectionSpecParser(Formatter errlog) {
    if (rootDir != null)
      return new CollectionSpecParser(rootDir, regExp, errlog);
    else
      return new CollectionSpecParser(spec, errlog);
  }

  public String getCollectionName() {
    return collectionName;
  }

  public boolean isTrigggerOk() {
    return updateConfig.triggerOk || (tdmConfig != null) && tdmConfig.triggerOk;
  }

  @Override
  public String toString() {
    Formatter f = new Formatter();
    f.format("FeatureCollectionConfig name ='%s' collectionName='%s' type='%s'%n", name, collectionName, type);
    f.format("  spec='%s'%n", spec);
    if (rootDir != null)
      f.format("  rootDir= '%s' regExp= '%s'%n", rootDir, regExp);
    if (dateFormatMark != null)
      f.format("  dateFormatMark ='%s'%n", dateFormatMark);
    if (olderThan != null)
      f.format("  olderThan =%s%n", olderThan);
    f.format("  timePartition =%s%n", ptype);
    if (updateConfig != null)
      f.format("  updateConfig =%s%n", updateConfig);
    if (tdmConfig != null)
      f.format("  tdmConfig =%s%n", tdmConfig);
    if (protoConfig != null)
      f.format("  %s%n", protoConfig);
    f.format("  hasInnerNcml =%s%n", innerNcml != null);

    if (type != null) {
      switch (type) {
        case GRIB1:
        case GRIB2:
          f.format("  %s%n", gribConfig);
          break;
        case FMRC:
          f.format("  fmrcConfig =%s%n", fmrcConfig);
          break;
        case Point:
        case Station:
        case Station_Profile:
          f.format("  pointConfig =%s%n", pointConfig);
          break;
      }
    }

    return f.toString();
  }

  public void show(Formatter f) {
    f.format("FeatureCollectionConfig name= '%s' collectionName= '%s' type= '%s'%n", name, collectionName, type);
    f.format("  spec= '%s'%n", spec);
    if (rootDir != null)
      f.format("  rootDir= '%s' regExp= '%s'%n", rootDir, regExp);
    if (dateFormatMark != null)
      f.format("  dateFormatMark='%s'%n", dateFormatMark);
    if (olderThan != null)
      f.format("  olderThan= %s%n", olderThan);
    if (ptype == PartitionType.timePeriod)
      f.format("  timePartition= %s %n", timePeriod);
    else
      f.format("  timePartition= %s%n", ptype);

    if (type != null) {
      switch (type) {
        case GRIB1:
        case GRIB2:
          gribConfig.show(f);
          break;
        case FMRC:
          f.format("  fmrcConfig= %s%n", fmrcConfig);
          break;
        case Point:
        case Station:
        case Station_Profile:
          f.format("  pointConfig= %s%n", pointConfig);
          break;
      }
    }

  }

  // finished reading - do anything needed
  public void finish() {
    // if tdm element was not specified, default is test
    if (!tdmConfig.userDefined)
      tdmConfig.updateType = CollectionUpdateType.test;

    // if update element was not specified, set default
    if (!updateConfig.userDefined) {
      // if tdm working, default tds is never, otherwise nocheck
      updateConfig.updateType = tdmConfig.userDefined ? CollectionUpdateType.never : CollectionUpdateType.nocheck;
    }

    // startupType allows override on tdm command line
    updateConfig.startupType = updateConfig.updateType;
    tdmConfig.startupType = tdmConfig.updateType;
  }

  public DateExtractor getDateExtractor() {
    if (dateFormatMark != null)
      return new DateExtractorFromName(dateFormatMark, false);
    else {
      CollectionSpecParserAbstract sp = getCollectionSpecParserAbstract(null);
      if (sp.getDateFormatMark() != null)
        return new DateExtractorFromName(sp.getDateFormatMark(), true);
    }
    return new DateExtractorNone();
  }

  // <update startup="nocheck" rescan="cron expr" trigger="allow" recheckAfter="15 min"/>
  public static class UpdateConfig {
    public String recheckAfter; // used by non-GRIB FC
    public String rescan;
    public boolean triggerOk = true;
    public boolean userDefined;
    public CollectionUpdateType startupType = CollectionUpdateType.never; // same as updateType, except may be
                                                                          // overridden on the command line, for startup
                                                                          // only
    public CollectionUpdateType updateType = CollectionUpdateType.never; // this is what the user entered in config
    public String deleteAfter; // not implemented yet

    public UpdateConfig() { // defaults
    }

    public UpdateConfig(String startupS, String rewriteS, String recheckAfter, String rescan, String triggerS,
        String deleteAfter) {
      this.rescan = rescan; // may be null
      if (recheckAfter != null)
        this.recheckAfter = recheckAfter; // in case it was set in collection element
      if (rescan != null)
        this.recheckAfter = null; // both not allowed
      this.deleteAfter = deleteAfter; // may be null
      if (triggerS != null)
        this.triggerOk = triggerS.equalsIgnoreCase("allow");

      // rewrite superceeds startup
      if (rewriteS == null)
        rewriteS = startupS;
      if (rewriteS != null) {
        rewriteS = rewriteS.toLowerCase();
        if (rewriteS.equalsIgnoreCase("true"))
          this.updateType = CollectionUpdateType.test;
        else
          try {
            this.updateType = CollectionUpdateType.valueOf(rewriteS);
          } catch (Throwable t) {
            log.error("Bad updateType= {}", rewriteS);
          }

        // user has placed an update/tdm element in the catalog
        userDefined = true;
      }
    }

    @Override
    public String toString() {
      return "UpdateConfig{" + "userDefined=" + userDefined + ", recheckAfter='" + recheckAfter + '\'' + ", rescan='"
          + rescan + '\'' + ", triggerOk=" + triggerOk + ", updateType=" + updateType + '}';
    }
  }

  // <protoDataset choice="First | Random | Penultimate | Latest | Run" param="0" change="expr" />
  public static class ProtoConfig {
    public ProtoChoice choice = ProtoChoice.Penultimate;
    public String param;
    public String change;
    public Element outerNcml;
    public boolean cacheAll = true;

    public ProtoConfig() { // defaults
    }

    public ProtoConfig(String choice, String change, String param, Element ncml) {
      if (choice != null) {
        try {
          this.choice = ProtoChoice.valueOf(choice);
        } catch (Exception e) {
          log.warn("Dont recognize ProtoChoice " + choice);
        }
      }

      this.change = change;
      this.param = param;
      this.outerNcml = ncml;
    }

    @Override
    public String toString() {
      return "ProtoConfig{" + "choice=" + choice + ", change='" + change + '\'' + ", param='" + param + '\''
          + ", outerNcml='" + outerNcml + '\'' + ", cacheAll=" + cacheAll + '}';
    }
  }

  ////////////////////////////////////////////////

  //
  public static void setRegularizeDefault(boolean t) {
    regularizeDefault = t;
  }

  // public static boolean getRegularizeDefault() {
  // return regularizeDefault;
  // }

  private static boolean regularizeDefault;

  private static Set<FmrcDatasetType> defaultFmrcDatasetTypes = Collections.unmodifiableSet(
      EnumSet.of(FmrcDatasetType.TwoD, FmrcDatasetType.Best, FmrcDatasetType.Files, FmrcDatasetType.Runs));

  public static class FmrcConfig {
    public boolean regularize = regularizeDefault;
    public Set<FmrcDatasetType> datasets = defaultFmrcDatasetTypes;
    private boolean explicit;
    private List<BestDataset> bestDatasets;

    public FmrcConfig() { // defaults
    }

    public FmrcConfig(String regularize) {
      this.regularize = "true".equalsIgnoreCase(regularize);
    }

    public void addDatasetType(String datasetTypes) {
      // if they list datasetType explicitly, remove defaults
      if (!explicit)
        datasets = EnumSet.noneOf(FmrcDatasetType.class);
      explicit = true;

      String[] types = StringUtil2.splitString(datasetTypes);
      for (String type : types) {
        try {
          FmrcDatasetType fdt = FmrcDatasetType.valueOf(type);
          datasets.add(fdt);
        } catch (Exception e) {
          log.warn("Dont recognize FmrcDatasetType " + type);
        }
      }
    }

    public void addBestDataset(String name, double greaterEqual) {
      if (bestDatasets == null)
        bestDatasets = new ArrayList<>(2);
      bestDatasets.add(new BestDataset(name, greaterEqual));
    }

    public List<BestDataset> getBestDatasets() {
      return bestDatasets;
    }

    @Override
    public String toString() {
      Formatter f = new Formatter();
      f.format("FmrcConfig: regularize=%s datasetTypes=%s", regularize, datasets);
      if (bestDatasets != null)
        for (BestDataset bd : bestDatasets)
          f.format("best = (%s, %f) ", bd.name, bd.greaterThan);
      return f.toString();
    }
  }

  public static class BestDataset {
    public String name;
    public double greaterThan;

    public BestDataset(String name, double greaterThan) {
      this.name = name;
      this.greaterThan = greaterThan;
    }

  }

  private static Set<PointDatasetType> defaultPointDatasetTypes =
      Collections.unmodifiableSet(EnumSet.of(PointDatasetType.cdmrFeature, PointDatasetType.Files));

  public static class PointConfig {
    public Set<PointDatasetType> datasets = defaultPointDatasetTypes;
    protected boolean explicit;

    public void addDatasetType(String datasetTypes) {
      // if they list datasetType explicitly, remove defaults
      if (!explicit)
        datasets = EnumSet.noneOf(PointDatasetType.class);
      explicit = true;

      String[] types = StringUtil2.splitString(datasetTypes);
      for (String type : types) {
        try {
          PointDatasetType fdt = PointDatasetType.valueOf(type);
          datasets.add(fdt);
        } catch (Exception e) {
          log.warn("Dont recognize PointDatasetType " + type);
        }
      }
    }

    @Override
    public String toString() {
      Formatter f = new Formatter();
      f.format("PointConfig: datasetTypes=%s", datasets);
      return f.toString();
    }
  }

  // GribConfig

  private static final Set<GribDatasetType> defaultGribDatasetTypes = Collections.unmodifiableSet(
      EnumSet.of(GribDatasetType.TwoD, GribDatasetType.Best, GribDatasetType.Files, GribDatasetType.Latest));

  public static boolean useGenTypeDef, useTableVersionDef, intvMergeDef = true, useCenterDef;

  public static class GribConfig {

    public Map<Integer, Integer> gdsHash; // map one gds hash to another
    public Map<Integer, String> gdsNamer; // hash, group name
    public boolean useGenType = useGenTypeDef;
    public boolean useTableVersion = useTableVersionDef;
    public boolean intvMerge = intvMergeDef;
    public boolean useCenter = useCenterDef;
    public boolean unionRuntimeCoord;

    public GribIntvFilter intvFilter;
    public TimeUnitConverterHash tuc;
    public CalendarPeriod userTimeUnit;

    // late binding
    public String latestNamer, bestNamer;
    private Optional<Boolean> filesSortIncreasing = Optional.empty();
    public Set<GribDatasetType> datasets = defaultGribDatasetTypes;

    public String lookupTablePath, paramTablePath; // user defined tables
    public Element paramTable; // ??

    private boolean explicitDatasets;

    public TimeUnitConverter getTimeUnitConverter() {
      return tuc;
    }

    public void configFromXml(Element configElem, Namespace ns) {
      String datasetTypes = configElem.getAttributeValue("datasetTypes");
      if (null != datasetTypes)
        addDatasetType(datasetTypes);

      List<Element> gdsElems = configElem.getChildren("gdsHash", ns);
      for (Element gds : gdsElems)
        addGdsHash(gds.getAttributeValue("from"), gds.getAttributeValue("to"));

      List<Element> tuElems = configElem.getChildren("timeUnitConvert", ns);
      for (Element tu : tuElems)
        addTimeUnitConvert(tu.getAttributeValue("from"), tu.getAttributeValue("to"));

      gdsElems = configElem.getChildren("gdsName", ns);
      for (Element gds : gdsElems)
        addGdsName(gds.getAttributeValue("hash"), gds.getAttributeValue("groupName"));

      if (configElem.getChild("parameterMap", ns) != null)
        paramTable = configElem.getChild("parameterMap", ns);
      if (configElem.getChild("gribParameterTable", ns) != null)
        paramTablePath = configElem.getChildText("gribParameterTable", ns);
      if (configElem.getChild("gribParameterTableLookup", ns) != null)
        lookupTablePath = configElem.getChildText("gribParameterTableLookup", ns);
      if (configElem.getChild("latestNamer", ns) != null)
        latestNamer = configElem.getChild("latestNamer", ns).getAttributeValue("name");
      if (configElem.getChild("bestNamer", ns) != null)
        bestNamer = configElem.getChild("bestNamer", ns).getAttributeValue("name");

      // old way - filesSort element inside the gribConfig element
      Element filesSortElem = configElem.getChild("filesSort", ns);
      if (filesSortElem != null) {
        // String orderByS = filesSortElem.getAttributeValue("orderBy"); // filename vs date ??
        String increasingS = filesSortElem.getAttributeValue("increasing");
        if (increasingS != null) {
          if (increasingS.equalsIgnoreCase("true"))
            filesSortIncreasing = Optional.of(true);
          else if (increasingS.equalsIgnoreCase("false"))
            filesSortIncreasing = Optional.of(false);

        } else { // older way
          Element lexigraphicByName = filesSortElem.getChild("lexigraphicByName", ns);
          if (lexigraphicByName != null) {
            increasingS = lexigraphicByName.getAttributeValue("increasing");
            if (increasingS != null) {
              if (increasingS.equalsIgnoreCase("true"))
                filesSortIncreasing = Optional.of(true);
              else if (increasingS.equalsIgnoreCase("false"))
                filesSortIncreasing = Optional.of(false);
            }
          }
        }
      }

      List<Element> intvElems = configElem.getChildren("intvFilter", ns);
      for (Element intvElem : intvElems) {
        if (intvFilter == null)
          intvFilter = new GribIntvFilter();
        String excludeZero = intvElem.getAttributeValue("excludeZero");
        if (excludeZero != null)
          setExcludeZero(!excludeZero.equals("false"));
        String intervalS = intvElem.getAttributeValue("interval");
        if (intervalS != null)
          intvFilter.addInterval(intervalS);

        String intvLengthS = intvElem.getAttributeValue("intvLength");
        if (intvLengthS != null) {
          int intvLength = Integer.parseInt(intvLengthS);
          List<Element> varElems = intvElem.getChildren("variable", ns);
          for (Element varElem : varElems)
            intvFilter.addVariable(intvLength, varElem.getAttributeValue("id"), varElem.getAttributeValue("prob"));
        }
      }

      List<Element> paramElems = configElem.getChildren("option", ns);
      if (paramElems.isEmpty())
        paramElems = configElem.getChildren("parameter", ns); // backwards compatible
      for (Element param : paramElems) {
        String name = param.getAttributeValue("name");
        String value = param.getAttributeValue("value");
        if (name != null && value != null) {
          setOption(name, value);
        }
      }

      Element pdsHashElement = configElem.getChild("pdsHash", ns);
      useGenType = readValue(pdsHashElement, "useGenType", ns, useGenTypeDef);
      useTableVersion = readValue(pdsHashElement, "useTableVersion", ns, useTableVersionDef);
      intvMerge = readValue(pdsHashElement, "intvMerge", ns, intvMergeDef);
      useCenter = readValue(pdsHashElement, "useCenter", ns, useCenterDef);
    }

    public boolean setOption(String name, String value) {
      if (name == null || value == null)
        return false;

      if (name.equalsIgnoreCase("timeUnit")) {
        setUserTimeUnit(value); // eg "10 min" or "minute"
        return true;
      }
      if (name.equalsIgnoreCase("runtimeCoordinate") && value.equalsIgnoreCase("union")) {
        unionRuntimeCoord = true;
        return true;
      }
      return false;
    }

    public void setUserTimeUnit(String value) {
      if (value != null)
        userTimeUnit = CalendarPeriod.of(value); // eg "10 min" or "minute
    }

    public void setExcludeZero(boolean val) {
      if (intvFilter == null)
        intvFilter = new GribIntvFilter();
      intvFilter.isZeroExcluded = val;
    }

    public void setUseTableVersion(boolean val) {
      useTableVersion = val;
    }

    public void setIntervalLength(int intvLength, String varId) {
      if (intvFilter == null)
        intvFilter = new GribIntvFilter();
      intvFilter.addVariable(intvLength, varId, null);
    }

    private boolean readValue(Element pdsHashElement, String key, Namespace ns, boolean value) {
      if (pdsHashElement != null) {
        Element e = pdsHashElement.getChild(key, ns);
        if (e != null) {
          value = true; // no value means true
          String t = e.getTextNormalize();
          if ("true".equalsIgnoreCase(t))
            value = true;
          if ("false".equalsIgnoreCase(t))
            value = false;
        }
      }
      return value;
    }

    public void addDatasetType(String datasetTypes) {
      // if they list datasetType explicitly, remove defaults
      if (!explicitDatasets)
        datasets = EnumSet.noneOf(GribDatasetType.class);
      explicitDatasets = true;

      String[] types = StringUtil2.splitString(datasetTypes);
      for (String type : types) {
        try {
          GribDatasetType fdt = GribDatasetType.valueOf(type);
          if (fdt == GribDatasetType.LatestFile)
            fdt = GribDatasetType.Latest;
          datasets.add(fdt);
        } catch (Exception e) {
          log.warn("Dont recognize GribDatasetType {}", type);
        }
      }
    }

    public boolean hasDatasetType(GribDatasetType type) {
      return datasets.contains(type);
    }

    public void addGdsHash(String fromS, String toS) {
      if (fromS == null || toS == null)
        return;
      if (gdsHash == null)
        gdsHash = new HashMap<>(10);

      try {
        int from = Integer.parseInt(fromS);
        int to = Integer.parseInt(toS);
        gdsHash.put(from, to);
      } catch (Exception e) {
        log.warn("Failed  to parse as Integer = {} {}", fromS, toS);
      }
    }

    public void addTimeUnitConvert(String fromS, String toS) {
      if (fromS == null || toS == null)
        return;
      if (tuc == null)
        tuc = new TimeUnitConverterHash();

      try {
        int from = Integer.parseInt(fromS);
        int to = Integer.parseInt(toS);
        tuc.map.put(from, to);
      } catch (Exception e) {
        log.warn("Failed  to parse as Integer = {} {}", fromS, toS);
      }
    }

    public void addGdsName(String hashS, String name) {
      if (hashS == null || name == null)
        return;
      if (gdsNamer == null)
        gdsNamer = new HashMap<>(5);

      try {
        int hash = Integer.parseInt(hashS);
        gdsNamer.put(hash, name);
      } catch (Exception e) {
        log.warn("Failed  to parse as Integer = {} {}", hashS, name);
      }
    }

    public void show(Formatter f) {
      f.format("GribConfig= ");
      if (useGenType != useGenTypeDef)
        f.format(" useGenType=%s", useGenType);
      if (useTableVersion != useTableVersionDef)
        f.format(" useTableVersion=%s", useTableVersion);
      if (intvMerge != intvMergeDef)
        f.format(" intvMerge=%s", intvMerge);
      if (useCenter != useCenterDef)
        f.format(" useCenter=%s", useCenter);
      if (userTimeUnit != null)
        f.format(" userTimeUnit= %s", userTimeUnit);
      f.format("%n");
      if (gdsHash != null)
        f.format("  gdsHash=%s%n", gdsHash);
      if (gdsNamer != null)
        f.format("  gdsNamer=%s%n", gdsNamer);
      if (intvFilter != null)
        f.format("  intvFilter=%s%n", intvFilter);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("GribConfig{");
      sb.append("datasets=").append(datasets);
      if (gdsHash != null)
        sb.append(", gdsHash=").append(gdsHash);
      if (gdsNamer != null)
        sb.append(", gdsNamer=").append(gdsNamer);
      sb.append(", useGenType=").append(useGenType);
      sb.append(", useTableVersion=").append(useTableVersion);
      sb.append(", intvMerge=").append(intvMerge);
      sb.append(", useCenter=").append(useCenter);
      if (lookupTablePath != null)
        sb.append(", lookupTablePath='").append(lookupTablePath).append('\'');
      if (paramTablePath != null)
        sb.append(", paramTablePath='").append(paramTablePath).append('\'');
      if (latestNamer != null)
        sb.append(", latestNamer='").append(latestNamer).append('\'');
      if (bestNamer != null)
        sb.append(", bestNamer='").append(bestNamer).append('\'');
      if (paramTable != null)
        sb.append(", paramTable=").append(paramTable);
      if (filesSortIncreasing.isPresent())
        sb.append(", filesSortIncreasing=").append(filesSortIncreasing);
      if (intvFilter != null)
        sb.append(", intvFilter=").append(intvFilter);
      if (userTimeUnit != null)
        sb.append(", userTimeUnit='").append(userTimeUnit).append('\'');
      sb.append('}');
      return sb.toString();
    }

    public Object getIospMessage() {
      if (lookupTablePath != null)
        return "gribParameterTableLookup=" + lookupTablePath;
      if (paramTablePath != null)
        return "gribParameterTable=" + paramTablePath;
      return null;
    }

    public int convertGdsHash(int hashcode) {
      if (gdsHash == null)
        return hashcode;
      Integer convertedValue = gdsHash.get(hashcode);
      if (convertedValue == null)
        return hashcode;
      return convertedValue;
    }

  } // GribConfig

  //////////////////////////////////////////////////////////////////////////

  interface IntvFilter {
    // true means discard
    boolean filter(int id, int intvStart, int intvEnd, int prob);
  }

  static class IntvLengthFilter implements IntvFilter {
    public final int id;
    public final int intvLength;
    public final int prob; // none = Integer.MIN_VALUE;

    public IntvLengthFilter(int id, int intvLength, int prob) {
      this.id = id;
      this.intvLength = intvLength;
      this.prob = prob;
    }

    public boolean filter(int id, int intvStart, int intvEnd, int prob) {
      int intvLength = intvEnd - intvStart;
      boolean needProb = (this.prob != Integer.MIN_VALUE); // filter uses prob
      boolean hasProb = (prob != Integer.MIN_VALUE); // record has prob
      boolean isMine = !needProb || hasProb && (this.prob == prob);
      if (this.id == id && isMine) { // first match in the filter list is used
        return this.intvLength != intvLength; // remove the ones whose intervals dont match
      }
      return false;
    }
  }

  static class IntervalFilter implements IntvFilter {
    public final int start, end;

    public IntervalFilter(int start, int end) {
      this.start = start;
      this.end = end;
    }

    public boolean filter(int id, int intvStart, int intvEnd, int prob) {
      boolean match = (this.start == intvStart) && (this.end == intvEnd); // remove ones that match
      if (match) {
        log.info("interval filter applied id=" + id);
      }
      return match;
    }
  }

  public static class GribIntvFilter {
    public List<IntvFilter> filterList = new ArrayList<>();
    public boolean isZeroExcluded; // default is false 1/31/2019

    public boolean isZeroExcluded() {
      return isZeroExcluded;
    }

    public boolean hasFilter() {
      return (!filterList.isEmpty());
    }

    // true means discard
    public boolean filter(int id, int intvStart, int intvEnd, int prob) {
      int intvLength = intvEnd - intvStart;
      if (intvLength == 0 && isZeroExcluded())
        return true;

      for (IntvFilter filter : filterList) {
        if (filter.filter(id, intvStart, intvEnd, prob))
          return true;
      }
      return false;
    }

    // <intvFilter interval="225,228">
    void addInterval(String intervalS) {
      if (intervalS == null) {
        log.warn("Error on interval attribute: must not be empty");
        return;
      }

      String[] s = intervalS.split(",");
      if (s.length != 2) {
        log.warn("Error on interval attribute: must be of form 'start,end'");
        return;
      }

      try {
        int start = Integer.parseInt(s[0]);
        int end = Integer.parseInt(s[1]);

        filterList.add(new IntervalFilter(start, end));

      } catch (NumberFormatException e) {
        log.info("Error on intvFilter element - attribute must be an integer");
      }
    }

    /*
     * <intvFilter intvLength="12">
     * <variable id="0-1-8" prob="50800"/>
     * </intvFilter>
     * 
     * <intvFilter intvLength="3">
     * <variable id="0-1-8"/>
     * </intvFilter>
     */
    void addVariable(int intvLength, String idS, String probS) {
      if (idS == null) {
        log.warn("Error on intvFilter: must have an id attribute");
        return;
      }

      String[] s = idS.split("-");
      if (s.length != 3 && s.length != 4) {
        log.warn(
            "Error on intvFilter: id attribute must be of form 'discipline-category-number' (GRIB2) or 'center-subcenter-version-param' (GRIB1)");
        return;
      }

      try {
        int id;
        if (s.length == 3) { // GRIB2
          int discipline = Integer.parseInt(s[0]);
          int category = Integer.parseInt(s[1]);
          int number = Integer.parseInt(s[2]);
          id = (discipline << 16) + (category << 8) + number;
        } else { // GRIB1
          int center = Integer.parseInt(s[0]);
          int subcenter = Integer.parseInt(s[1]);
          int version = Integer.parseInt(s[2]);
          int param = Integer.parseInt(s[3]);
          id = (center << 8) + (subcenter << 16) + (version << 24) + param;
        }
        int prob = (probS == null) ? Integer.MIN_VALUE : Integer.parseInt(probS);

        filterList.add(new IntvLengthFilter(id, intvLength, prob));

      } catch (NumberFormatException e) {
        log.info("Error on intvFilter element - attribute must be an integer");
      }
    }

  }

  public static class TimeUnitConverterHash implements TimeUnitConverter {
    public Map<Integer, Integer> map = new HashMap<>(5);

    public int convertTimeUnit(int timeUnit) {
      if (map == null)
        return timeUnit;
      Integer convert = map.get(timeUnit);
      return (convert == null) ? timeUnit : convert;
    }
  }

}
