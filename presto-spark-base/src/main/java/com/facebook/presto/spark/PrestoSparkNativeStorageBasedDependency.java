/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.spark;

import com.facebook.presto.common.type.BigintType;
import com.facebook.presto.spark.classloader_interface.PrestoSparkSerializedPage;
import com.facebook.presto.spark.util.PrestoSparkUtils;
import com.facebook.presto.spi.page.PagesSerde;
import io.airlift.units.DataSize;
import org.apache.spark.SparkException;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import scala.Tuple2;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static com.facebook.presto.ExceededMemoryLimitException.exceededLocalBroadcastMemoryLimit;
import static com.facebook.presto.spark.util.PrestoSparkUtils.computeNextTimeout;
import static io.airlift.units.DataSize.succinctBytes;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;

public class PrestoSparkNativeStorageBasedDependency
        implements PrestoSparkBroadcastDependency<PrestoSparkSerializedPage>
{
    private RddAndMore<PrestoSparkSerializedPage> broadcastDependency;
    private final DataSize maxBroadcastSize;
    private final long queryCompletionDeadline;
    private Broadcast<List<PrestoSparkSerializedPage>> broadcastVariable;
    private final Set<PrestoSparkServiceWaitTimeMetrics> waitTimeMetrics;
    private final PagesSerde pagesSerde;

    public PrestoSparkNativeStorageBasedDependency(RddAndMore<PrestoSparkSerializedPage> broadcastDependency,
            DataSize maxBroadcastSize,
            long queryCompletionDeadline,
            Set<PrestoSparkServiceWaitTimeMetrics> waitTimeMetrics,
            PagesSerde pagesSerde)
    {
        this.broadcastDependency = requireNonNull(broadcastDependency, "broadcastDependency cannot be null");
        this.maxBroadcastSize = requireNonNull(maxBroadcastSize, "maxBroadcastSize cannot be null");
        this.queryCompletionDeadline = queryCompletionDeadline;
        this.waitTimeMetrics = requireNonNull(waitTimeMetrics, "waitTimeMetrics cannot be null");
        this.pagesSerde = requireNonNull(pagesSerde, "pagesSerde cannot be null");
    }

    @Override
    public Broadcast<List<PrestoSparkSerializedPage>> executeBroadcast(JavaSparkContext sparkContext)
            throws SparkException, TimeoutException
    {
        // The page returned from native process contains filePath, maxSerializedBytes and numberOfRows in broadcast data.
        List<PrestoSparkSerializedPage> broadcastValue = broadcastDependency.collectAndDestroyDependenciesWithTimeout(computeNextTimeout(queryCompletionDeadline), MILLISECONDS, waitTimeMetrics).stream()
                .map(Tuple2::_2)
                .collect(toList());

        // release memory retained by the RDD (splits and dependencies)
        broadcastDependency = null;

        long maxBroadcastSizeInBytes = maxBroadcastSize.toBytes();
        long maxSerializedBytes = getNativeMaxSerializedBytes(broadcastValue, pagesSerde);

        if (maxSerializedBytes > maxBroadcastSizeInBytes) {
            throw exceededLocalBroadcastMemoryLimit(maxBroadcastSize, format("Max serialized broadcast size: %s", succinctBytes(maxSerializedBytes)));
        }

        broadcastVariable = sparkContext.broadcast(broadcastValue);
        return broadcastVariable;
    }

    private long getNativeMaxSerializedBytes(List<PrestoSparkSerializedPage> prestoSparkSerializedPages, PagesSerde pagesSerde)
    {
        return prestoSparkSerializedPages.stream()
                .map(prestoSparkSerializedPage -> PrestoSparkUtils.toSerializedPage(prestoSparkSerializedPage))
                .map(serializedPage -> pagesSerde.deserialize(serializedPage))
                // Extract sum of maxSerializedSize.
                .flatMap(page -> IntStream.range(0, page.getPositionCount())
                        .mapToObj(position -> BigintType.BIGINT.getObjectValue(null, page.getBlock(1), position)))
                .mapToLong(Long.class::cast)
                .sum();
    }

    @Override
    public void destroy()
    {
        if (broadcastVariable != null) {
            broadcastVariable.destroy();
        }
    }
}
