package com.vonoy.pdf_pipeline.controller;

import com.vonoy.pdf_pipeline.api.dto.PdfJobRequest;
import com.vonoy.pdf_pipeline.services.PdfService;
import com.vonoy.pdf_pipeline.util.PdfUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pdf")
@Tag(name = "PDF Generation", description = "PDF document generation APIs")
public class PdfController {

    private final PdfService pdfService;

    public PdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    @Operation(
            summary = "Generates a PDF and returns it as binary data",
            description = "Receives a PdfJobRequest (JSON), generates a PDF in memory, and returns the PDF file directly"
    )
    @ApiResponse(responseCode = "200", description = "PDF generated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @PostMapping(produces = MediaType.APPLICATION_PDF_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@Valid @RequestBody PdfJobRequest req) {
        byte[] pdfBytes = pdfService.generate(req);
        String fileName = PdfUtils.resolveFileName(req.getOutputFileName());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename(fileName).build());
        headers.setContentLength(pdfBytes.length);
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}