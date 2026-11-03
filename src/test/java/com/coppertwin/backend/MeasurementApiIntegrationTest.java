package com.coppertwin.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.coppertwin.backend.measurement.IngestService;
import com.coppertwin.backend.measurement.MeasurementModels.ReadingBatchRequest;
import com.coppertwin.backend.measurement.MeasurementModels.ReadingInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class MeasurementApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired IngestService ingestService;
    @Autowired JdbcTemplate jdbc;

    @Test void healthOpenApiAndSeedMasterDataAreAvailable() throws Exception {
        mvc.perform(get("/api/v1/health/live")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK"));
        mvc.perform(get("/api/v1/master/sensors").param("workshopCode","WS-ROD"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data",hasSize(greaterThanOrEqualTo(7))))
                .andExpect(jsonPath("$.data[0].parameters").isArray());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/measurements/latest")));
    }

    @Test void batchDirectlyStoresReadingsAndUpdatesLatestAndTrend() throws Exception {
        OffsetDateTime time=OffsetDateTime.now(ZoneOffset.ofHours(8)).minusSeconds(5).withNano(0);
        String body=batch("it-good-1","TEMP-002","temperature",time,"1182",null,"C","GOOD");
        mvc.perform(post("/api/v1/ingest/sensor-readings:batch").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.acceptedCount").value(1));
        mvc.perform(post("/api/v1/ingest/sensor-readings:batch").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.acceptedCount").value(1));
        org.junit.jupiter.api.Assertions.assertEquals(1,jdbc.queryForObject(
                "SELECT COUNT(*) FROM ods_sensor_data WHERE source_system='integration-test' AND source_record_id='it-good-1'",Integer.class));
        mvc.perform(get("/api/v1/measurements/latest").param("sensorCodes","TEMP-002"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].value").value(1182))
                .andExpect(jsonPath("$.data.items[0].displayStatus").value("NORMAL"));
        mvc.perform(get("/api/v1/measurements/trends").param("sensorCode","TEMP-002").param("parameterCode","temperature")
                        .param("startTime",time.minusMinutes(1).toString()).param("endTime",time.plusMinutes(1).toString()).param("granularity","minute"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.points",hasSize(greaterThanOrEqualTo(1))));
    }

    @Test void unitConversionAndOutOfOrderReadingDoNotRegressSnapshot() throws Exception {
        OffsetDateTime newer=OffsetDateTime.now(ZoneOffset.ofHours(8)).minusSeconds(3).withNano(0);
        OffsetDateTime older=newer.minusMinutes(1);
        mvc.perform(post("/api/v1/ingest/sensor-readings:batch").contentType(MediaType.APPLICATION_JSON)
                .content(batch("it-pressure-new","PRESS-001","pressure",newer,"-0.018",null,"kPa","GOOD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.acceptedCount").value(1));
        mvc.perform(post("/api/v1/ingest/sensor-readings:batch").contentType(MediaType.APPLICATION_JSON)
                .content(batch("it-pressure-old","PRESS-001","pressure",older,"-0.020",null,"kPa","GOOD")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/measurements/latest").param("sensorCodes","PRESS-001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].value").value(-18));
    }

    @Test void partialFailureAndOverviewContractAreExplicit() throws Exception {
        OffsetDateTime time=OffsetDateTime.now(ZoneOffset.ofHours(8)).minusSeconds(2).withNano(0);
        String request="""
            {"sourceSystem":"integration-test","readings":[
              {"sourceRecordId":"it-alarm-1","sensorCode":"DIAM-WIRE-002","parameterCode":"diameter","collectTime":"%s","valueNum":1.840,"sourceUnit":"MM","qualityCode":"GOOD"},
              {"sourceRecordId":"it-unknown-1","sensorCode":"UNKNOWN","parameterCode":"value","collectTime":"%s","valueNum":1,"sourceUnit":"MM","qualityCode":"GOOD"}
            ]}
            """.formatted(time,time);
        mvc.perform(post("/api/v1/ingest/sensor-readings:batch").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.failedCount").value(1));
        String response=mvc.perform(get("/api/v1/production/process-overview/measurements"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.workshops",hasSize(2)))
                .andExpect(jsonPath("$.data.workshops[0].processes[0].processCode").value("ROD-FEED"))
                .andReturn().getResponse().getContentAsString();
        JsonNode root=json.readTree(response).path("data");
        org.junit.jupiter.api.Assertions.assertFalse(root.has("productionQuantity"));
        org.junit.jupiter.api.Assertions.assertFalse(root.has("energyConsumption"));
    }

    @Test void invalidTrendSpanUsesUnifiedError() throws Exception {
        OffsetDateTime end=OffsetDateTime.now(ZoneOffset.UTC);
        mvc.perform(get("/api/v1/measurements/trends").param("sensorCode","TEMP-001").param("parameterCode","temperature")
                        .param("startTime",end.minusHours(3).toString()).param("endTime",end.toString()).param("granularity","raw"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/measurements/trends").param("sensorCode","TEMP-001").param("parameterCode","temperature")
                        .param("startTime",end.minusHours(2).minusSeconds(1).toString()).param("endTime",end.toString()).param("granularity","raw"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/measurements/trends").param("sensorCode","TEMP-001"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/ingest/sensor-readings:batch").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test void equipmentMonitorContractsCoverSummaryListDetailLatestAndTrends() throws Exception {
        String list=mvc.perform(get("/api/v1/equipment").param("pageSize","20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items",hasSize(8)))
                .andExpect(jsonPath("$.data.items[*].status",hasItems("RUNNING","WARNING","ALARM","OFFLINE")))
                .andReturn().getResponse().getContentAsString();
        JsonNode items=json.readTree(list).path("data").path("items");
        long smeltId=0;
        for (JsonNode item:items) if ("SMELT-FUR-01".equals(item.path("equipmentCode").asText())) smeltId=item.path("equipmentId").asLong();
        org.junit.jupiter.api.Assertions.assertTrue(smeltId>0);
        mvc.perform(get("/api/v1/equipment/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(8)).andExpect(jsonPath("$.data.alarmCount",greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/v1/equipment/{id}",smeltId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipmentCode").value("SMELT-FUR-01"));
        mvc.perform(get("/api/v1/equipment/{id}/parameters/latest",smeltId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items",hasSize(5))).andExpect(jsonPath("$.data.items[0].positionX").isNumber());
        OffsetDateTime end=OffsetDateTime.now(ZoneOffset.ofHours(8)).plusMinutes(1);
        mvc.perform(get("/api/v1/equipment/{id}/parameters/trends",smeltId)
                        .param("parameterCodes","temperature,pressure").param("startTime",end.minusHours(1).toString())
                        .param("endTime",end.toString()).param("granularity","minute"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.series",hasSize(greaterThanOrEqualTo(2))));
    }

    @Test void badQualityOfflineAndRawOnlyStatesAreVisible() throws Exception {
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.ofHours(8)).withNano(0);
        mvc.perform(post("/api/v1/ingest/sensor-readings:batch").contentType(MediaType.APPLICATION_JSON)
                .content(batch("it-bad-quality","TEMP-001","temperature",now.minusSeconds(2),"1300",null,"C","BAD")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/measurements/latest").param("sensorCodes","TEMP-001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].limitStatus").value("ALARM"))
                .andExpect(jsonPath("$.data.items[0].displayStatus").value("DATA_INVALID"));

        mvc.perform(post("/api/v1/ingest/sensor-readings:batch").contentType(MediaType.APPLICATION_JSON)
                .content(batch("it-offline","TEMP-003","temperature",now.minusMinutes(10),"1169",null,"C","GOOD")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/measurements/latest").param("sensorCodes","TEMP-003"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].displayStatus").value("OFFLINE"));

        mvc.perform(post("/api/v1/ingest/sensor-readings:batch").contentType(MediaType.APPLICATION_JSON)
                .content(batch("it-raw-only","LEVEL-001","level",now.minusSeconds(1),"70",null,"N","GOOD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.items[0].errorCode").value("RAW_ONLY"));
    }

    @Test void sensorsCanBeFilteredByProcessWithoutABindingTable() throws Exception {
        mvc.perform(get("/api/v1/master/sensors").param("processCode","ROD-MELT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data",hasSize(greaterThanOrEqualTo(2))));
    }

    @Test void fiveThousandReadingsHaveAnExecutablePerformanceBaseline() {
        OffsetDateTime time=OffsetDateTime.now(ZoneOffset.ofHours(8)).minusSeconds(1).withNano(0);
        var readings=IntStream.range(0,5000).mapToObj(i->new ReadingInput(
                "perf-"+i,"TEMP-002","temperature",time.plusNanos(i*1000L),
                BigDecimal.valueOf(1180+(i%5)),null,"C","GOOD")).toList();
        var result=org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(30),
                ()->ingestService.ingest(new ReadingBatchRequest("PERFORMANCE_TEST",readings)));
        org.junit.jupiter.api.Assertions.assertEquals(5000,result.acceptedCount());
    }

    private String batch(String id,String sensor,String parameter,OffsetDateTime time,String number,String text,String unit,String quality) {
        String value=number!=null?"\"valueNum\":"+number:"\"valueText\":\""+text+"\"";
        return "{\"sourceSystem\":\"integration-test\",\"readings\":[{\"sourceRecordId\":\""+id+"\",\"sensorCode\":\""+sensor+
                "\",\"parameterCode\":\""+parameter+"\",\"collectTime\":\""+time+"\","+value+",\"sourceUnit\":\""+unit+"\",\"qualityCode\":\""+quality+"\"}]}";
    }
}
