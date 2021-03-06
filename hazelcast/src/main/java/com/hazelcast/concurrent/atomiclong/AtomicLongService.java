/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.concurrent.atomiclong;

import com.hazelcast.concurrent.atomiclong.operations.AtomicLongReplicationOperation;
import com.hazelcast.partition.MigrationEndpoint;
import com.hazelcast.partition.PartitionService;
import com.hazelcast.spi.ManagedService;
import com.hazelcast.spi.MigrationAwareService;
import com.hazelcast.spi.NodeEngine;
import com.hazelcast.spi.Operation;
import com.hazelcast.spi.PartitionMigrationEvent;
import com.hazelcast.spi.PartitionReplicationEvent;
import com.hazelcast.spi.RemoteService;
import com.hazelcast.util.ConstructorFunction;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.hazelcast.partition.strategy.StringPartitioningStrategy.getPartitionKey;
import static com.hazelcast.util.ConcurrencyUtil.getOrPutIfAbsent;

public class AtomicLongService implements ManagedService, RemoteService, MigrationAwareService {

    /**
     * The name of this service.s
     */
    public static final String SERVICE_NAME = "hz:impl:atomicLongService";

    private NodeEngine nodeEngine;
    private final ConcurrentMap<String, LongWrapper> numbers = new ConcurrentHashMap<String, LongWrapper>();
    private final ConstructorFunction<String, LongWrapper> atomicLongConstructorFunction =
            new ConstructorFunction<String, LongWrapper>() {
                public LongWrapper createNew(String key) {
                    return new LongWrapper();
                }
            };

    public AtomicLongService() {
    }

    public LongWrapper getNumber(String name) {
        return getOrPutIfAbsent(numbers, name, atomicLongConstructorFunction);
    }

    // need for testing..
    public boolean containsAtomicLong(String name) {
        return numbers.containsKey(name);
    }

    @Override
    public void init(NodeEngine nodeEngine, Properties properties) {
        this.nodeEngine = nodeEngine;
    }

    @Override
    public void reset() {
        numbers.clear();
    }

    @Override
    public void shutdown(boolean terminate) {
        reset();
    }

    @Override
    public AtomicLongProxy createDistributedObject(String name) {
        return new AtomicLongProxy(name, nodeEngine, this);
    }

    @Override
    public void destroyDistributedObject(String name) {
        numbers.remove(name);
    }

    @Override
    public void beforeMigration(PartitionMigrationEvent partitionMigrationEvent) {
    }

    @Override
    public Operation prepareReplicationOperation(PartitionReplicationEvent event) {
        if (event.getReplicaIndex() > 1) {
            return null;
        }

        Map<String, Long> data = new HashMap<String, Long>();
        int partitionId = event.getPartitionId();
        for (String name : numbers.keySet()) {
            if (partitionId == getPartitionId(name)) {
                LongWrapper number = numbers.get(name);
                data.put(name, number.get());
            }
        }
        return data.isEmpty() ? null : new AtomicLongReplicationOperation(data);
    }

    private int getPartitionId(String name) {
        PartitionService partitionService = nodeEngine.getPartitionService();
        String partitionKey = getPartitionKey(name);
        return partitionService.getPartitionId(partitionKey);
    }

    @Override
    public void commitMigration(PartitionMigrationEvent partitionMigrationEvent) {
        if (partitionMigrationEvent.getMigrationEndpoint() == MigrationEndpoint.SOURCE) {
            removeNumber(partitionMigrationEvent.getPartitionId());
        }
    }

    @Override
    public void rollbackMigration(PartitionMigrationEvent partitionMigrationEvent) {
        if (partitionMigrationEvent.getMigrationEndpoint() == MigrationEndpoint.DESTINATION) {
            removeNumber(partitionMigrationEvent.getPartitionId());
        }
    }

    @Override
    public void clearPartitionReplica(int partitionId) {
        removeNumber(partitionId);
    }

    public void removeNumber(int partitionId) {
        final Iterator<String> iterator = numbers.keySet().iterator();
        while (iterator.hasNext()) {
            String name = iterator.next();
            if (getPartitionId(name) == partitionId) {
                iterator.remove();
            }
        }
    }
}
