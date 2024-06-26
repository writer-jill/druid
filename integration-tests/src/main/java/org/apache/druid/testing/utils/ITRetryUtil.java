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

package org.apache.druid.testing.utils;

import org.apache.druid.java.util.common.ISE;
import org.apache.druid.java.util.common.logger.Logger;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class ITRetryUtil
{

  private static final Logger LOG = new Logger(ITRetryUtil.class);

  public static final int DEFAULT_RETRY_COUNT = 240; // 20 minutes

  public static final long DEFAULT_RETRY_SLEEP = TimeUnit.SECONDS.toMillis(5);

  public static void retryUntilTrue(Callable<Boolean> callable, String task)
  {
    retryUntil(callable, true, DEFAULT_RETRY_SLEEP, DEFAULT_RETRY_COUNT, task);
  }

  public static void retryUntilFalse(Callable<Boolean> callable, String task)
  {
    retryUntil(callable, false, DEFAULT_RETRY_SLEEP, DEFAULT_RETRY_COUNT, task);
  }

  public static void retryUntil(
      Callable<Boolean> callable,
      boolean expectedValue,
      long delayInMillis,
      int retryCount,
      String taskMessage
  )
  {
    int currentTry = 0;
    Exception lastException = null;

    while (true) {
      try {
        LOG.info("Trying attempt[%d/%d]...", currentTry, retryCount);
        if (currentTry > retryCount || callable.call() == expectedValue) {
          break;
        }
      }
      catch (Exception e) {
        // just continue retrying if there is an exception (it may be transient!) but save the last:
        lastException = e;
      }

      LOG.info(
          "Attempt[%d/%d] did not pass: Task %s still not complete. Next retry in %d ms",
          currentTry,
          retryCount,
          taskMessage,
          delayInMillis
      );
      try {
        Thread.sleep(delayInMillis);
      }
      catch (InterruptedException e) {
        // Ignore
      }
      currentTry++;
    }

    if (currentTry > retryCount) {
      if (lastException != null) {
        throw new ISE(
            lastException,
            "Max number of retries[%d] exceeded for Task[%s]. Failing.",
            retryCount,
            taskMessage
        );
      } else {
        throw new ISE(
            "Max number of retries[%d] exceeded for Task[%s]. Failing.",
            retryCount,
            taskMessage
        );
      }
    }
  }

}
