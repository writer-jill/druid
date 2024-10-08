---
id: zookeeper
title: "ZooKeeper"
---

<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one
  ~ or more contributor license agreements.  See the NOTICE file
  ~ distributed with this work for additional information
  ~ regarding copyright ownership.  The ASF licenses this file
  ~ to you under the Apache License, Version 2.0 (the
  ~ "License"); you may not use this file except in compliance
  ~ with the License.  You may obtain a copy of the License at
  ~
  ~   http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing,
  ~ software distributed under the License is distributed on an
  ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~ KIND, either express or implied.  See the License for the
  ~ specific language governing permissions and limitations
  ~ under the License.
  -->


Apache Druid uses [Apache ZooKeeper](http://zookeeper.apache.org/) (ZK) for management of current cluster state.

## Minimum ZooKeeper versions

Apache Druid supports ZooKeeper versions 3.5.x and above.

:::info
 Note: Starting with Apache Druid 0.22.0, support for ZooKeeper 3.4.x has been removed
 Starting with Apache Druid 31.0.0, support for Zookeeper-based segment loading has been removed.
:::

## ZooKeeper Operations

The operations that happen over ZK are

1.  [Coordinator](../design/coordinator.md) leader election
2.  Segment "publishing" protocol from [Historical](../design/historical.md)
3.  [Overlord](../design/overlord.md) leader election
4.  [Overlord](../design/overlord.md) and [Middle Manager](../design/middlemanager.md) task management

## Coordinator Leader Election

We use the Curator [LeaderLatch](https://curator.apache.org/curator-recipes/leader-latch.html) recipe to perform leader election at path

```
${druid.zk.paths.coordinatorPath}/_COORDINATOR
```

## Segment "publishing" protocol from Historical and Realtime

The `announcementsPath` and `liveSegmentsPath` are used for this.

All [Historical](../design/historical.md) processes publish themselves on the `announcementsPath`, specifically, they will create an ephemeral znode at

```
${druid.zk.paths.announcementsPath}/${druid.host}
```

Which signifies that they exist. They will also subsequently create a permanent znode at

```
${druid.zk.paths.liveSegmentsPath}/${druid.host}
```

And as they load up segments, they will attach ephemeral znodes that look like

```
${druid.zk.paths.liveSegmentsPath}/${druid.host}/_segment_identifier_
```

Processes like the [Coordinator](../design/coordinator.md) and [Broker](../design/broker.md) can then watch these paths to see which processes are currently serving which segments.
