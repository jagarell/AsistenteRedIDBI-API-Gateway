package com.upc.idbi.gateway.asistente;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Escribe un .xlsx mínimo (una hoja) sin dependencias: título, fecha de generación, encabezados en negrita con filtro y datos.
 * Los valores numéricos (Integer, Long, Double) quedan como número; el resto, como texto.
 */
final class XlsxWriter {

    private XlsxWriter() {
    }

    static byte[] hoja(String titulo, String generado, List<String> encabezados, List<List<Object>> filas) {
        StringBuilder s = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        s.append("<cols>");
        for (int i = 0; i < encabezados.size(); i++) {
            s.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"").append(i == 0 || i == encabezados.size() - 1 ? 30 : 18).append("\" customWidth=\"1\"/>");
        }
        s.append("</cols><sheetData>");
        fila(s, 1, List.of(titulo), 2);
        fila(s, 2, List.of(generado), 0);
        fila(s, 4, new java.util.ArrayList<Object>(encabezados), 1);
        int r = 5;
        for (List<Object> f : filas) {
            fila(s, r++, f, 0);
        }
        s.append("</sheetData>");
        if (!filas.isEmpty()) {
            s.append("<autoFilter ref=\"A4:").append(col(encabezados.size() - 1)).append(4 + filas.size()).append("\"/>");
        }
        s.append("</worksheet>");
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream(); ZipOutputStream z = new ZipOutputStream(bo)) {
            put(z, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
            put(z, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(z, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Reporte\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(z, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            put(z, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"14\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1565C0\"/></patternFill></fill></fills>"
                    + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs></styleSheet>");
            put(z, "xl/worksheets/sheet1.xml", s.toString());
            z.finish();
            return bo.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void fila(StringBuilder s, int n, List<?> celdas, int estilo) {
        s.append("<row r=\"").append(n).append("\">");
        for (int i = 0; i < celdas.size(); i++) {
            Object v = celdas.get(i);
            String ref = col(i) + n;
            if (v instanceof Number num) {
                s.append("<c r=\"").append(ref).append("\"").append(estilo > 0 ? " s=\"" + estilo + "\"" : "").append("><v>").append(num).append("</v></c>");
            } else {
                s.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"").append(estilo > 0 ? " s=\"" + estilo + "\"" : "").append("><is><t xml:space=\"preserve\">")
                        .append(esc(String.valueOf(v == null ? "" : v))).append("</t></is></c>");
            }
        }
        s.append("</row>");
    }

    private static String col(int i) {
        return String.valueOf((char) ('A' + i));
    }

    private static String esc(String t) {
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void put(ZipOutputStream z, String name, String contenido) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(contenido.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
