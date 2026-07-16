package com.upc.idbi.gateway.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Genera el PDF de la propuesta técnica con Apache PDFBox. Todo el contenido
 * viene de {@link ProposalPdfRequest} (datos reales del chat/AnalysisEngine);
 * este servicio solo compone el documento.
 */
@Service
public class PdfService {

    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    public byte[] generateProposalPdf(ProposalPdfRequest request) {
        try (PDDocument document = new PDDocument()) {
            Writer writer = new Writer(document);

            writer.title(request.establishmentName() == null || request.establishmentName().isBlank()
                    ? "Propuesta de Infraestructura de Red"
                    : request.establishmentName());
            writer.subtitle("Propuesta de Infraestructura de Red");
            writer.blank();

            writer.label("Fecha: " + LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("es", "ES"))));
            if (request.address() != null && !request.address().isBlank()) {
                writer.label("Dirección: " + request.address());
            }
            if (request.technicianName() != null && !request.technicianName().isBlank()) {
                writer.label("Técnico: " + request.technicianName());
            }
            if (request.score() != null) {
                writer.label("Puntaje de infraestructura: " + request.score() + "/100");
            }
            writer.blank();

            if (request.summary() != null && !request.summary().isBlank()) {
                writer.heading("Resumen");
                writer.paragraph(request.summary());
                writer.blank();
            }

            List<String> recommendations = request.recommendations();
            if (recommendations != null && !recommendations.isEmpty()) {
                writer.heading("Recomendaciones");
                for (String recommendation : recommendations) {
                    writer.bullet(recommendation);
                }
                writer.blank();
            }

            List<ProposalPdfRequest.EquipmentLineDto> equipment = request.equipment();
            if (equipment != null && !equipment.isEmpty()) {
                writer.heading("Equipamiento recomendado");
                for (ProposalPdfRequest.EquipmentLineDto item : equipment) {
                    String desc = item.description() == null || item.description().isBlank()
                            ? "" : " — " + item.description();
                    writer.bullet(item.name() + desc + " (x" + item.quantity() + ")");
                }
                writer.blank();
            }

            if (request.topologyText() != null && !request.topologyText().isBlank()) {
                writer.heading("Topología de red");
                writer.monospaceBlock(request.topologyText());
            }

            writer.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la propuesta", e);
        }
    }

    /** Maneja el cursor de escritura, el salto de página y el ajuste de línea. */
    private static final class Writer {

        private final PDDocument document;
        private final PDFont bodyFont = PDType1Font.HELVETICA;
        private final PDFont boldFont = PDType1Font.HELVETICA_BOLD;
        private final PDFont monoFont = PDType1Font.COURIER;

        private PDPage page;
        private PDPageContentStream stream;
        private float cursorY;

        Writer(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            cursorY = PAGE_HEIGHT - MARGIN;
        }

        private void ensureSpace(float needed) throws IOException {
            if (cursorY - needed < MARGIN) {
                newPage();
            }
        }

        private void writeLine(String text, PDFont font, float fontSize, float lineHeight) throws IOException {
            ensureSpace(lineHeight);
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(MARGIN, cursorY);
            stream.showText(sanitize(text));
            stream.endText();
            cursorY -= lineHeight;
        }

        void title(String text) throws IOException {
            writeLine(text, boldFont, 20f, 26f);
        }

        void subtitle(String text) throws IOException {
            writeLine(text, bodyFont, 12f, 18f);
        }

        void heading(String text) throws IOException {
            ensureSpace(22f);
            cursorY -= 4f;
            writeLine(text, boldFont, 14f, 20f);
        }

        void label(String text) throws IOException {
            writeLine(text, bodyFont, 11f, 16f);
        }

        void blank() {
            cursorY -= 10f;
        }

        void paragraph(String text) throws IOException {
            for (String line : wrap(text, bodyFont, 11f, CONTENT_WIDTH)) {
                writeLine(line, bodyFont, 11f, 15f);
            }
        }

        void bullet(String text) throws IOException {
            for (String line : wrap("•  " + text, bodyFont, 11f, CONTENT_WIDTH)) {
                writeLine(line, bodyFont, 11f, 15f);
            }
        }

        void monospaceBlock(String text) throws IOException {
            for (String rawLine : text.split("\n")) {
                for (String line : wrap(rawLine, monoFont, 9.5f, CONTENT_WIDTH)) {
                    writeLine(line, monoFont, 9.5f, 13f);
                }
            }
        }

        void close() throws IOException {
            stream.close();
        }

        /** Ajusta el texto a líneas que no excedan el ancho disponible. */
        private List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
            List<String> lines = new java.util.ArrayList<>();
            for (String paragraph : text.split("\n")) {
                StringBuilder current = new StringBuilder();
                for (String word : paragraph.split(" ")) {
                    String candidate = current.isEmpty() ? word : current + " " + word;
                    if (width(candidate, font, fontSize) > maxWidth && !current.isEmpty()) {
                        lines.add(current.toString());
                        current = new StringBuilder(word);
                    } else {
                        current = new StringBuilder(candidate);
                    }
                }
                lines.add(current.toString());
            }
            return lines;
        }

        private float width(String text, PDFont font, float fontSize) throws IOException {
            return font.getStringWidth(sanitize(text)) / 1000f * fontSize;
        }

        /** PDFBox con fuentes estándar (WinAnsi) no soporta todo Unicode (p. ej. emojis, ⚠, →). */
        private String sanitize(String text) {
            return text
                    .replace("→", "->")
                    .replace("⚠", "!")
                    .replaceAll("[^\\x00-\\x7F\\u00C0-\\u00FF]", "?");
        }
    }
}
