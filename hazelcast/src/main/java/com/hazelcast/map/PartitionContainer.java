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

package com.hazelcast.map;

import com.hazelcast.nio.Address;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.partition.PartitionInfo;
import com.hazelcast.util.ConcurrencyUtil;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class PartitionContainer {
    private final MapService mapService;
    final PartitionInfo partitionInfo;
    final ConcurrentMap<String, DefaultRecordStore> maps = new ConcurrentHashMap<String, DefaultRecordStore>(1000);
    final ConcurrentMap<String, TransactionLog> transactions = new ConcurrentHashMap<String, TransactionLog>(1000);

    public PartitionContainer(final MapService mapService, final PartitionInfo partitionInfo) {
        this.mapService = mapService;
        this.partitionInfo = partitionInfo;
    }

    void onDeadAddress(Address deadAddress) {
        // invalidate scheduled operations of dead
        // invalidate transactions of dead
        // invalidate locks owned by dead
    }

    public MapService getMapService() {
        return mapService;
    }

    private final ConcurrencyUtil.ConstructorFunction<String, DefaultRecordStore> recordStoreConstructor
            = new ConcurrencyUtil.ConstructorFunction<String, DefaultRecordStore>() {
        public DefaultRecordStore createNew(String name) {
            return new DefaultRecordStore(name, PartitionContainer.this);
        }
    };

    public RecordStore getRecordStore(String name) {
        return ConcurrencyUtil.getOrPutIfAbsent(maps, name, recordStoreConstructor);
    }

    public TransactionLog getTransactionLog(String txnId) {
        return transactions.get(txnId);
    }

    public void addTransactionLogItem(String txnId, TransactionLogItem logItem) {
        TransactionLog log = transactions.get(txnId);
        if (log == null) {
            log = new TransactionLog(txnId);
            transactions.put(txnId, log);
        }
        log.addLogItem(logItem);
    }

    public void putTransactionLog(String txnId, TransactionLog txnLog) {
        transactions.put(txnId, txnLog);
    }

    void rollback(String txnId) {
        transactions.remove(txnId);
    }

    void commit(String txnId) {
//        TransactionLog txnLog = transactions.get(txnId);
        TransactionLog txnLog = transactions.remove(txnId); // TODO: not sure?
        if (txnLog == null) return;
        for (TransactionLogItem txnLogItem : txnLog.changes.values()) {
            System.out.println(mapService.getNodeEngine().getThisAddress() + " pc.commit " + txnLogItem);
            RecordStore recordStore = getRecordStore(txnLogItem.getName());
            Data key = txnLogItem.getKey();
            if (txnLogItem.isRemoved()) {
                recordStore.remove(key);
            } else {
                Record record = recordStore.getRecords().get(key);
                if (record == null) {
                    record = mapService.createRecord(txnLogItem.getName(), key, txnLogItem.getValue(), -1);
                    recordStore.getRecords().put(key, record);
                } else {
                    recordStore.setRecordValue(record, txnLogItem.getValue());
                }
            }
        }
    }

    public int getMaxBackupCount() {
        int max = 1;
        for (DefaultRecordStore mapPartition : maps.values()) {
            // TODO: get max map backup count!
//        777    max = Math.max(max, mapPartition.get);
        }
        return max;
    }

    void destroyMap(String name) {
        DefaultRecordStore recordStore = maps.remove(name);
        recordStore.clear();
    }

    void destroy() {
        for (DefaultRecordStore store : maps.values()) {
            store.clear();
        }
        maps.clear();
        transactions.clear();
    }
}