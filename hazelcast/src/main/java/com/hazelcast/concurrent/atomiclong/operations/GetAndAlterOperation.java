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

package com.hazelcast.concurrent.atomiclong.operations;

import com.hazelcast.concurrent.atomiclong.LongWrapper;
import com.hazelcast.core.IFunction;

public class GetAndAlterOperation extends AbstractAlterOperation {

    public GetAndAlterOperation() {
    }

    public GetAndAlterOperation(String name, IFunction<Long, Long> function) {
        super(name, function);
    }

    @Override
    public void run() throws Exception {
        LongWrapper number = getNumber();

        long input = number.get();
        response = input;
        long output = function.apply(input);
        shouldBackup = input != output;
        if (shouldBackup) {
            backup = output;
            number.set(output);
        }
    }
}
