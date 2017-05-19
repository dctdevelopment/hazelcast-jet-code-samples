/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
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

import com.hazelcast.jet.AbstractProcessor;
import com.hazelcast.jet.Traverser;
import com.hazelcast.jet.function.DistributedFunction;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hazelcast.jet.Traversers.traverseStream;

/**
 * A processor to join two streams. The stream on ordinal 0 is fully
 * accumulated and must have higher priority edge. It must be bounded. The
 * stream on ordinal 1 can be infinite and is processed on the fly. If both
 * inputs are bounded, the smaller one should be connected to ordinal 0 to
 * limit memory usage.
 * <p>
 * Items with keys that do not have corresponding items in the other stream are
 * emitted with {@code null} in place of the other item. The output type is
 * {@code Object[]}.
 *
 * @param <T0> type of items on ordinal 0
 * @param <T1> type of items on ordinal 1
 * @param <K> key type
 */
public class CoGroupP<T0, T1, K> extends AbstractProcessor {

    private final DistributedFunction<? super T0, ? extends K> keyExtractor0;
    private final DistributedFunction<? super T1, ? extends K> keyExtractor1;

    private final Map<K, List<T0>> unseenMap = new HashMap<>();
    private final Map<K, List<T0>> seenMap = new HashMap<>();
    private final FlatMapper<T1, Object[]> flatMapper;
    private boolean t1Received; // for fail-fast behavior
    private final Traverser<Object[]> unseenTraverser =
            traverseStream(unseenMap.values().stream()
                                    .flatMap(List::stream)
                                    .map(t0 -> new Object[]{t0, null}));

    public CoGroupP(DistributedFunction<? super T0, ? extends K> keyExtractor0,
                    DistributedFunction<? super T1, ? extends K> keyExtractor1
    ) {
        this.keyExtractor0 = keyExtractor0;
        this.keyExtractor1 = keyExtractor1;

        flatMapper = flatMapper(this::outputTraverser);
    }

    private Traverser<? extends Object[]> outputTraverser(T1 t1) {
        K key = keyExtractor1.apply(t1);
        List<T0> joinedT0 = seenMap.computeIfAbsent(key, unseenMap::remove);
        if (joinedT0 == null) {
            joinedT0 = Collections.singletonList(null);
        }
        return traverseStream(joinedT0.stream()
                                      .map(t0 -> new Object[]{t0, t1}));
    }

    @Override
    protected boolean tryProcess0(@Nonnull Object item) throws Exception {
        assert !t1Received : "new items on ordinal 0 after items on ordinal 1: please set priority";
        K key = keyExtractor0.apply((T0) item);
        unseenMap.computeIfAbsent(key, k -> new ArrayList<>())
                 .add((T0) item);
        return true;
    }

    @Override
    protected boolean tryProcess1(@Nonnull Object item) throws Exception {
        t1Received = true;
        return flatMapper.tryProcess((T1) item);
    }

    @Override
    public boolean complete() {
        return emitFromTraverser(unseenTraverser);
    }
}