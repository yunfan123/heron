// Copyright 2016 Twitter. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.heron.metricsmgr.executor;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.twitter.heron.api.metric.MultiCountMetric;
import com.twitter.heron.common.basics.Communicator;
import com.twitter.heron.common.basics.SlaveLooper;
import com.twitter.heron.metricsmgr.MetricsSinksConfig;
import com.twitter.heron.metricsmgr.sink.SinkContextImpl;
import com.twitter.heron.spi.metricsmgr.metrics.ExceptionInfo;
import com.twitter.heron.spi.metricsmgr.metrics.MetricsInfo;
import com.twitter.heron.spi.metricsmgr.metrics.MetricsRecord;
import com.twitter.heron.spi.metricsmgr.sink.IMetricsSink;
import com.twitter.heron.spi.metricsmgr.sink.SinkContext;

import static org.junit.Assert.fail;

public class SinkExecutorTest {
  private static final long FLUSH_INTERVAL_MS = 100;
  private static final int N = 100;
  private static final int EXPECTED_FLUSHES = 2;
  private static final String METRICS_NAME = "metrics_name";
  private static final String METRICS_VALUE = "metrics_value";
  private static final String EXCEPTION_STACK_TRACE = "stackTrace";
  private static final String EXCEPTION_LAST_TIME = "lastTime";
  private static final String EXCEPTION_FIRST_TIME = "firstTime";
  private static final String EXCEPTION_LOGGING = "logging";
  private static final String RECORD_SOURCE = "source";
  private static final String RECORD_CONTEXT = "context";

  private volatile int processRecordInvoked = 0;
  private volatile int flushInvoked = 0;
  private volatile int initInvoked = 0;
  private DummyMetricsSink metricsSink;
  private SlaveLooper slaveLooper;
  private Communicator<MetricsRecord> communicator;
  private SinkExecutor sinkExecutor;
  private ExecutorService threadsPool;

  @Before
  public void before() throws Exception {
    metricsSink = new DummyMetricsSink(N, EXPECTED_FLUSHES);
    slaveLooper = new SlaveLooper();
    communicator = new Communicator<>(null, slaveLooper);

    SinkContext sinkContext =
        new SinkContextImpl("topology-name", "metricsmgr-id", "sink-id", new MultiCountMetric());

    sinkExecutor =
        new SinkExecutor("testSinkId", metricsSink, slaveLooper, communicator, sinkContext);
  }

  @After
  public void after() throws Exception {
    metricsSink = null;
    slaveLooper = null;
    communicator = null;
    sinkExecutor = null;
  }

  /**
   * Method: setProperty(String key, Object value)
   */
  @Test
  @SuppressWarnings("unchecked")
  public void testSetProperty() throws Exception {
    String key = "key";
    String value = "value";
    sinkExecutor.setProperty(key, value);

    Field field = sinkExecutor.getClass().getDeclaredField("sinkConfig");
    field.setAccessible(true);

    Map<String, Object> map = (Map<String, Object>) field.get(sinkExecutor);
    Assert.assertEquals(map.get(key), value);
  }

  /**
   * Method: setPropertyMap(Map&lt;? extends String, Object&gt; configMap)
   */
  @Test
  @SuppressWarnings("unchecked")
  public void testSetPropertyMap() throws Exception {
    String key = "key";
    String value = "value";
    Map<String, Object> propertyMap = new HashMap<>();
    propertyMap.put(key, value);
    sinkExecutor.setPropertyMap(propertyMap);

    Field field = sinkExecutor.getClass().getDeclaredField("sinkConfig");
    field.setAccessible(true);

    Map<String, Object> map = (Map<String, Object>) field.get(sinkExecutor);
    Assert.assertEquals(map.get(key), value);
  }

  /**
   * Method: run()
   */
  @Test
  public void testRun() throws Exception {
    threadsPool = Executors.newSingleThreadExecutor();
    runSinkExecutor();

    // Push MetricsRecord
    for (int i = 0; i < N; i++) {
      communicator.offer(constructMetricsRecord());
    }

    // wait for the SinkExecutor to fully process the MetricsRecord
    metricsSink.awaitRecordsProcessed(Duration.ofSeconds(5));

    Assert.assertEquals(1, initInvoked);
    // Totally we offer N MetricsRecord
    Assert.assertEquals(N, processRecordInvoked);

    // the flush interval is 100ms, so wait up to 220 ms for 2 flushes to occur
    metricsSink.awaitFlushes(
        Duration.ofMillis(FLUSH_INTERVAL_MS).multipliedBy(EXPECTED_FLUSHES).plusMillis(20));

    Assert.assertTrue(String.format("metrics flush invocations expected: %d, found: %d",
        EXPECTED_FLUSHES, flushInvoked), flushInvoked >= EXPECTED_FLUSHES);

    threadsPool.shutdownNow();
    threadsPool = null;
  }

  private void runSinkExecutor() {
    sinkExecutor.setProperty(MetricsSinksConfig.CONFIG_KEY_FLUSH_FREQUENCY_MS, FLUSH_INTERVAL_MS);
    threadsPool.execute(sinkExecutor);
  }

  private MetricsRecord constructMetricsRecord() {
    List<MetricsInfo> metricsInfos = new ArrayList<>();
    // We add N MetricsInfo into a MetricsRecord
    for (int i = 0; i < N; i++) {
      MetricsInfo metricsInfo = new MetricsInfo(METRICS_NAME + i, METRICS_VALUE + i);
      metricsInfos.add(metricsInfo);
    }

    // We add N ExceptionInfo into a MetricsRecord
    List<ExceptionInfo> exceptionInfos = new ArrayList<>();
    for (int i = 0; i < N; i++) {
      String stackTrace = EXCEPTION_STACK_TRACE + i;
      String lastTime = EXCEPTION_LAST_TIME + i;
      String firstTime = EXCEPTION_FIRST_TIME + i;
      String logging = EXCEPTION_LOGGING + i;
      ExceptionInfo info = new ExceptionInfo(stackTrace, lastTime, firstTime, i, logging);
      exceptionInfos.add(info);
    }

    return new MetricsRecord(RECORD_SOURCE, metricsInfos, exceptionInfos, RECORD_CONTEXT);
  }

  private final class DummyMetricsSink implements IMetricsSink {

    private final CountDownLatch recordProcessedLatch;
    private final CountDownLatch flushesLatch;

    private DummyMetricsSink(int expectedRecords, int expectedFlushes) {
      this.recordProcessedLatch = new CountDownLatch(expectedRecords);
      this.flushesLatch = new CountDownLatch(expectedFlushes);
    }

    void awaitRecordsProcessed(Duration timeout) {
      await(recordProcessedLatch, timeout);
    }

    void awaitFlushes(Duration timeout) {
      await(flushesLatch, timeout);
    }

    void await(CountDownLatch latch, Duration timeout) {
      try {
        latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        fail(String.format(
            "latch failed to release until timeout of %s was reached.", timeout));
      }
    }

    @Override
    public void init(Map<String, Object> map, SinkContext context) {
      initInvoked++;
      Assert.assertEquals(
          map.get(MetricsSinksConfig.CONFIG_KEY_FLUSH_FREQUENCY_MS), FLUSH_INTERVAL_MS);

      Assert.assertEquals("topology-name", context.getTopologyName());
      Assert.assertEquals("sink-id", context.getSinkId());
    }

    @Override
    public void processRecord(MetricsRecord record) {
      Assert.assertEquals(record.getContext(), RECORD_CONTEXT);
      Assert.assertEquals(record.getSource(), RECORD_SOURCE);

      int metrics = 0;
      for (MetricsInfo metricsInfo : record.getMetrics()) {
        Assert.assertEquals(metricsInfo.getName(), METRICS_NAME + metrics);
        Assert.assertEquals(metricsInfo.getValue(), METRICS_VALUE + metrics);
        metrics++;
      }
      // Every time we added N MetricsInfo
      Assert.assertEquals(metrics, N);

      int exceptions = 0;
      for (ExceptionInfo exceptionInfo : record.getExceptions()) {
        Assert.assertEquals(exceptionInfo.getCount(), exceptions);
        Assert.assertEquals(exceptionInfo.getFirstTime(), EXCEPTION_FIRST_TIME + exceptions);
        Assert.assertEquals(exceptionInfo.getLastTime(), EXCEPTION_LAST_TIME + exceptions);
        Assert.assertEquals(exceptionInfo.getLogging(), EXCEPTION_LOGGING + exceptions);
        Assert.assertEquals(exceptionInfo.getStackTrace(), EXCEPTION_STACK_TRACE + exceptions);
        exceptions++;
      }
      // Every time we added N ExceptionInfo
      Assert.assertEquals(exceptions, N);

      processRecordInvoked++;
      recordProcessedLatch.countDown();
    }

    @Override
    public void flush() {
      flushInvoked++;
      flushesLatch.countDown();
    }

    @Override
    public void close() {

    }
  }
}
