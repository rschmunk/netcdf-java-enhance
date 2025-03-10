---
title: GRIB Tables in CDM
last_updated: 2021-06-10
sidebar: netcdfJavaTutorial_sidebar 
permalink: grib_tables.html
toc: false
---

### GRIB Tables in CDM

The following assumes that you want generic software like the netCDF-Java library to be able to correctly read the GRIB files that you write, or need to read.

In principle, if everything is done right, the reader ends up using the table that the writer used. In practice, there are many ways for that to fail. In order to increase the reliability of table-based file formats, I have proposed a web registry of tables, which would create a unique id for each registered table. The writer would then embed the id into the GRIB or BUFR message (possibly in the "local use" section of GRIB-2, or GRIB-1 octets > 41 in PDS), and the reader could use the id to unambiguously retrieve the table from the web registry. Stay tuned for further details and a trial implementation.

#### Contents:

* [Writing Grib Files](#writing-grib-files)
* [GRIB-1 Tables](#grib-1-table-handling)
* [XML Schema for defining Grib-1 Parameters](#xml-schema-for-defining-grib-1-parameters)

### Writing Grib Files
#### Encoding the center and subcenter id

You must encode a center and subcenter id, in order for software to correctly match any local tables used in the message. If there is no subcenter in use, use `id =0` ("no subcenter"), although 255 ("missing") is acceptable but ambiguous.

#### Encoding the version number(s)
**GRIB-1** : (octet 4 of the PDS) If you are only using WMO standard tables (all parameter ids < 128) then you should use version number = 1, 2, or 3, corresponding to the WMO standard table version. The [Current WMO GRIB-1 table version](https://community.wmo.int/activity-areas/wmo-codes/manual-codes/latest-version){:target="_blank"} is 3. Using only parameters from the standard tables is best practice for the _international exchange of GRIB messages_. If you are using non-standard (aka local) parameters, then you should use a version number in the range 128-254, which names your version of the tables.

**GRIB-2**: Use the correct version of the Master table in octect 10 of the Identification section. All parameters with numbers in the range 0-191 will be taken from that table. If you use any local tables, encode the version of your local table in octect 11 of the Identification section, otherwise set the local version to 0. _"In any case, the use of Local tables in messages intended for non-local or international exchange is strongly discouraged."_ from [WMO Manual on Codes](https://library.wmo.int/doc_num.php?explnum_id=10235){:target="_blank"}.

#### Using local tables
If you use local parameters, you must do the following:

1. You must own the center id, or own a subcenter id within the center, so that you can version your tables.
2. You must correctly version your local table number. If your local table changes in a way that is not backwards compatible, you must change the version number.
3. You must publish your local tables at some authoritative place, in a machine readable format. You must mantain these indefinitely.
4. You must put the correct center, subcenter and version numbers into each GRIB message.

Best practice for local table use includes the following:

1. For GRIB-1, use a version number between 128-254.
2. Do not override any entries in the WMO standard tables, ie, with parameter numbers less than 128 (GRIB-1) or 192 (GRIB-2).
3. There is no one standard for publishing your tables, but any fixed column ASCII, CSV, or XML format is ok. In the US, many follow the <a href="ftp://ftp.cpc.ncep.noaa.gov/wd51we/wgrib/usertables.txt">NCEP wgrib table format</a>. Use the [XML Schema for declaring Grib-1 Parameters](#xml-schema-for-defining-grib-1-parameters) if you like XML. Do not use HTML, PDF, MS Word etc, since these are not machine readable formats.
4. A parameter name is encouraged, and is added to the variable as an attribute with name "Grib_Parameter_Name".
5. Unidata encourages the use of <a href="https://www.unidata.ucar.edu/software/udunits/">udunit formatting</a> for expressing units.
6. We need to be able to find your tables. Add enough metadata so that a google search on "<your center name> GRIB tables" finds you within the first 2 pages. Send a link to netcdf-java@unidata.ucar.edu and we will link to you (and use your tables in the CDM library).

### GRIB-1 Table Handling

#### Standard table mapping

A standard table is a GRIB parameter table that is automatically used by the CDM. A _standard table map_ is an association of a standard table with a center/subcenter/version id. The CDM internally loads several table maps, from `resources/grib1/lookupTables.txt` and its subdirectories `resources/grib1/*/lookupTables.txt`. These are stored in the `grib.jar`, and referenced in `ucar.nc2.grib.grib1.tables.Grib1ParamTables`.

You can view all the standard tables used by the CDM in ToolsUI, using the `IOSP/GRIB-1/GRIB1-TABLES` tab. A standard table map looks like this:

~~~
# resources\grib1\lookupTables.txt

# cen sub version table
  0:  -1:     -1:  dss/WMO_GRIB1.xml
  7:   -1:    -1:  wgrib/table2.htm
  9:   -1:   128:  noaa_rfc/params9-128.tab
 57:   -1:     2:  local/afwa.tab
 58:   42:     2:  local/af_2.tab
 60:   255:    2:  local/wrf_amps.wrf
~~~

1. Each row contains the center, subcenter and table version, and the table filename, with a colon (:) separating the fields.
2. The center, subcenter and table ids are read from each GRIB record, and the list of tables is searched for a match. The first exact match is used.
3. If there is no exact match, then a wildcard match is used, where a "-1" for the subcenter or version id matches any id. The first wildcard match is used.
4. Center 0 is the WMO standard table, called the **default table**. It is set internally and cannot be overridden by the user.
5. If a table is not matched, the default table is used.
6. If a parameter is not found then "Unknown Parameter center-subcenter-version-param" is used as the name, and an empty string for the units.
7. If **strictMode** is on, then
* If (param < 128 and version < 128) the default table is **always** used.
* If (param > 127 or version > 127) a table must be found for all parameters, or else the file will fail to open.
8. You can set strictMode programmatically via `ucar.nc2.grib.grib1.tables.Grib1StandardTables.setStrict(true)`; or in the [RunTime configuration file](runtime_loading.html) by adding

~~~xml
<grib1Table strict="true"/>
~~~

If `strict=true`, when a table is not matched, and local parameters are used, the GRIB file will fail to open. At that point you will need to add the correct parameter table, as described below.

#### GRIB parameter table formats

The CDM can read GRIB parameter tables in several formats:

A **file ending in .xml**: an ad-hoc [XML format](#xml-schema-for-defining-grib-1-parameters) we made up.
A **file ending in .tab**: the <a href="ftp://ftp.cpc.ncep.noaa.gov/wd51we/wgrib/usertables.txt">format</a> that <a href="http://www.cpc.ncep.noaa.gov/products/wesley/wgrib.html">wgrib</a> uses.
{::comment}
A **file starting with "table_2_" or "local_table_2_"**: the format the <a href="http://www.ecmwf.int/products/data/software/grib.html">ECMWF software</a> uses.
{:/comment}

#### Adding to the standard table mapping
When the CDM does not have a table for a center, subcenter and table version that a GRIB file uses, you must track down the corrrect table and add it to the CDM at runtime. (Also, send it to us so we can include it in the next release). To add a table at runtime:

1. Use [Runtime Loading](runtime_loading.html) to add your own table programmatically within your application, or by using the Runtime configuration file.
2. Tables that are added at runtime take precedence over the standard tables, and are searched first in the order of being added. However, the default WMO table cannot be overidden.
3. Parameters that are not present in your table are taken from the default WMO table, if they exist.
   <a name="strict"></a>Unless `strictMode` is on, your table may override entries in the default table.
4. If a parameter is not found then "Unknown Parameter center-subcenter-version-param" is used as the name, and an empty string for the units.

#### Specifying a table for a particular dataset

Many GRIB datasets have an incorrect center/subcenter/version id, which means that the CDM would read from a different table than the one used to write the file. In this case, you dont want to override the correct table in the table map, rather you want to fix the problem for just the incorrect dataset. Here are the ways that can be done:

##### Directly embed table in NcML
You can directly embed the table in NcML, using the [XML schema for declaring Grib-1 Parameters](#xml-schema-for-defining-grib-1-parameters). Place the table inside of a <iospParam> element. For example:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<netcdf xmlns="http://www.unidata.ucar.edu/namespaces/netcdf/ncml-2.2" location="cldc.mean.grib1">
 <iospParam>
  <parameterMap>
    <parameter code="2">
      <description>Pressure reduced to MSL</description>
      <units>Pa</units>
      <name>PRMSL</name>
    </parameter>
    <parameter code="3">
      <description>Pressure tendency</description>
      <units>Pa/s</units>
      <name>PTEND</name>
      <CF>tendency_of_air_pressure</CF>
      <GCMD>EARTH SCIENCE > Atmosphere > Atmospheric Pressure > Pressure Tendency</GCMD>
    </parameter>
    ...
  </parameterMap>
 </iospParam>
</netcdf>
~~~

##### Reference to a Grib Parameter table in NcML

You can reference the table in NcML, with the table being in any {:: comment} this is broken at http://www.ecmwf.int/products/data/software/grib.html {:/comment} GRIB parameter table format that that CDM recognizes. To do so, you pass the string `GribParameterTable=<absolute file path to table>` in the iospParam attribute of the netcdf element:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<netcdf xmlns="http://www.unidata.ucar.edu/namespaces/netcdf/ncml-2.2" location="cldc.mean.grib1"
  iospParam="gribParameterTable=/data/NCEP/grib1/version123.tab">
</netcdf>
~~~

##### Reference to a Grib Parameter table lookup in NcML
You can reference a [table map](#grib-1-table-handling) in NcML by passing the string `"GribParameterTableLookup=<absolute file path to table map>"` in the iospParam attribute of the netcdf element:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<netcdf xmlns="http://www.unidata.ucar.edu/namespaces/netcdf/ncml-2.2" location="cldc.mean.grib1"
  iospParam="gribParameterTableLookup=/data/NCEP/grib1/lookupTable.txt">
</netcdf>
~~~

You can specify a `parameterMap` or an `iospParam`, but not both.

### Correcting Grib-1 files in a GRIB Feature Collection in a THREDDS Data Server

The above methods of adding or referencing tables in NcML will not work for GRIB Feature Collections. In this case, you need to do one of the following:

##### Directly embed table in featureCollection element of the TDS configuration catalog

You can directly embed an XML table (use the [standard XML schema](#xml-schema-for-defining-grib-1-parameters)) in a `featureCollection` element of type GRIB in a TDS configuration catalog:

~~~xml
<featureCollection name="NCEP-GFS-Global_0p5deg" featureType="GRIB" harvest="true" path="grib/NCEP/GFS/Global_0p5deg">
  <collection spec="/NCEP/GFS/Global_0p5deg/GFS_Global_0p5deg_#yyyyMMdd_HHmm#.grib2$" name="GFS_Global_0p5deg" />
  <gribConfig>
   <parameterMap>
    <parameter code="2">
      <description>Pressure reduced to MSL</description>
      <units>Pa</units>
      <name>PRMSL</name>
    </parameter>
    <parameter code="3">
      <description>Pressure tendency</description>
      <units>Pa/s</units>
      <name>PTEND</name>
      <CF>tendency_of_air_pressure</CF>
      <GCMD>EARTH SCIENCE > Atmosphere > Atmospheric Pressure > Pressure Tendency</GCMD>
    </parameter>
    ...

  </parameterMap>

 </gribConfig>
</featureCollection>
~~~

##### Reference a table in featureCollection element of the TDS configuration catalog

You can add a table in a `featureCollection` element of type GRIB in a TDS configuration catalog:

~~~xml
<featureCollection name="NCEP-GFS-Global_0p5deg" featureType="GRIB" harvest="true" path="grib/NCEP/GFS/Global_0p5deg">
  <collection spec="/NCEP/GFS/Global_0p5deg/GFS_Global_0p5deg_#yyyyMMdd_HHmm#.grib2$"  name="GFS_Global_0p5deg" />
  <gribConfig>
    <gribParameterTable>/data/NCEP/grib1/version123.tab</gribParameterTable>
  </gribConfig>
</featureCollection>
~~~

##### Reference a table map in featureCollection element of the TDS configuration catalog:

You can add a table lookup in a `featureCollection` element of type GRIB in a TDS configuration catalog:

~~~xml
<featureCollection name="NCEP-GFS-Global_0p5deg" featureType="GRIB" harvest="true" path="grib/NCEP/GFS/Global_0p5deg">
  <collection spec="/NCEP/GFS/Global_0p5deg/GFS_Global_0p5deg_#yyyyMMdd_HHmm#.grib2$"  name="GFS_Global_0p5deg" />
  <gribConfig>
    <gribParameterTableLookup>/data/NCEP/grib1/version123.txt</gribParameterTableLookup>
  </gribConfig> 
</featureCollection>
~~~

In all these cases, the table that you specify will take precedence over any standard tables. However, you only need to specify the changes/additions; when a parameter is not found in your table, the standard tables are used.

### XML Schema for defining Grib-1 Parameters

Derived from NCAR DSS format with additional "name" element.

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
  <xs:element name="parameterMap">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="title" type="xs:string"/>
        <xs:element name="source" type="xs:string"/>
       <xs:element maxOccurs="unbounded" ref="parameter"/>
      </xs:sequence>
    </xs:complexType>
  </xs:element>


  <xs:element name="parameter">
    <xs:complexType>
      <xs:sequence>
        <xs:element ref="description"/>
        <xs:element minOccurs="0" ref="units"/>
        <xs:element minOccurs="0" ref="name"/>
        <xs:element minOccurs="0" ref="CF"/>
        <xs:element minOccurs="0" maxOccurs="unbounded" ref="GCMD"/>
      </xs:sequence>
      <xs:attribute name="code" use="required" type="xs:integer"/>
    </xs:complexType>
  </xs:element>
  
  <xs:element name="description" type="xs:string"/>
  <xs:element name="units" type="xs:string"/>
  <xs:element name="name" type="xs:string"/>
  <xs:element name="CF" type="xs:NCName"/>
  
  <xs:element name="GCMD">
    <xs:complexType mixed="true">
      <xs:attribute name="ifLevelType" type="xs:integer"/>
    </xs:complexType>
  </xs:element>
</xs:schema>
~~~
   
Example:

~~~xml
<?xml version="1.0" ?>

<parameterMap>
  <title>WMO GRIB1 Parameter Code Table 3</title>
  <source>http://dss.ucar.edu/docs/formats/grib/gribdoc/params.html</source>
  <parameter code="1">
    <description>Pressure</description>
    <units>Pa</units>
    <name>PRES</name>
     <CF>air_pressure</CF>
    <GCMD ifLevelType="1">EARTH SCIENCE > Atmosphere > Atmospheric Pressure > Surface Pressure</GCMD>
    <GCMD ifLevelType="2">EARTH SCIENCE > Atmosphere > Clouds > Cloud Base Pressure</GCMD>
    <GCMD ifLevelType="3">EARTH SCIENCE > Atmosphere > Clouds > Cloud Top Pressure</GCMD>
    <GCMD ifLevelType="7">EARTH SCIENCE > Atmosphere > Altitude > Tropopause</GCMD>
    <GCMD ifLevelType="102">EARTH SCIENCE > Atmosphere > Atmospheric Pressure > Sea Level Pressure</GCMD>
    <GCMD>EARTH SCIENCE > Atmosphere > Atmospheric Pressure > Hydrostatic Pressure</GCMD>
  </parameter>
  <parameter code="2">
    <description>Pressure reduced to MSL</description>
    <units>Pa</units>
    <name>PRMSL</name>
  </parameter>
  <parameter code="3">
    <description>Pressure tendency</description>
    <units>Pa/s</units>
    <name>PTEND</name>
    <CF>tendency_of_air_pressure</CF>
    <GCMD>EARTH SCIENCE > Atmosphere > Atmospheric Pressure > Pressure Tendency</GCMD>
  </parameter>
  <parameter code="4">
    <description>Potential vorticity</description>
    <units>K.m2.kg-1.s-1</units>
    <name>PVORT</name>
    <CF>ertel_potential_vorticity</CF>
    <GCMD>EARTH SCIENCE > Atmosphere > Atmospheric Winds > Vorticity</GCMD>
  </parameter>
  <parameter code="5">
    <description>ICAO Standard Atmosphere reference height</description>
    <units>m</units>
    <name>ICAHT</name>
  </parameter>
   ...

</parameterMap>
~~~

### Notes
In GRIB-1, a single byte contains the version number, with separate bytes for the center and the subcenter. The WMO manual on codes describes the version byte as:

~~~
4: GRIB tables Version No. (currently 3 for international  exchange) – Version numbers 128–254 are reserved for local use
~~~
  
So the rules for GRIB-1 seem to be in actual practice:

* If param id < 128 and table version < 128, use the standard WMO table.
* If param id > 127 or table version > 127, use the version bytes as the local table version for the named center and subcenter.

GRIB-2 expanded this to include a separate byte for the local table version, as well as a discipline:

~~~
    7:  Discipline – GRIB  Master table number (see Code table 0.0)
   10: GRIB Master tables version number (see Code table 1.0 and  Note 1)
   11: Version number of GRIB Local tables used to augment  Master tables (see Code table 1.1 and Note 2) 
Notes:
    (1) Local tables shall define those parts of the Master table which are reserved for local use except for the case described below.
  In any case, the use of Local tables in  messages intended for non-local or international exchange is strongly discouraged.
    (2) If octet 10 contains 255 then only Local tables are in  use, the Local table version number (octet 11) must not be zero nor missing,  
        and Local tables may include entries from the entire range of the tables.
    (3) If octet 11 is zero, octet 10 must contain a valid Master  table version number and only those parts of the tables not reserved 
        for local use may be used.
~~~

BUFR editions greater than 3 also have separate bytes for master and local versions, as well as the equivalent of a discipline (BUFR master table):

~~~
    4: BUFR master table (zero if standard WMO FM 94 BUFR tables  are used – see Note 2)
   11: Version number of master table used – see Notes 2 and 4
   12: Version number of local tables used to augment the master  table in use – see Note 2
   Note 2 (partial):  For  all Master Tables (including Master Table 0):
  – Each revision of the master tables shall be given a new  version number.
  – Local tables shall define those parts of the master table  which are reserved for local use, thus version numbers of local tables
       may be changed at will by the originating centre. If no local table is used, the version  number of the local table shall be encoded as 0.
~~~