package com.fleethub;

import com.fleethub.integration.dto.FuelTransactionDto;
import com.fleethub.integration.dto.TachographDayDto;
import com.fleethub.integration.parser.DddFileParser;
import com.fleethub.integration.parser.FuelFileParser;
import com.fleethub.integration.parser.TachoFileParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FileImportParserTest {

    // ---- TachoFileParser ----

    @Test
    void parseTachoCsv() {
        String csv = """
                licence_number,date,driving_hours,work_hours,rest_minutes
                FR-104-852-371,2026-08-15,8.5,10.0,480
                FR-104-741-258,2026-08-15,7.25,9.0,510
                """;
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        TachoFileParser parser = new TachoFileParser();
        TachoFileParser.ParseResult result = parser.parse(is);

        assertEquals(2, result.rows().size());
        assertTrue(result.errors().isEmpty());

        TachographDayDto row1 = result.rows().get(0);
        assertEquals("FR-104-852-371", row1.licenseNumber());
        assertEquals(LocalDate.of(2026, 8, 15), row1.date());
        assertEquals(8.5, row1.drivingHours(), 0.01);
        assertEquals(10.0, row1.workHours(), 0.01);
        assertEquals(480.0, row1.restMinutes(), 0.01);
    }

    @Test
    void parseTachoCsvWithErrors() {
        String csv = """
                licence_number,date,driving_hours,work_hours,rest_minutes
                ,2026-08-15,8.5,10.0,480
                FR-104-852-371,invalid-date,8.5,10.0,480
                FR-104-741-258,2026-08-15,7.25,9.0,510
                """;
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        TachoFileParser parser = new TachoFileParser();
        TachoFileParser.ParseResult result = parser.parse(is);

        assertEquals(1, result.rows().size());
        assertEquals(2, result.errors().size());
        assertEquals("FR-104-741-258", result.rows().get(0).licenseNumber());
    }

    @Test
    void parseTachoEmptyFile() {
        InputStream is = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        TachoFileParser parser = new TachoFileParser();
        TachoFileParser.ParseResult result = parser.parse(is);

        assertTrue(result.rows().isEmpty());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void parseTachoAlternateHeaders() {
        String csv = """
                license_number,date,driving,work,rest
                FR-104-852-371,2026-08-15,8.5,10.0,480
                """;
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        TachoFileParser parser = new TachoFileParser();
        TachoFileParser.ParseResult result = parser.parse(is);

        assertEquals(1, result.rows().size());
        assertEquals(8.5, result.rows().get(0).drivingHours(), 0.01);
    }

    // ---- FuelFileParser ----

    @Test
    void parseFuelCsv() {
        String csv = """
                registration,date,liters,amount,odometer_km
                GT-123-AB,2026-08-15,120.5,185.30,125000
                GT-456-CD,2026-08-15,95.0,143.55,87650
                """;
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        FuelFileParser parser = new FuelFileParser();
        FuelFileParser.ParseResult result = parser.parse(is);

        assertEquals(2, result.rows().size());
        assertTrue(result.errors().isEmpty());

        FuelTransactionDto row1 = result.rows().get(0);
        assertEquals("GT-123-AB", row1.registration());
        assertEquals(LocalDate.of(2026, 8, 15), row1.date());
        assertEquals(120.5, row1.liters(), 0.01);
        assertEquals(185.30, row1.amount(), 0.01);
        assertEquals(125000.0, row1.odometerKm(), 0.01);
    }

    @Test
    void parseFuelCsvFrenchDate() {
        String csv = """
                registration,date,liters,amount,odometer_km
                GT-123-AB,15/08/2026,120.5,185.30,125000
                """;
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        FuelFileParser parser = new FuelFileParser();
        FuelFileParser.ParseResult result = parser.parse(is);

        assertEquals(1, result.rows().size());
        assertEquals(LocalDate.of(2026, 8, 15), result.rows().get(0).date());
    }

    @Test
    void parseFuelSemicolonSeparated() {
        String csv = """
                immatriculation;date;volume;montant;kilometrage
                GT-123-AB;2026-08-15;120,5;185,30;125000
                """;
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        FuelFileParser parser = new FuelFileParser();
        FuelFileParser.ParseResult result = parser.parse(is);

        assertEquals(1, result.rows().size());
        assertEquals("GT-123-AB", result.rows().get(0).registration());
        assertEquals(120.5, result.rows().get(0).liters(), 0.01);
    }

    @Test
    void parseFuelEuropeanFormat() {
        String csv = """
                registration,date,liters,amount,odometer_km
                GT-123-AB,2026-08-15,"1.234,56","2.185,30",125000
                """;
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        FuelFileParser parser = new FuelFileParser();
        FuelFileParser.ParseResult result = parser.parse(is);

        assertEquals(1, result.rows().size());
        assertEquals(1234.56, result.rows().get(0).liters(), 0.01);
        assertEquals(2185.30, result.rows().get(0).amount(), 0.01);
    }

    @Test
    void parseFuelWithErrors() {
        String csv = """
                registration,date,liters,amount,odometer_km
                ,2026-08-15,120.5,185.30,125000
                GT-123-AB,bad-date,120.5,185.30,125000
                GT-456-CD,2026-08-15,0,0,125000
                GT-789-EF,2026-08-15,95.0,143.55,87650
                """;
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        FuelFileParser parser = new FuelFileParser();
        FuelFileParser.ParseResult result = parser.parse(is);

        assertEquals(1, result.rows().size());
        assertEquals(3, result.errors().size());
        assertEquals("GT-789-EF", result.rows().get(0).registration());
    }

    @Test
    void parseFuelEmptyFile() {
        InputStream is = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        FuelFileParser parser = new FuelFileParser();
        FuelFileParser.ParseResult result = parser.parse(is);

        assertTrue(result.rows().isEmpty());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void parseFuelPositionalSemicolon() {
        String csv = """
                GT-123-AB;15/08/2026;120,50;185,30;125000
                GT-456-CD;16/08/2026;80,00;122,40;130500
                """;
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        FuelFileParser parser = new FuelFileParser();
        FuelFileParser.ParseResult result = parser.parse(is);

        assertEquals(2, result.rows().size());
        assertTrue(result.errors().isEmpty());
        assertEquals("GT-123-AB", result.rows().get(0).registration());
        assertEquals(LocalDate.of(2026, 8, 15), result.rows().get(0).date());
        assertEquals(120.50, result.rows().get(0).liters(), 0.01);
        assertEquals(185.30, result.rows().get(0).amount(), 0.01);
        assertEquals(125000.0, result.rows().get(0).odometerKm(), 0.01);
        assertEquals("GT-456-CD", result.rows().get(1).registration());
        assertEquals(LocalDate.of(2026, 8, 16), result.rows().get(1).date());
    }

    @Test
    void parseFuelPositionalComma() {
        String csv = """
                GT-123-AB,2026-08-15,120.5,185.30,125000
                """;
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        FuelFileParser parser = new FuelFileParser();
        FuelFileParser.ParseResult result = parser.parse(is);

        assertEquals(1, result.rows().size());
        assertEquals("GT-123-AB", result.rows().get(0).registration());
    }

    @Test
    void parseFuelPositionalWithErrors() {
        String csv = """
                ;15/08/2026;120,50;185,30;125000
                GT-456-CD;bad-date;80,00;122,40;130500
                GT-789-EF;17/08/2026;0;0;140000
                """;
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        FuelFileParser parser = new FuelFileParser();
        FuelFileParser.ParseResult result = parser.parse(is);

        assertTrue(result.rows().isEmpty());
        assertEquals(3, result.errors().size());
    }

    @Test
    void parseFuelPositionalMinCols() {
        String csv = """
                GT-123-AB;15/08/2026;120,50
                """;
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        FuelFileParser parser = new FuelFileParser();
        FuelFileParser.ParseResult result = parser.parse(is);

        assertEquals(1, result.rows().size());
        assertEquals(120.50, result.rows().get(0).liters(), 0.01);
        assertEquals(0.0, result.rows().get(0).amount(), 0.01);
    }
}
