/*
 *
 * Copyright 2016 EUROPEAN COMMISSION
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/community/eupl/og_page/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 *
 */

package eu.europa.ec.grow.espd.controller;

import com.google.common.base.Optional;
import eu.europa.ec.grow.espd.domain.EspdDocument;
import eu.europa.ec.grow.espd.xml.EspdXmlExporter;
import eu.europa.ec.grow.espd.xml.EspdXmlImporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@RestController
@RequestMapping("/api")
public class ApiEspdController {

    private final EspdXmlImporter xmlImporter;
    private final EspdXmlExporter xmlExporter;

    @Autowired
    public ApiEspdController(EspdXmlImporter xmlImporter, EspdXmlExporter xmlExporter) {
        this.xmlImporter = xmlImporter;
        this.xmlExporter = xmlExporter;
    }

    @PostMapping("/import")
    public ResponseEntity<?> importEspd(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        try (InputStream is = file.getInputStream()) {
            Optional<EspdDocument> espdDocument = xmlImporter.importAmbiguousEspdFile(is);
            if (espdDocument.isPresent()) {
                return ResponseEntity.ok(espdDocument.get());
            } else {
                return ResponseEntity.badRequest().body("Could not parse ESPD file");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        }
    }

    @PostMapping("/export")
    public ResponseEntity<?> exportEspd(@RequestBody EspdDocument espdDocument) {
        try {
            boolean isResponse = espdDocument.getEconomicOperator() != null;

            ByteArrayOutputStream out = isResponse
                    ? xmlExporter.generateEspdResponse(espdDocument)
                    : xmlExporter.generateEspdRequest(espdDocument);

            String fileName = isResponse ? "espd-response.xml" : "espd-request.xml";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setContentDispositionFormData("attachment", fileName);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(out.toByteArray());

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error generating XML: " + e.getMessage());
        }
    }
}