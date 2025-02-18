/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.e2e.connector.http;

import org.apache.seatunnel.shade.com.fasterxml.jackson.core.type.TypeReference;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.DeserializationFeature;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.seatunnel.e2e.common.TestResource;
import org.apache.seatunnel.e2e.common.TestSuiteBase;
import org.apache.seatunnel.e2e.common.container.EngineType;
import org.apache.seatunnel.e2e.common.container.TestContainer;
import org.apache.seatunnel.e2e.common.junit.DisabledOnContainer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestTemplate;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.ClearType;
import org.mockserver.model.Format;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.DockerLoggerFactory;
import org.testcontainers.utility.MountableFile;

import com.google.common.collect.Lists;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockserver.model.HttpRequest.request;

public class HttpIT extends TestSuiteBase implements TestResource {

    private static final String TMP_DIR = "/tmp";

    private static final String IMAGE = "mockserver/mockserver:5.14.0";

    private GenericContainer<?> mockserverContainer;

    private static final List<Record> records = new ArrayList<>();

    private MockServerClient mockServerClient;

    @BeforeAll
    @Override
    public void startUp() {
        Optional<URL> resource =
                Optional.ofNullable(HttpIT.class.getResource(getMockServerConfig()));
        this.mockserverContainer =
                new GenericContainer<>(DockerImageName.parse(IMAGE))
                        .withNetwork(NETWORK)
                        .withNetworkAliases("mockserver")
                        .withExposedPorts(1080)
                        .withCopyFileToContainer(
                                MountableFile.forHostPath(
                                        new File(
                                                        resource.orElseThrow(
                                                                        () ->
                                                                                new IllegalArgumentException(
                                                                                        "Can not get config file of mockServer"))
                                                                .getPath())
                                                .getAbsolutePath()),
                                TMP_DIR + getMockServerConfig())
                        .withEnv(
                                "MOCKSERVER_INITIALIZATION_JSON_PATH",
                                TMP_DIR + getMockServerConfig())
                        .withEnv("MOCKSERVER_LOG_LEVEL", "WARN")
                        .withLogConsumer(new Slf4jLogConsumer(DockerLoggerFactory.getLogger(IMAGE)))
                        .waitingFor(new HttpWaitStrategy().forPath("/").forStatusCode(404));
        mockserverContainer.setPortBindings(Lists.newArrayList(String.format("%s:%s", 1080, 1080)));
        Startables.deepStart(Stream.of(mockserverContainer)).join();
        mockServerClient = new MockServerClient("127.0.0.1", 1080);
        fillMockRecords();
    }

    private static void fillMockRecords() {
        Record recordFirst = new Record();
        RequestBody requestBodyFirst = new RequestBody();
        JsonBody jsonBodyFirst = new JsonBody();
        jsonBodyFirst.setId(1);
        jsonBodyFirst.setVal_bool(true);
        jsonBodyFirst.setVal_int8(new Byte("1"));
        jsonBodyFirst.setVal_int16((short) 2);
        jsonBodyFirst.setVal_int32(3);
        jsonBodyFirst.setVal_int64(4);
        jsonBodyFirst.setVal_float(4.3F);
        jsonBodyFirst.setVal_double(5.3);
        jsonBodyFirst.setVal_decimal(BigDecimal.valueOf(6.3));
        jsonBodyFirst.setVal_string("NEW");
        jsonBodyFirst.setVal_unixtime_micros("2020-02-02T02:02:02");
        requestBodyFirst.setJson(jsonBodyFirst);
        recordFirst.setBody(requestBodyFirst);

        Record recordSec = new Record();
        RequestBody requestBodySec = new RequestBody();
        JsonBody jsonBodySec = new JsonBody();
        jsonBodySec.setId(2);
        jsonBodySec.setVal_bool(true);
        jsonBodySec.setVal_int8(new Byte("1"));
        jsonBodySec.setVal_int16((short) 2);
        jsonBodySec.setVal_int32(3);
        jsonBodySec.setVal_int64(4);
        jsonBodySec.setVal_float(4.3F);
        jsonBodySec.setVal_double(5.3);
        jsonBodySec.setVal_decimal(BigDecimal.valueOf(6.3));
        jsonBodySec.setVal_string("NEW");
        jsonBodySec.setVal_unixtime_micros("2020-02-02T02:02:02");
        requestBodySec.setJson(jsonBodySec);
        recordSec.setBody(requestBodySec);
        records.add(recordFirst);
        records.add(recordSec);
    }

    @AfterAll
    @Override
    public void tearDown() {
        if (mockserverContainer != null) {
            mockserverContainer.stop();
        }
        if (mockServerClient != null) {
            mockServerClient.close();
        }
    }

    @TestTemplate
    public void testSourceToAssertSink(TestContainer container)
            throws IOException, InterruptedException {
        // normal http
        Container.ExecResult execResult1 = container.executeJob("/http_json_to_assert.conf");
        Assertions.assertEquals(0, execResult1.getExitCode());

        // http github
        Container.ExecResult execResult2 = container.executeJob("/github_json_to_assert.conf");
        Assertions.assertEquals(0, execResult2.getExitCode());

        // http gitlab
        Container.ExecResult execResult3 = container.executeJob("/gitlab_json_to_assert.conf");
        Assertions.assertEquals(0, execResult3.getExitCode());

        // http content json
        Container.ExecResult execResult4 = container.executeJob("/http_contentjson_to_assert.conf");
        Assertions.assertEquals(0, execResult4.getExitCode());

        // http jsonpath
        Container.ExecResult execResult5 = container.executeJob("/http_jsonpath_to_assert.conf");
        Assertions.assertEquals(0, execResult5.getExitCode());

        // http jira
        Container.ExecResult execResult6 = container.executeJob("/jira_json_to_assert.conf");
        Assertions.assertEquals(0, execResult6.getExitCode());

        // http klaviyo
        Container.ExecResult execResult7 = container.executeJob("/klaviyo_json_to_assert.conf");
        Assertions.assertEquals(0, execResult7.getExitCode());

        // http lemlist
        Container.ExecResult execResult8 = container.executeJob("/lemlist_json_to_assert.conf");
        Assertions.assertEquals(0, execResult8.getExitCode());

        // http notion
        Container.ExecResult execResult9 = container.executeJob("/notion_json_to_assert.conf");
        Assertions.assertEquals(0, execResult9.getExitCode());

        // http onesignal
        Container.ExecResult execResult10 = container.executeJob("/onesignal_json_to_assert.conf");
        Assertions.assertEquals(0, execResult10.getExitCode());

        // http persistiq
        Container.ExecResult execResult11 = container.executeJob("/persistiq_json_to_assert.conf");
        Assertions.assertEquals(0, execResult11.getExitCode());

        // http httpMultiLine
        Container.ExecResult execResult12 =
                container.executeJob("/http_multilinejson_to_assert.conf");
        Assertions.assertEquals(0, execResult12.getExitCode());

        // http httpFormRequestbody
        Container.ExecResult execResult13 =
                container.executeJob("/http_formrequestbody_to_assert.conf");
        Assertions.assertEquals(0, execResult13.getExitCode());

        // http httpJsonRequestBody
        Container.ExecResult execResult14 =
                container.executeJob("/http_jsonrequestbody_to_assert.conf");
        Assertions.assertEquals(0, execResult14.getExitCode());

        Container.ExecResult execResult15 =
                container.executeJob("/http_page_increase_page_num.conf");
        Assertions.assertEquals(0, execResult15.getExitCode());

        Container.ExecResult execResult16 =
                container.executeJob("/http_page_increase_no_page_num.conf");
        Assertions.assertEquals(0, execResult16.getExitCode());

        Container.ExecResult execResult17 =
                container.executeJob("/http_jsonrequestbody_to_feishu.conf");
        Assertions.assertEquals(0, execResult17.getExitCode());

        Container.ExecResult execResult18 = container.executeJob("/httpnoschema_to_http.conf");
        Assertions.assertEquals(0, execResult18.getExitCode());

        Container.ExecResult execResult19 =
                container.executeJob("/http_page_increase_start_num.conf");
        Assertions.assertEquals(0, execResult19.getExitCode());
    }

    @DisabledOnContainer(
            value = {},
            type = {EngineType.FLINK},
            disabledReason = "Currently FLINK do not support multiple table read")
    @TestTemplate
    public void testMultiTableHttp(TestContainer container)
            throws IOException, InterruptedException {
        Container.ExecResult execResult = container.executeJob("/fake_to_multitable.conf");
        Assertions.assertEquals(0, execResult.getExitCode());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String mockResponse =
                mockServerClient.retrieveRecordedRequests(
                        request().withPath("/example/httpMultiTableContentSink").withMethod("POST"),
                        Format.JSON);
        mockServerClient.clear(
                request().withPath("/example/httpMultiTableContentSink").withMethod("POST"),
                ClearType.LOG);
        List<Record> recordResponse =
                objectMapper.readValue(mockResponse, new TypeReference<List<Record>>() {});
        recordResponse =
                recordResponse.stream()
                        .sorted(
                                (r1, r2) ->
                                        r1.getBody().getJson().getId()
                                                - r2.getBody().getJson().getId())
                        .collect(Collectors.toList());
        Assertions.assertIterableEquals(records, recordResponse);
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    static class Record {
        private RequestBody body;
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    static class RequestBody {
        private JsonBody json;
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    static class JsonBody {
        private int id;
        private boolean val_bool;
        private byte val_int8;
        private short val_int16;
        private int val_int32;
        private long val_int64;
        private float val_float;
        private double val_double;
        private BigDecimal val_decimal;
        private String val_string;
        private String val_unixtime_micros;
    }

    public String getMockServerConfig() {
        return "/mockserver-config.json";
    }
}
