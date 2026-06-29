package com.example.todo.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.example.todo.mapper.UserMapper;
import com.example.todo.model.AppUser;
import com.example.todo.model.Todo;
import com.example.todo.service.TodoService;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Controller
@RequestMapping("/export")
public class ExportController {
    private final TodoService todoService;
    private final UserMapper userMapper;

    public ExportController(TodoService todoService, UserMapper userMapper) {
        this.todoService = todoService;
        this.userMapper = userMapper;
    }

    @GetMapping("/excel")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<byte[]> exportExcel(@AuthenticationPrincipal UserDetails principal) {
        AppUser loginUser = getAuthenticatedUser(principal);
        List<Todo> todos = todoService.findAll(isAdmin(loginUser) ? null : loginUser.getId());

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Todos");
            createExcelHeader(workbook, sheet);
            writeExcelRows(sheet, todos);
            for (int i = 0; i < 7; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return buildFileResponse(
                    out.toByteArray(),
                    "todos_" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            );
        } catch (IOException e) {
            throw new IllegalStateException("Excelエクスポートに失敗しました", e);
        }
    }

    @GetMapping("/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<byte[]> exportPdf(@AuthenticationPrincipal UserDetails principal) {
        AppUser loginUser = getAuthenticatedUser(principal);
        List<Todo> todos = todoService.findAll(isAdmin(loginUser) ? null : loginUser.getId());

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument);
            PdfFont font = PdfFontFactory.createFont("HeiseiKakuGo-W5", "UniJIS-UCS2-H");
            document.setFont(font);

            document.add(new Paragraph("ToDo一覧"));
            document.add(new Paragraph("出力日: " + LocalDate.now().format(DateTimeFormatter.ISO_DATE)));

            Table table = new Table(UnitValue.createPercentArray(new float[] {1.2f, 3.5f, 2.0f, 2.0f, 2.0f, 2.2f}));
            table.setWidth(UnitValue.createPercentValue(100));
            addPdfHeader(table, "ID");
            addPdfHeader(table, "タイトル");
            addPdfHeader(table, "作成者");
            addPdfHeader(table, "優先度");
            addPdfHeader(table, "カテゴリ");
            addPdfHeader(table, "期限日");

            for (Todo todo : todos) {
                table.addCell(new Cell().add(new Paragraph(String.valueOf(todo.getId() == null ? "" : todo.getId()))));
                table.addCell(new Cell().add(new Paragraph(nonNull(todo.getTitle()))));
                table.addCell(new Cell().add(new Paragraph(nonNull(todo.getAuthor()))));
                table.addCell(new Cell().add(new Paragraph(todo.getPriority() == null ? "" : todo.getPriority().name())));
                table.addCell(new Cell().add(new Paragraph(nonNull(todo.getCategoryName()))));
                table.addCell(new Cell().add(new Paragraph(todo.getDeadline() == null ? "" : todo.getDeadline().toString())));
            }

            document.add(table);
            document.close();

            return buildFileResponse(
                    out.toByteArray(),
                    "todos_" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".pdf",
                    "application/pdf"
            );
        } catch (IOException e) {
            throw new IllegalStateException("PDFエクスポートに失敗しました", e);
        }
    }

    private void createExcelHeader(XSSFWorkbook workbook, XSSFSheet sheet) {
        XSSFRow header = sheet.createRow(0);
        String[] columns = {"ID", "タイトル", "作成者", "優先度", "カテゴリ", "期限日", "完了"};
        CellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);

        for (int i = 0; i < columns.length; i++) {
            XSSFCell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeExcelRows(XSSFSheet sheet, List<Todo> todos) {
        for (int i = 0; i < todos.size(); i++) {
            Todo todo = todos.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(todo.getId() == null ? "" : String.valueOf(todo.getId()));
            row.createCell(1).setCellValue(nonNull(todo.getTitle()));
            row.createCell(2).setCellValue(nonNull(todo.getAuthor()));
            row.createCell(3).setCellValue(todo.getPriority() == null ? "" : todo.getPriority().name());
            row.createCell(4).setCellValue(nonNull(todo.getCategoryName()));
            row.createCell(5).setCellValue(todo.getDeadline() == null ? "" : todo.getDeadline().toString());
            row.createCell(6).setCellValue(Boolean.TRUE.equals(todo.getCompleted()) ? "完了" : "未完了");
        }
    }

    private void addPdfHeader(Table table, String text) {
        table.addHeaderCell(new Cell().add(new Paragraph(text).setBold()));
    }

    private ResponseEntity<byte[]> buildFileResponse(byte[] bytes, String fileName, String contentType) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }

    private AppUser getAuthenticatedUser(UserDetails principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ログインが必要です");
        }
        AppUser user = userMapper.findByUsername(principal.getUsername());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザー情報が見つかりません");
        }
        return user;
    }

    private boolean isAdmin(AppUser user) {
        return user != null && "ADMIN".equals(user.getRole());
    }

    private String nonNull(String value) {
        return value == null ? "" : value;
    }
}
