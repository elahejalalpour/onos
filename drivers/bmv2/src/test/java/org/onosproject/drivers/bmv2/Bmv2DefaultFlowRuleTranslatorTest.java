/*
 * Copyright 2014-2016 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.drivers.bmv2;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.google.common.testing.EqualsTester;
import org.junit.Before;
import org.junit.Test;
import org.onlab.packet.MacAddress;
import org.onosproject.bmv2.api.model.Bmv2Model;
import org.onosproject.bmv2.api.runtime.Bmv2TableEntry;
import org.onosproject.bmv2.api.runtime.Bmv2TernaryMatchParam;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.drivers.bmv2.translators.Bmv2DefaultFlowRuleTranslator;
import org.onosproject.drivers.bmv2.translators.Bmv2FlowRuleTranslator;
import org.onosproject.drivers.bmv2.translators.Bmv2SimpleTranslatorConfig;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Random;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link Bmv2DefaultFlowRuleTranslator}.
 */
public class Bmv2DefaultFlowRuleTranslatorTest {

    private static final String JSON_CONFIG_PATH = "/simple.json";
    private Random random = new Random();
    private Bmv2Model model;
    private Bmv2FlowRuleTranslator.TranslatorConfig config;
    private Bmv2FlowRuleTranslator translator;

    @Before
    public void setUp() throws Exception {
        InputStream inputStream = Bmv2SimpleTranslatorConfig.class
                .getResourceAsStream(JSON_CONFIG_PATH);
        InputStreamReader reader = new InputStreamReader(inputStream);
        BufferedReader bufReader = new BufferedReader(reader);
        JsonObject json = null;
        try {
            json = Json.parse(bufReader).asObject();
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse JSON file: " + e.getMessage());
        }

        this.model = Bmv2Model.parse(json);
        this.config = new Bmv2SimpleTranslatorConfig(model);
        this.translator = new Bmv2DefaultFlowRuleTranslator(config);

    }


    @Test
    public void testCompiler() throws Exception {

        DeviceId deviceId = DeviceId.NONE;
        ApplicationId appId = new DefaultApplicationId(1, "test");
        int tableId = 0;
        MacAddress ethDstMac = MacAddress.valueOf(random.nextLong());
        MacAddress ethSrcMac = MacAddress.valueOf(random.nextLong());
        short ethType = (short) (0x0000FFFF & random.nextInt());
        short outPort = (short) random.nextInt(65);
        short inPort = (short) random.nextInt(65);
        int timeout = random.nextInt(100);
        int priority = random.nextInt(100);

        TrafficSelector matchInPort1 = DefaultTrafficSelector
                .builder()
                .matchInPort(PortNumber.portNumber(inPort))
                .matchEthDst(ethDstMac)
                .matchEthSrc(ethSrcMac)
                .matchEthType(ethType)
                .build();

        TrafficTreatment outPort2 = DefaultTrafficTreatment
                .builder()
                .setOutput(PortNumber.portNumber(outPort))
                .build();

        FlowRule rule1 = DefaultFlowRule.builder()
                .forDevice(deviceId)
                .forTable(tableId)
                .fromApp(appId)
                .withSelector(matchInPort1)
                .withTreatment(outPort2)
                .makeTemporary(timeout)
                .withPriority(priority)
                .build();

        FlowRule rule2 = DefaultFlowRule.builder()
                .forDevice(deviceId)
                .forTable(tableId)
                .fromApp(appId)
                .withSelector(matchInPort1)
                .withTreatment(outPort2)
                .makeTemporary(timeout)
                .withPriority(priority)
                .build();

        Bmv2TableEntry entry1 = translator.translate(rule1);
        Bmv2TableEntry entry2 = translator.translate(rule1);

        // check equality, i.e. same rules must produce same entries
        new EqualsTester()
                .addEqualityGroup(rule1, rule2)
                .addEqualityGroup(entry1, entry2)
                .testEquals();

        int numMatchParams = model.table(0).keys().size();
        // parse values stored in entry1
        Bmv2TernaryMatchParam inPortParam = (Bmv2TernaryMatchParam) entry1.matchKey().matchParams().get(0);
        Bmv2TernaryMatchParam ethDstParam = (Bmv2TernaryMatchParam) entry1.matchKey().matchParams().get(1);
        Bmv2TernaryMatchParam ethSrcParam = (Bmv2TernaryMatchParam) entry1.matchKey().matchParams().get(2);
        Bmv2TernaryMatchParam ethTypeParam = (Bmv2TernaryMatchParam) entry1.matchKey().matchParams().get(3);
        double expectedTimeout = (double) (model.table(0).hasTimeouts() ? rule1.timeout() : -1);

        // check that the number of parameters in the entry is the same as the number of table keys
        assertThat("Incorrect number of match parameters",
                   entry1.matchKey().matchParams().size(), is(equalTo(numMatchParams)));

        // check that values stored in entry are the same used for the flow rule
        assertThat("Incorrect inPort match param value",
                   inPortParam.value().asReadOnlyBuffer().getShort(), is(equalTo(inPort)));
        assertThat("Incorrect ethDestMac match param value",
                   ethDstParam.value().asArray(), is(equalTo(ethDstMac.toBytes())));
        assertThat("Incorrect ethSrcMac match param value",
                   ethSrcParam.value().asArray(), is(equalTo(ethSrcMac.toBytes())));
        assertThat("Incorrect ethType match param value",
                   ethTypeParam.value().asReadOnlyBuffer().getShort(), is(equalTo(ethType)));
        assertThat("Incorrect priority value",
                   entry1.priority(), is(equalTo(Integer.MAX_VALUE - rule1.priority())));
        assertThat("Incorrect timeout value",
                   entry1.timeout(), is(equalTo(expectedTimeout)));

    }
}