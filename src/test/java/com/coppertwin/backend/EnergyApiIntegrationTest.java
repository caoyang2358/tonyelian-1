package com.coppertwin.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class EnergyApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void completeEnergyReadContractsAreAvailable() throws Exception {
        Integer removed=jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('dim_energy_meter','app_energy_target','app_energy_task','app_energy_task_audit')
            """,Integer.class);
        assertEquals(0,removed);
        mvc.perform(get("/api/v1/energy/overview")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics",hasSize(3)))
                .andExpect(jsonPath("$.data.metrics[*].energyCode",hasItems("ELECTRICITY","WATER","COAL_GAS")))
                .andExpect(jsonPath("$.data.unitElectricityConsumption",greaterThan(0.0)));
        mvc.perform(get("/api/v1/energy/workshops/comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workshops",hasSize(2)))
                .andExpect(jsonPath("$.data.workshops[0].productionStage",not(is("NO_PRODUCTION_STAGE"))));
        mvc.perform(get("/api/v1/energy/equipment/ranking")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items",hasSize(greaterThanOrEqualTo(4))));
        mvc.perform(get("/api/v1/energy/equipment/benchmark").param("equipmentCode","DRAW-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].equipmentCode",startsWith("DRAW")));
        mvc.perform(get("/api/v1/energy/flow").param("energyCode","ELECTRICITY")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roots[0].children",hasSize(2)));
        mvc.perform(get("/api/v1/energy/balance").param("energyCode","ELECTRICITY")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items",hasSize(greaterThanOrEqualTo(1))));
    }

    @Test void workshopAndEquipmentEnergyHaveStableContracts() throws Exception {
        long workshopId=jdbc.queryForObject("SELECT workshop_id FROM dim_workshop WHERE workshop_code='WS-ROD'",Long.class);
        long equipmentId=jdbc.queryForObject("SELECT equipment_id FROM dim_equipment WHERE equipment_code='SMELT-FUR-01'",Long.class);
        mvc.perform(get("/api/v1/energy/workshops/{id}/dashboard",workshopId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workshopCode").value("WS-ROD"));
        mvc.perform(get("/api/v1/equipment/{id}/energy",equipmentId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consumption",greaterThan(0.0)));
    }

    @Test void cumulativeResetIsRecognizedAndRebuildIsTraceable() throws Exception {
        ZoneId zone=ZoneId.of("Asia/Shanghai"); LocalDate day=LocalDate.now(zone).minusDays(1);
        OffsetDateTime base=day.atTime(10,0).atZone(zone).toOffsetDateTime();
        BigDecimal[] values={new BigDecimal("100"),new BigDecimal("110"),new BigDecimal("2"),new BigDecimal("5")};
        for(int i=0;i<values.length;i++) {
            String body="""
                {"sourceSystem":"energy-it","readings":[{"sourceRecordId":"energy-reset-%d","sensorCode":"EM-E-SITE",
                 "parameterCode":"cumulative","collectTime":"%s","valueNum":%s,"sourceUnit":"KWH","qualityCode":"GOOD"}]}
                """.formatted(i,base.plusMinutes(i*10L),values[i]);
            mvc.perform(post("/api/v1/ingest/sensor-readings:batch").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.acceptedCount").value(1));
        }
        mvc.perform(post("/api/v1/energy/aggregations/{date}:rebuild",day)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meterCount",greaterThanOrEqualTo(16)));
        BigDecimal amount=jdbc.queryForObject("""
            SELECT h.consumption FROM dws_energy_hour h JOIN dim_sensor s ON s.sensor_id=h.sensor_id
             WHERE s.sensor_code='EM-E-SITE' AND h.bucket_time>=? AND h.bucket_time<?
            """,BigDecimal.class,base.withMinute(0),base.withMinute(0).plusHours(1));
        assertEquals(0,new BigDecimal("15").compareTo(amount));
    }

    @Test void cumulativeConsumptionAcrossHourBoundaryIsNotLost() throws Exception {
        ZoneId zone=ZoneId.of("Asia/Shanghai"); LocalDate day=LocalDate.now(zone).minusDays(2);
        OffsetDateTime before=day.atTime(10,50).atZone(zone).toOffsetDateTime();
        BigDecimal[] values={new BigDecimal("200"),new BigDecimal("210")};
        OffsetDateTime[] times={before,before.plusMinutes(20)};
        for(int i=0;i<2;i++) {
            String body="""
                {"sourceSystem":"energy-boundary-it","readings":[{"sourceRecordId":"energy-boundary-%d","sensorCode":"EM-E-SITE",
                 "parameterCode":"cumulative","collectTime":"%s","valueNum":%s,"sourceUnit":"KWH","qualityCode":"GOOD"}]}
                """.formatted(i,times[i],values[i]);
            mvc.perform(post("/api/v1/ingest/sensor-readings:batch").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.acceptedCount").value(1));
        }
        mvc.perform(post("/api/v1/energy/aggregations/{date}:rebuild",day)).andExpect(status().isOk());
        BigDecimal amount=jdbc.queryForObject("""
            SELECT h.consumption FROM dws_energy_hour h JOIN dim_sensor s ON s.sensor_id=h.sensor_id
             WHERE s.sensor_code='EM-E-SITE' AND h.bucket_time>=? AND h.bucket_time<?
            """,BigDecimal.class,day.atTime(11,0).atZone(zone).toOffsetDateTime(),day.atTime(12,0).atZone(zone).toOffsetDateTime());
        assertEquals(0,new BigDecimal("10").compareTo(amount));
    }
}
