/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.segment;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.apache.druid.collections.bitmap.BitmapFactory;
import org.apache.druid.java.util.common.io.smoosh.SmooshedFileMapper;
import org.apache.druid.query.OrderBy;
import org.apache.druid.segment.column.ColumnHolder;
import org.apache.druid.segment.data.Indexed;
import org.joda.time.Interval;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 *
 */
public abstract class SimpleQueryableIndex implements QueryableIndex
{
  private final Interval dataInterval;
  private final List<String> columnNames;
  private final Indexed<String> availableDimensions;
  private final BitmapFactory bitmapFactory;
  private final Map<String, Supplier<ColumnHolder>> columns;
  private final SmooshedFileMapper fileMapper;
  private final Supplier<Map<String, DimensionHandler>> dimensionHandlers;
  private final List<OrderBy> ordering;

  public SimpleQueryableIndex(
      Interval dataInterval,
      Indexed<String> dimNames,
      BitmapFactory bitmapFactory,
      Map<String, Supplier<ColumnHolder>> columns,
      SmooshedFileMapper fileMapper,
      boolean lazy
  )
  {
    Preconditions.checkNotNull(columns.get(ColumnHolder.TIME_COLUMN_NAME));
    this.dataInterval = Preconditions.checkNotNull(dataInterval, "dataInterval");
    ImmutableList.Builder<String> columnNamesBuilder = ImmutableList.builder();
    LinkedHashSet<String> dimsFirst = new LinkedHashSet<>();
    for (String dimName : dimNames) {
      dimsFirst.add(dimName);
    }
    for (String columnName : columns.keySet()) {
      if (!ColumnHolder.TIME_COLUMN_NAME.equals(columnName)) {
        dimsFirst.add(columnName);
      }
    }
    columnNamesBuilder.addAll(dimsFirst);
    this.columnNames = columnNamesBuilder.build();
    this.availableDimensions = dimNames;
    this.bitmapFactory = bitmapFactory;
    this.columns = columns;
    this.fileMapper = fileMapper;

    if (lazy) {
      this.dimensionHandlers = Suppliers.memoize(() -> initDimensionHandlers(availableDimensions));
    } else {
      this.dimensionHandlers = () -> initDimensionHandlers(availableDimensions);
    }

    final Metadata metadata = getMetadata();
    if (metadata != null && metadata.getOrdering() != null) {
      this.ordering = metadata.getOrdering();
    } else {
      // When sort order isn't set in metadata.drd, assume the segment is sorted by __time.
      this.ordering = Cursors.ascendingTimeOrder();
    }
  }

  @Override
  public Interval getDataInterval()
  {
    return dataInterval;
  }

  @Override
  public int getNumRows()
  {
    return columns.get(ColumnHolder.TIME_COLUMN_NAME).get().getLength();
  }

  @Override
  public List<String> getColumnNames()
  {
    return columnNames;
  }

  @Override
  public Indexed<String> getAvailableDimensions()
  {
    return availableDimensions;
  }

  @Override
  public List<OrderBy> getOrdering()
  {
    return ordering;
  }

  @Override
  public BitmapFactory getBitmapFactoryForDimensions()
  {
    return bitmapFactory;
  }

  @Nullable
  @Override
  public ColumnHolder getColumnHolder(String columnName)
  {
    Supplier<ColumnHolder> columnHolderSupplier = columns.get(columnName);
    return columnHolderSupplier == null ? null : columnHolderSupplier.get();
  }

  @VisibleForTesting
  public Map<String, Supplier<ColumnHolder>> getColumns()
  {
    return columns;
  }

  @VisibleForTesting
  public SmooshedFileMapper getFileMapper()
  {
    return fileMapper;
  }

  @Override
  public void close()
  {
    if (fileMapper != null) {
      fileMapper.close();
    }
  }

  @Override
  public abstract Metadata getMetadata();

  @Override
  public Map<String, DimensionHandler> getDimensionHandlers()
  {
    return dimensionHandlers.get();
  }

  private Map<String, DimensionHandler> initDimensionHandlers(Indexed<String> availableDimensions)
  {
    Map<String, DimensionHandler> dimensionHandlerMap = Maps.newLinkedHashMap();
    for (String dim : availableDimensions) {
      final ColumnHolder columnHolder = getColumnHolder(dim);
      final DimensionHandler handler = columnHolder.getColumnFormat().getColumnHandler(dim);
      dimensionHandlerMap.put(dim, handler);
    }
    return dimensionHandlerMap;
  }
}
