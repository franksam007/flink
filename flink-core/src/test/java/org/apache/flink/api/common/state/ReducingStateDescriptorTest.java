/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.common.state;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.TaskInfo;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.base.StringSerializer;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.flink.core.fs.Path;
import org.apache.flink.core.testutils.CommonTestUtils;
import org.apache.flink.util.TestLogger;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link ReducingStateDescriptor}.
 */
public class ReducingStateDescriptorTest extends TestLogger {

	@Test
	public void testValueStateDescriptorEagerSerializer() throws Exception {

		@SuppressWarnings("unchecked")
		ReduceFunction<String> reducer = mock(ReduceFunction.class);

		TypeSerializer<String> serializer = new KryoSerializer<>(String.class, new ExecutionConfig());

		ReducingStateDescriptor<String> descr =
				new ReducingStateDescriptor<>("testName", reducer, serializer);

		assertEquals("testName", descr.getName());
		assertNotNull(descr.getSerializer());
		assertEquals(serializer, descr.getSerializer());

		ReducingStateDescriptor<String> copy = CommonTestUtils.createCopySerializable(descr);

		assertEquals("testName", copy.getName());
		assertNotNull(copy.getSerializer());
		assertEquals(serializer, copy.getSerializer());
	}

	@Test
	public void testValueStateDescriptorLazySerializer() throws Exception {

		@SuppressWarnings("unchecked")
		ReduceFunction<Path> reducer = mock(ReduceFunction.class);

		// some different registered value
		ExecutionConfig cfg = new ExecutionConfig();
		cfg.registerKryoType(TaskInfo.class);

		ReducingStateDescriptor<Path> descr =
				new ReducingStateDescriptor<>("testName", reducer, Path.class);

		try {
			descr.getSerializer();
			fail("should cause an exception");
		} catch (IllegalStateException ignored) {}

		descr.initializeSerializerUnlessSet(cfg);

		assertNotNull(descr.getSerializer());
		assertTrue(descr.getSerializer() instanceof KryoSerializer);

		assertTrue(((KryoSerializer<?>) descr.getSerializer()).getKryo().getRegistration(TaskInfo.class).getId() > 0);
	}

	@Test
	public void testValueStateDescriptorAutoSerializer() throws Exception {

		@SuppressWarnings("unchecked")
		ReduceFunction<String> reducer = mock(ReduceFunction.class);

		ReducingStateDescriptor<String> descr =
				new ReducingStateDescriptor<>("testName", reducer, String.class);

		ReducingStateDescriptor<String> copy = CommonTestUtils.createCopySerializable(descr);

		assertEquals("testName", copy.getName());
		assertNotNull(copy.getSerializer());
		assertEquals(StringSerializer.INSTANCE, copy.getSerializer());
	}

	/**
	 * FLINK-6775.
	 *
	 * <p>Tests that the returned serializer is duplicated. This allows to
	 * share the state descriptor.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testSerializerDuplication() {
		TypeSerializer<String> statefulSerializer = mock(TypeSerializer.class);
		when(statefulSerializer.duplicate()).thenAnswer(new Answer<TypeSerializer<String>>() {
			@Override
			public TypeSerializer<String> answer(InvocationOnMock invocation) throws Throwable {
				return mock(TypeSerializer.class);
			}
		});

		ReduceFunction<String> reducer = mock(ReduceFunction.class);

		ReducingStateDescriptor<String> descr = new ReducingStateDescriptor<>("foobar", reducer, statefulSerializer);

		TypeSerializer<String> serializerA = descr.getSerializer();
		TypeSerializer<String> serializerB = descr.getSerializer();

		// check that the retrieved serializers are not the same
		assertNotSame(serializerA, serializerB);
	}

}
