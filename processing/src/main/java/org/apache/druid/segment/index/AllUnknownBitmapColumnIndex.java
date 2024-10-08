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

package org.apache.druid.segment.index;

import org.apache.druid.query.BitmapResultFactory;
import org.apache.druid.query.filter.ColumnIndexSelector;
import org.apache.druid.segment.column.ColumnIndexCapabilities;
import org.apache.druid.segment.column.SimpleColumnIndexCapabilities;

/**
 * Like {@link AllFalseBitmapColumnIndex} during normal operation, except if called with 'includeUnknowns' becomes like
 * {@link AllTrueBitmapColumnIndex}.
 */
public class AllUnknownBitmapColumnIndex implements BitmapColumnIndex
{
  private final ColumnIndexSelector selector;


  public AllUnknownBitmapColumnIndex(ColumnIndexSelector indexSelector)
  {
    this.selector = indexSelector;
  }

  @Override
  public ColumnIndexCapabilities getIndexCapabilities()
  {
    return SimpleColumnIndexCapabilities.getConstant();
  }

  @Override
  public int estimatedComputeCost()
  {
    return 0;
  }

  @Override
  public <T> T computeBitmapResult(BitmapResultFactory<T> bitmapResultFactory, boolean includeUnknown)
  {
    if (includeUnknown) {
      return bitmapResultFactory.wrapAllTrue(
          selector.getBitmapFactory()
                  .complement(selector.getBitmapFactory().makeEmptyImmutableBitmap(), selector.getNumRows())
      );
    }
    return bitmapResultFactory.wrapAllFalse(selector.getBitmapFactory().makeEmptyImmutableBitmap());
  }
}
