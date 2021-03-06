
/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.apache.cassandra.service.pager;

import java.nio.ByteBuffer;

import org.junit.Test;

import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.ColumnDefinition;
import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.db.*;
import org.apache.cassandra.db.rows.*;
import org.apache.cassandra.db.marshal.*;
import org.apache.cassandra.transport.Server;
import org.apache.cassandra.utils.ByteBufferUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PagingStateTest
{
    private PagingState makeSomePagingState(int protocolVersion)
    {
        CFMetaData metadata = CFMetaData.Builder.create("ks", "tbl")
                                                .addPartitionKey("k", AsciiType.instance)
                                                .addClusteringColumn("c1", AsciiType.instance)
                                                .addClusteringColumn("c1", Int32Type.instance)
                                                .addRegularColumn("myCol", AsciiType.instance)
                                                .build();

        ByteBuffer pk = ByteBufferUtil.bytes("someKey");

        ColumnDefinition def = metadata.getColumnDefinition(new ColumnIdentifier("myCol", false));
        Clustering c = new Clustering(ByteBufferUtil.bytes("c1"), ByteBufferUtil.bytes(42));
        Row row = BTreeRow.singleCellRow(c, BufferCell.live(metadata, def, 0, ByteBufferUtil.EMPTY_BYTE_BUFFER));
        PagingState.RowMark mark = PagingState.RowMark.create(metadata, row, protocolVersion);
        return new PagingState(pk, mark, 10, 0);
    }

    @Test
    public void testSerializationBackwardCompatibility()
    {
        /*
         * Tests that the serialized paging state for the native protocol V3 is backward compatible
         * with what old nodes generate. For that, it compares the serialized format to the hard-coded
         * value of the same state generated on a 2.1. For the curious, said hardcoded value has been
         * generated by the following code:
         *     ByteBuffer pk = ByteBufferUtil.bytes("someKey");
         *     CellName cn = CellNames.compositeSparse(new ByteBuffer[]{ ByteBufferUtil.bytes("c1"), ByteBufferUtil.bytes(42) },
         *                                             new ColumnIdentifier("myCol", false),
         *                                             false);
         *     PagingState state = new PagingState(pk, cn.toByteBuffer(), 10);
         *     System.out.println("PagingState = " + ByteBufferUtil.bytesToHex(state.serialize()));
         */
        PagingState state = makeSomePagingState(Server.VERSION_3);

        String serializedState = ByteBufferUtil.bytesToHex(state.serialize(Server.VERSION_3));
        // Note that we don't assert exact equality because we know 3.0 nodes include the "remainingInPartition" number
        // that is not present on 2.1/2.2 nodes. We know this is ok however because we know that 2.1/2.2 nodes will ignore
        // anything remaining once they have properly deserialized a paging state.
        assertTrue(serializedState.startsWith("0007736f6d654b65790014000263310000040000002a0000056d79636f6c000000000a"));
    }

    @Test
    public void testSerializeDeserializeV3()
    {
        PagingState state = makeSomePagingState(Server.VERSION_3);
        ByteBuffer serialized = state.serialize(Server.VERSION_3);
        assertEquals(serialized.remaining(), state.serializedSize(Server.VERSION_3));
        assertEquals(state, PagingState.deserialize(serialized, Server.VERSION_3));
    }

    @Test
    public void testSerializeDeserializeV4()
    {
        PagingState state = makeSomePagingState(Server.VERSION_4);
        ByteBuffer serialized = state.serialize(Server.VERSION_4);
        assertEquals(serialized.remaining(), state.serializedSize(Server.VERSION_4));
        assertEquals(state, PagingState.deserialize(serialized, Server.VERSION_4));
    }
}
