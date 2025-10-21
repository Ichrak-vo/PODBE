package com.vonoy.pdf_pipeline.services;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.vonoy.pdf_pipeline.api.dto.PdfJobRequest;

import java.io.InputStream;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class PdfService {

    private final TemplateEngine templateEngine;

    @Value("${pdf.output-dir:results}")
    private String outputDir;

    private static final Map<String, String> TEMPLATE_BY_KEY = Map.of(
            "invoice:v1", "invoice.v1",
            "invoice:v2", "invoice.v2",
            "invoice:v3", "invoice.v3"
    );
    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    private String resolveTemplate(String apiKey) {
        final String templateId = TEMPLATE_BY_KEY.get(apiKey);
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("Unknown apiKey: " + apiKey);
        }
        return templateId;
    }
    private void setStaticVariables(Context context, LanguageProcessor processor) {
        context.setVariable("logoUrl", "/images/logo.png");
        context.setVariable("logoBase64", encodeImageToBase64("src/main/resources/static/images/logo.png"));

        context.setVariable("compTel1req", processor.process("ﻫﺎﺗﻒ:"));
        context.setVariable("compTel2req", processor.process("ﻓﺎﻛﺲ:"));
        context.setVariable("compTel1", processor.process("4022251 6 +962 "));
        context.setVariable("compTel2", processor.process("4022626 6 +962"));
        context.setVariable("emailadd", processor.process("info@finehh.com"));
        context.setVariable("link", processor.process("www.finehh.com"));
        context.setVariable("address", processor.process("ص.ب. 154 عمان 11118 الأردن"));
        context.setVariable("compName", processor.process("ﺷﺮﻛﺔ ﻓﺎﻳﻦ ﻟﺼﻨﺎﻋﺔ ﺍﻟﻮﺭﻕ ﺍﻟﺼﺤﻲ ﺫ.ﻡ.ﻡ"));
        context.setVariable("footerLine1", processor.process(
                "لأي استفسارات أو لإعادة جدولة التسليم، يُرجى التواصل مع فريق التوزيع أو السائق مباشرةً."));
        context.setVariable("footerLine2", processor.process(
                "يُرجى التأكد من تواجد المستلم أو الممثل المفوَّض في موقع التسليم خلال الوقت المحدد."));
    }
    private void processDataMap(Map<String, Object> dataMap, Context context, LanguageProcessor processor) {
        if (dataMap == null) return;

        processProofImage(dataMap.get("imageBase64"), context);
        setProofLineAndDeliveryDate(dataMap, context, processor);
    }
    private void processProofImage(Object imgObj, Context context) {
        if (!(imgObj instanceof String imgBase64) || imgBase64.isBlank()) return;

        String mime = detectMimeType(imgBase64);
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
        }

        String dataUri = "data:" + mime + ";base64," + imgBase64;
        context.setVariable("proofBase64", dataUri);
    }
    private String detectMimeType(String base64) {
        if (base64.startsWith("iVBOR")) return "image/png";
        if (base64.startsWith("/9j/")) return "image/jpeg";
        if (base64.startsWith("R0lGOD")) return "image/gif";
        return "image/jpeg";
    }
    private void setProofLineAndDeliveryDate(Map<String, Object> dataMap, Context context, LanguageProcessor processor) {
        Function<Object, String> asString = v -> v == null ? "" : String.valueOf(v).trim();
        Function<Object, Map<String, Object>> asMap = v -> (v instanceof Map) ? (Map<String, Object>) v : null;

        String customerName = asString.apply(dataMap.get("customerName"));
        if (customerName.isBlank()) {
            Map<String, Object> customer = asMap.apply(dataMap.get("customer"));
            if (customer != null) {
                customerName = asString.apply(customer.getOrDefault("name", customer.get("customerName")));
            }
        }

        String driverName = asString.apply(dataMap.get("driverName"));
        if (driverName.isBlank()) {
            Map<String, Object> driver = asMap.apply(dataMap.get("driver"));
            if (driver != null) {
                driverName = asString.apply(driver.getOrDefault("name", driver.get("driverName")));
            }
        }

        String deliveryDate = asString.apply(dataMap.get("deliveryDate"));

        String proofLine = "فيما يلي إثبات التسليم المنفَّذ من قبل السائق "
                + driverName + " إلى العميل " + customerName + " بتاريخ " + deliveryDate;
        context.setVariable("proofLine", processor.process(proofLine));

        try {
            if (!deliveryDate.isBlank()) {
                context.setVariable("deliveryDateObj", java.time.LocalDate.parse(deliveryDate));
            }
        } catch (Exception ignore) {}
    }
    public byte[] generate(PdfJobRequest req) {
        final String templateId = resolveTemplate(req.getApiKey());
        final LanguageProcessor processor = new ArabicLigaturizer();
        final Context context = new Context();

        setStaticVariables(context, processor);
        processDataMap(req.getData(), context, processor);

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
                builder.useFont(() -> fontStream, "Noto Naskh Arabic", 400, BaseRendererBuilder.FontStyle.NORMAL, true);
            } else {
                log.error("Arabic font not found in classpath!");
            }

            builder.defaultTextDirection(BaseRendererBuilder.TextDirection.RTL);

            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("HTML->PDF conversion failed", e);        }
    }

    private String resolveStaticBaseUri() {
        URL u = getClass().getResource("/static/");
        return (u != null) ? u.toExternalForm() : new File(".").toURI().toString();
    }
    public static String getMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        } else {
            return "application/octet-stream";
        }
    }

    private String encodeImageToBase64(String imagePath) {
        try {
            File file = new File(imagePath);
            if (!file.exists()) {
                log.error("Image not found:" + imagePath);
                return "";
            }
            byte[] imageBytes = Files.readAllBytes(file.toPath());

            // Devine un mimetype simple en fonction de l’extension
            String lower = imagePath.toLowerCase();
            String mime = getMimeType(lower);


            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            log.error(" Base64 encode error for " + imagePath + ": " + e.getMessage());
            return "";
        }
    }
}
