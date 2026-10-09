/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class XMLReaderUtils_getMaxEntityExpansions_33_0_Test {

    @Mock
    private XMLReaderUtils xmlReaderUtils;

    @BeforeEach
    public void setUp() {
        xmlReaderUtils = new XMLReaderUtils();
    }

    @Test
    public void testGetMaxEntityExpansions() throws Exception {
        Method method = xmlReaderUtils.getClass().getMethod("getMaxEntityExpansions");
        int maxEntityExpansions = (int) method.invoke(xmlReaderUtils);
        assertTrue(maxEntityExpansions > 0);
        assertEquals(XMLReaderUtils.DEFAULT_MAX_ENTITY_EXPANSIONS, maxEntityExpansions);
    }
}
