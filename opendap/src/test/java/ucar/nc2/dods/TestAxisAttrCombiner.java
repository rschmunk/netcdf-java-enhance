/*
 * (c) 1998-2017 University Corporation for Atmospheric Research/Unidata
 */
package ucar.nc2.dods;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.nc2.Attribute;
import ucar.nc2.constants._Coordinate;
import java.lang.invoke.MethodHandles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static ucar.nc2.dods.DODSNetcdfFile.combineAxesAttrs;

public class TestAxisAttrCombiner {
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void testAxisAttrCombineSame() {
    Attribute attr1 = new Attribute(_Coordinate.Axes, "abe bec cid dave");
    Attribute attr2 = new Attribute(_Coordinate.Axes, "abe bec cid dave");
    Attribute attr3 = combineAxesAttrs(attr1, attr2);

    assertEquals(attr3.getStringValue(), (attr1.getStringValue()));
    assertEquals(attr3.getStringValue(), attr2.getStringValue());
  }

  @Test
  public void testAxisAttrCombineDiff() {
    Attribute attr1 = new Attribute(_Coordinate.Axes, "abe bec cid dave");
    Attribute attr2 = new Attribute(_Coordinate.Axes, "ed fin gabe hedi");
    Attribute attr3 = combineAxesAttrs(attr1, attr2);

    assertTrue(attr3.getStringValue().contains(attr1.getStringValue()));
    assertTrue(attr3.getStringValue().contains(attr2.getStringValue()));
  }

  @Test
  public void testAxisAttrCombineOverlap() {
    Attribute attr1 = new Attribute(_Coordinate.Axes, "abe bec cid dave");
    Attribute attr2 = new Attribute(_Coordinate.Axes, "cid dave ed fin");
    Attribute attr3 = combineAxesAttrs(attr1, attr2);

    assertTrue(attr3.getStringValue().contains(attr1.getStringValue()));
    assertTrue(attr3.getStringValue().contains(attr2.getStringValue()));

    assertEquals(attr3.getStringValue().split("\\s").length, 6);
  }

  @Test
  public void testAxisAttrCombineOverlapMangled() {
    Attribute attr1 = new Attribute(_Coordinate.Axes, " abe bec     cid dave    ");
    Attribute attr2 = new Attribute(_Coordinate.Axes, "cid   dave ed   fin");
    Attribute attr3 = combineAxesAttrs(attr1, attr2);

    // these will fail, because whitespace is all messed up in the attribute values
    assertFalse(attr3.getStringValue().contains(attr1.getStringValue()));
    assertFalse(attr3.getStringValue().contains(attr2.getStringValue()));

    assertEquals(attr3.getStringValue().split("\\s").length, 6);
  }
}
