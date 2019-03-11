/*
 * Copyright 2019 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.connector.elasticsearch;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.document.Document;
import com.couchbase.client.java.error.TemporaryFailureException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import static com.couchbase.connector.testcontainers.Poller.poll;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

class IntegrationTestHelper {
  private IntegrationTestHelper() {
    throw new AssertionError("not instantiable");
  }

  static void waitForTravelSampleReplication(TestEsClient es) throws TimeoutException, InterruptedException {
    final int airlines = 187;
    final int routes = 24024;

    final int expectedAirlineCount = airlines + routes;
    final int expectedAirportCount = 1968;

    poll().until(() -> es.getDocumentCount("airlines") >= expectedAirlineCount);
    poll().until(() -> es.getDocumentCount("airports") >= expectedAirportCount);

    SECONDS.sleep(3); // quiet period, make sure no more documents appear in the index

    assertEquals(expectedAirlineCount, es.getDocumentCount("airlines"));
    assertEquals(expectedAirportCount, es.getDocumentCount("airports"));

    // route documents are routed using airlineid field
    final String routeId = "route_10000";
    final String expectedRouting = "airline_137";
    JsonNode airline = es.getDocument("airlines", routeId, expectedRouting).orElse(null);
    assertNotNull(airline);
    assertEquals(expectedRouting, airline.path("_routing").asText());
  }

  static <D extends Document<?>> D upsertWithRetry(Bucket bucket, D document) throws Exception {
    return callWithRetry(() -> bucket.upsert(document));
  }

  private static <R> R callWithRetry(Callable<R> callable) throws Exception {
    final int maxAttempts = 10;
    TemporaryFailureException deferred = null;
    for (int attempt = 0; attempt <= maxAttempts; attempt++) {
      if (attempt != 0) {
        SECONDS.sleep(1);
      }
      try {
        return callable.call();
      } catch (TemporaryFailureException e) {
        deferred = e;
      }
    }
    throw deferred;
  }
}
