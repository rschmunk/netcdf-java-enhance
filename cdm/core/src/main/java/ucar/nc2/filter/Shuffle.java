/*
 * Copyright (c) 2021 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.nc2.filter;

import java.util.Map;

/**
 * Filter implementation of Shuffle.
 */
public class Shuffle extends Filter {

  private static final String name = "shuffle";

  private static final int id = 2;

  private int elemSize;
  private static final int DEFAULT_SIZE = 4;

  public Shuffle(Map<String, Object> properties) {
    try {
      elemSize = (int) properties.get(Filters.Keys.ELEM_SIZE);
    } catch (Exception ex) {
      elemSize = DEFAULT_SIZE;
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int getId() {
    return id;
  }

  @Override
  public byte[] encode(byte[] dataIn) {
    if (elemSize <= 1) {
      return dataIn;
    }

    int nElems = dataIn.length / elemSize;
    int[] start = new int[elemSize];
    for (int k = 0; k < elemSize; k++) {
      start[k] = k * nElems;
    }

    byte[] result = new byte[dataIn.length];
    for (int i = 0; i < nElems; i++) {
      for (int j = 0; j < elemSize; j++) {
        result[i + start[j]] = dataIn[(i * elemSize) + j];
      }
    }

    int leftoverBytes = dataIn.length % this.elemSize;
    System.arraycopy(dataIn, dataIn.length - leftoverBytes, result, result.length - leftoverBytes, leftoverBytes);

    return result;
  }

  @Override
  public byte[] decode(byte[] dataIn) {
    if (elemSize <= 1) {
      return dataIn;
    }

    int nElems = dataIn.length / this.elemSize;
    byte[] result = new byte[dataIn.length];

    for (int j = 0; j < this.elemSize; ++j) {
      int sourceIndex = j * nElems;
      int destIndex = j;
      for (int i = 0; i < nElems; ++i) {
        result[destIndex] = dataIn[sourceIndex];
        sourceIndex++;
        destIndex += this.elemSize;
      }
    }

    int leftoverBytes = dataIn.length % this.elemSize;
    System.arraycopy(dataIn, dataIn.length - leftoverBytes, result, result.length - leftoverBytes, leftoverBytes);

    return result;
  }

  public static class Provider implements FilterProvider {

    @Override
    public String getName() {
      return name;
    }

    @Override
    public int getId() {
      return id;
    }

    @Override
    public Filter create(Map<String, Object> properties) {
      return new Shuffle(properties);
    }
  }
}
