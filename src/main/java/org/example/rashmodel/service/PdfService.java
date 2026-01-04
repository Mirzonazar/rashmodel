package org.example.rashmodel.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import org.example.rashmodel.entity.UserAnswer;
import org.example.rashmodel.entity.UserTest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Service
public class PdfService {

    public ByteArrayInputStream generateResultPdf(UserTest test) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.add(new Paragraph("Milliy Sertifikat Matematika Testi Natijasi")
                    .setBold().setFontSize(18));
            document.add(new Paragraph("Foydalanuvchi ID: " + test.getUserId()));
            document.add(new Paragraph("Boshlanish vaqti: " + test.getStartTime()));
            document.add(new Paragraph("Tugash vaqti: " + test.getFinishedAt()));
            document.add(new Paragraph("To'g'ri javoblar soni: " + test.getCorrectCount() + " / 45"));
            document.add(new Paragraph("Rasch Theta: " + String.format("%.2f", test.getAbilityScore())));
            document.add(new Paragraph("Taxminiy ball: " + String.format("%.1f", (test.getAbilityScore() + 4) / 8 * 75) + " / 75"));
            document.add(new Paragraph("Daraja: " + test.getLevel()).setBold().setFontSize(16));

            if (!test.getAnswers().isEmpty()) {
                document.add(new Paragraph("\nJavoblar tafsiloti:").setBold());

                Table table = new Table(4);
                table.addHeaderCell("Savol");
                table.addHeaderCell("Berilgan javob");
                table.addHeaderCell("Qism");
                table.addHeaderCell("Natija");

                for (UserAnswer ans : test.getAnswers()) {
                    table.addCell(String.valueOf(ans.getQuestion().getId()));
                    table.addCell(ans.getGivenAnswer() != null ? ans.getGivenAnswer() : "-");
                    table.addCell(ans.getSubStep() != null ? ans.getSubStep().replace("main_", "") : "main");
                    table.addCell(Boolean.TRUE.equals(ans.getCorrect()) ? "To'g'ri" : "Xato");
                }
                document.add(table);
            }

            document.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ByteArrayInputStream(out.toByteArray());
    }
}