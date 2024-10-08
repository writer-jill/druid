/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { C, SqlFunction, SqlQuery } from '@druid-toolkit/query';

import { filterMap, uniq } from '../../../utils';
import { Measure } from '../models';

export function rewriteAggregate(query: SqlQuery, measures: Measure[]): SqlQuery {
  const usedMeasures: Map<string, boolean> = new Map();
  return query.walk(ex => {
    if (ex instanceof SqlFunction && ex.getEffectiveFunctionName() === Measure.AGGREGATE) {
      if (ex.numArgs() !== 1)
        throw new Error(`${Measure.AGGREGATE} function must have exactly 1 argument`);

      const measureName = ex.getArgAsString(0);
      if (!measureName) throw new Error(`${Measure.AGGREGATE} argument must be a measure name`);

      const measure = measures.find(({ name }) => name === measureName);
      if (!measure) throw new Error(`${Measure.AGGREGATE} of unknown measure '${measureName}'`);

      usedMeasures.set(measureName, true);
      return measure.expression;
    }

    // If we encounter a (the) query with the measure definitions, and we have used those measures then expand out all the columns within them
    if (ex instanceof SqlQuery) {
      const queryMeasures = Measure.extractQueryMeasures(ex);
      if (queryMeasures.length) {
        return ex.applyForEach(
          uniq(
            filterMap(queryMeasures, queryMeasure =>
              usedMeasures.get(queryMeasure.name) ? queryMeasure.expression : undefined,
            ).flatMap(ex => ex.getUsedColumnNames()),
          ).filter(columnName => !ex.getSelectIndexForOutputColumn(columnName)),
          (q, columnName) => q.addSelect(C(columnName)),
        );
      }
    }

    return ex;
  }) as SqlQuery;
}
