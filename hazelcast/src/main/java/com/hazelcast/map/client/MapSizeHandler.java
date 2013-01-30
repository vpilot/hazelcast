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

package com.hazelcast.map.client;

import com.hazelcast.instance.Node;
import com.hazelcast.map.MapService;
import com.hazelcast.map.proxy.DataMapProxy;
import com.hazelcast.nio.Protocol;

public class MapSizeHandler extends MapCommandHandler {

    public MapSizeHandler(MapService mapService) {
        super(mapService);
    }

    public Protocol processCall(Node node, Protocol protocol) {
        String name = protocol.args[0];
        DataMapProxy dataMapProxy = mapService.createDistributedObjectForClient(name);
        int size = dataMapProxy.size();
        return protocol.success(String.valueOf(size));
    }
}