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

package map;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.jet.DAG;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.processor.Processors;
import com.hazelcast.jet.processor.Sinks;
import com.hazelcast.jet.processor.Sources;
import com.hazelcast.jet.Util;
import com.hazelcast.jet.Vertex;
import com.hazelcast.map.listener.EntryAddedListener;

import java.util.Map.Entry;

import static com.hazelcast.jet.Edge.between;

/**
 * A DAG which reads from a remote Hazelcast IMap,
 * converts the key/value to string,
 * and writes to another remote Hazelcast IMap
 */
public class ReadWriteRemoteMap {

    static final String SOURCE_MAP_NAME = "sourceMap";
    static final String SINK_MAP_NAME = "sinkMap";

    public static void main(String[] args) throws Exception {
        RemoteNode remoteNode = new RemoteNode();
        remoteNode.start();

        JetInstance instance = Jet.newJetInstance();

        try {
            DAG dag = new DAG();

            ClientConfig clientConfig = new ClientConfig();
            clientConfig.getGroupConfig().setName("dev").setPassword("dev-pass");
            clientConfig.getNetworkConfig().addAddress("localhost:6701");

            Vertex source = dag.newVertex("source", Sources.readMap(SOURCE_MAP_NAME, clientConfig));
            Vertex transform = dag.newVertex("transform", Processors.map((Entry<Integer, Integer> e)
                    -> Util.entry(e.getKey().toString(), e.getValue().toString())));
            Vertex sink = dag.newVertex("sink", Sinks.writeMap(SINK_MAP_NAME, clientConfig));

            dag.edge(between(source, transform));
            dag.edge(between(transform, sink));
            instance.newJob(dag).execute().get();

        } finally {
            Jet.shutdownAll();
            remoteNode.stop();
        }
    }

    private static class RemoteNode {

        private static final int ITEM_COUNT = 10;

        private HazelcastInstance instance;

        void start() throws Exception {
            Config config = new Config();
            config.getNetworkConfig().setPort(6701);
            instance = Hazelcast.newHazelcastInstance(config);
            IMap<Integer, Integer> sourceMap = instance.getMap(SOURCE_MAP_NAME);
            for (int i = 0; i < ITEM_COUNT; i++) {
                sourceMap.put(i, i);
            }
            IMap<String, String> sinkMap = instance.getMap(SINK_MAP_NAME);
            sinkMap.addEntryListener((EntryAddedListener<String, String>) event
                    -> System.out.println("Entry added to sink" + event.getKey() + "-" + event.getValue()), true);
        }

        void stop() {
            instance.shutdown();
        }
    }
}
