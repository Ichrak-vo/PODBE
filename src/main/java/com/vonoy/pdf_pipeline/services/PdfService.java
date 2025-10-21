package com.vonoy.pdf_pipeline.services;

import com.vonoy.pdf_pipeline.api.dto.PdfJobRequest;

import java.io.InputStream;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.itextpdf.text.pdf.languages.ArabicLigaturizer;
import com.itextpdf.text.pdf.languages.LanguageProcessor;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PdfService {

    private final TemplateEngine templateEngine;

    @Value("${pdf.output-dir:results}")
    private String outputDir;

    private static final Map<String, String> TEMPLATE_BY_KEY = Map.of(
            "invoice:v1", "invoice.v1" 
    );


public byte[] generate(PdfJobRequest req) {
    final String apiKey = req.getApiKey();
    final String templateId = TEMPLATE_BY_KEY.get(apiKey);
    if (templateId == null || templateId.isBlank()) {
        throw new IllegalArgumentException("Unknown apiKey: " + apiKey);
    }

    final LanguageProcessor processor = new ArabicLigaturizer();
    final Context context = new Context();

    context.setVariable("logoUrl", "/images/logo.png");

    context.setVariable("logoBase64",
        encodeImageToBase64("src/main/resources/static/images/logo.png"));

    final Map<String, Object> dataMap = req.getData(); 
    if (dataMap != null) {
        Object imgObj = dataMap.get("imageBase64");
        String imgBase64 = (imgObj instanceof String) ? ((String) imgObj).trim() : null;

        if (imgBase64 != null && !imgBase64.isEmpty()) {
            String mime = "image/jpeg"; 
            if (imgBase64.startsWith("data:")) {
                int comma = imgBase64.indexOf(',');
                if (comma > 0) {
                    String header = imgBase64.substring(5, comma); 
                    if (header.startsWith("image/")) {
                        int semi = header.indexOf(';');
                        mime = (semi > 0) ? header.substring(0, semi) : header; 
                    }
                    imgBase64 = imgBase64.substring(comma + 1); 
                }
            } else {
                if (imgBase64.startsWith("iVBOR"))      mime = "image/png";
                else if (imgBase64.startsWith("/9j/"))  mime = "image/jpeg";
                else if (imgBase64.startsWith("R0lGOD")) mime = "image/gif";
            }
            final String dataUri = "data:" + mime + ";base64," + imgBase64;
            context.setVariable("proofBase64", dataUri);
        }
    }

    context.setVariable("compTel1req", processor.process("ﻫﺎﺗﻒ:"));
    context.setVariable("compTel2req", processor.process("ﻓﺎﻛﺲ:"));
    context.setVariable("compTel1",   processor.process("4022251 6 +962 "));
    context.setVariable("compTel2",   processor.process("4022626 6 +962"));
    context.setVariable("emailadd",   processor.process("info@finehh.com"));
    context.setVariable("link",       processor.process("www.finehh.com"));
    context.setVariable("address",    processor.process("ص.ب. 154 عمان 11118 الأردن"));
    context.setVariable("compName",   processor.process("ﺷﺮﻛﺔ ﻓﺎﻳﻦ ﻟﺼﻨﺎﻋﺔ ﺍﻟﻮﺭﻕ ﺍﻟﺼﺤﻲ ﺫ.ﻡ.ﻡ"));
    context.setVariable("footerLine1", processor.process(
        "لأي استفسارات أو لإعادة جدولة التسليم، يُرجى التواصل مع فريق التوزيع أو السائق مباشرةً."));
    context.setVariable("footerLine2", processor.process(
        "يُرجى التأكد من تواجد المستلم أو الممثل المفوَّض في موقع التسليم خلال الوقت المحدد."));

    if (dataMap != null) {
        java.util.function.Function<Object, String> asString =
            v -> v == null ? "" : String.valueOf(v).trim();

        @SuppressWarnings("unchecked")
        java.util.function.Function<Object, Map<String, Object>> asMap =
            v -> (v instanceof Map) ? (Map<String, Object>) v : null;

        String customerName = asString.apply(dataMap.get("customerName"));
        if (customerName.isBlank()) {
            Map<String, Object> customer = asMap.apply(dataMap.get("customer"));
            if (customer != null) {
                customerName = asString.apply(
                    customer.containsKey("name") ? customer.get("name") : customer.get("customerName"));
            }
        }

        String driverName = asString.apply(dataMap.get("driverName"));
        if (driverName.isBlank()) {
            Map<String, Object> driver = asMap.apply(dataMap.get("driver"));
            if (driver != null) {
                driverName = asString.apply(
                    driver.containsKey("name") ? driver.get("name") : driver.get("driverName"));
            }
        }

        String deliveryDate = asString.apply(dataMap.get("deliveryDate"));

        String proofLine = "فيما يلي إثبات التسليم المنفَّذ من قبل السائق "
            + driverName + " إلى العميل " + customerName + " بتاريخ " + deliveryDate;
        context.setVariable("proofLine", processor.process(proofLine));

        try {
            if (!deliveryDate.isBlank()) {
                java.time.LocalDate d = java.time.LocalDate.parse(deliveryDate);
                context.setVariable("deliveryDateObj", d);
            }
        } catch (Exception ignore) {
        }
    }

    final String html = templateEngine.process(templateId, context);

    return convertHtmlToPdf(html);
}


    private byte[] convertHtmlToPdf(String html) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();

            String baseUri = resolveStaticBaseUri(); // ex: file:/.../target/classes/static/
            builder.withHtmlContent(html, baseUri);

            InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoNaskhArabic-VariableFont_wght.ttf");
            if (fontStream != null) {
                builder.useFont(() -> fontStream, "Noto Naskh Arabic", 400, PdfRendererBuilder.FontStyle.NORMAL, true);
            } else {
                System.err.println("Arabic font not found in classpath!");
            }

            builder.defaultTextDirection(PdfRendererBuilder.TextDirection.RTL);

            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("HTML->PDF failed: " + e.getMessage(), e);
        }
    }

    private String resolveStaticBaseUri() {
        URL u = getClass().getResource("/static/");
        return (u != null) ? u.toExternalForm() : new File(".").toURI().toString();
    }

    private String encodeImageToBase64(String imagePath) {
        try {
            File file = new File(imagePath);
            if (!file.exists()) {
                System.err.println("Image not found: " + imagePath);
                return "";
            }
            byte[] imageBytes = Files.readAllBytes(file.toPath());

            // Devine un mimetype simple en fonction de l’extension
            String lower = imagePath.toLowerCase();
            String mime = lower.endsWith(".png") ? "image/png"
                       : lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg"
                       : lower.endsWith(".svg") ? "image/svg+xml"
                       : "application/octet-stream";

            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            System.err.println(" Base64 encode error for " + imagePath + ": " + e.getMessage());
            return "";
        }
    }
}
