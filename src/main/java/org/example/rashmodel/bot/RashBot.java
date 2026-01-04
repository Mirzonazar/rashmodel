package org.example.rashmodel.bot;

import org.example.rashmodel.config.BotConfig;
import org.example.rashmodel.entity.*;
import org.example.rashmodel.repository.*;
import org.example.rashmodel.service.*;
import org.example.rashmodel.util.RaschCalculator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RashBot extends TelegramLongPollingBot {

    private final TestService testService;
    private final UserTestRepository userTestRepository;
    private final QuestionRepository questionRepository;
    private final BotConfig botConfig;
    private final PdfService pdfService;

    public RashBot(TestService testService, UserTestRepository userTestRepository,
                   QuestionRepository questionRepository, BotConfig botConfig, PdfService pdfService) {
        this.testService = testService;
        this.userTestRepository = userTestRepository;
        this.questionRepository = questionRepository;
        this.botConfig = botConfig;
        this.pdfService = pdfService;
    }

    @Override
    public String getBotUsername() { return botConfig.getUsername(); }

    @Override
    public String getBotToken() { return botConfig.getToken(); }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        }
    }

    private void handleTextMessage(Message message) {
        String text = message.getText().trim();
        Long chatId = message.getChatId();

        if (text.equals("/start")) {
            sendWelcomeMessage(chatId);
            return;
        }

        UserTest test = userTestRepository.findByUserIdAndIsFinished(chatId, false);
        if (test != null) {
            Question q = questionRepository.findById((long) (test.getCurrentStep() + 1)).orElse(null);
            if (q != null && q.getIsDoubleAnswer()) {
                handleDoubleAnswer(test, q, text, chatId);
            }
        }
    }

    private void sendWelcomeMessage(Long chatId) {
        UserTest oldTest = userTestRepository.findByUserIdAndIsFinished(chatId, false);
        if (oldTest != null) {
            oldTest.setIsFinished(true);
            userTestRepository.save(oldTest);
        }

        UserTest test = testService.startNewTest(chatId, "MATEMATIKA");
        userTestRepository.save(test);

        sendTextMessage(chatId, """
                Assalomu alaykum! 👋
                
                Milliy Sertifikat Matematika testi (mock)
                Jami 45 ta savol.
                
                Test boshlandi! 🚀
                """);
        sendNextQuestion(test, chatId);
    }

    private void sendNextQuestion(UserTest test, Long chatId) {
        int current = test.getCurrentStep();
        long nextId = current + 1;

        if (nextId > 45) {
            finishTest(test, chatId);
            return;
        }

        Question q = questionRepository.findById(nextId).orElse(null);
        if (q == null) {
            finishTest(test, chatId);
            return;
        }

        String caption = "**" + nextId + "-savol**";

        if (q.getIsDoubleAnswer()) {
            caption += "\n\na) qismiga javob yuboring:";
        }

        // 34 va 35 — faqat matn + tugmalar
        if (nextId == 34 || nextId == 35) {
            SendMessage msg = new SendMessage(chatId.toString(), caption);
            msg.setParseMode("Markdown");
            msg.setReplyMarkup(createMatchingKeyboard());
            tryExecute(msg);

            test.setCurrentStep(current + 1);
            userTestRepository.save(test);
            return;
        }

        try {
            ClassPathResource res = new ClassPathResource(q.getInternalPath());
            InputStream is = res.getInputStream();

            SendPhoto photo = new SendPhoto();
            photo.setChatId(chatId.toString());
            photo.setPhoto(new InputFile(is, nextId + ".png"));
            photo.setCaption(caption);
            photo.setParseMode("Markdown");

            if (q.getIsBlockQuestion()) {
                photo.setReplyMarkup(createMatchingKeyboard());
            } else if (!q.getIsDoubleAnswer()) {
                photo.setReplyMarkup(createOptionsKeyboard());
            }
            // 36-45 uchun tugma yo'q!

            tryExecute(photo);

            test.setCurrentStep(current + 1);
            userTestRepository.save(test);

        } catch (Exception e) {
            sendTextMessage(chatId, caption + "\n\n(Javobingizni matn bilan yuboring)");
            test.setCurrentStep(current + 1);
            userTestRepository.save(test);
            e.printStackTrace();
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        UserTest test = userTestRepository.findByUserIdAndIsFinished(chatId, false);
        if (test == null) return;

        Question q = questionRepository.findById((long) (test.getCurrentStep() + 1)).orElse(null);
        if (q == null || q.getIsDoubleAnswer()) return;

        testService.processSingleAnswer(test, q, data);
        userTestRepository.save(test);

        sendTextMessage(chatId, "✅ Javob: **" + data + "**");
        sendNextQuestion(test, chatId);
    }

    private void handleDoubleAnswer(UserTest test, Question q, String text, Long chatId) {
        if ("a".equals(test.getCurrentSubStep())) {
            testService.processDoubleAnswerPart(test, q, text, "a");
            test.setCurrentSubStep("b");
            userTestRepository.save(test);
            sendTextMessage(chatId, "✅ a) qismi: `" + text + "`\n\nb) qismiga javob yuboring:");
        } else {
            testService.processDoubleAnswerPart(test, q, text, "b");
            test.setCurrentSubStep("a");
            userTestRepository.save(test);
            sendTextMessage(chatId, "✅ b) qismi: `" + text + "`\n\nKeyingi savol...");
            sendNextQuestion(test, chatId);
        }
    }

    private void finishTest(UserTest test, Long chatId) {
        test.setIsFinished(true);
        test.setFinishedAt(LocalDateTime.now());
        userTestRepository.save(test);

        sendTextMessage(chatId, "🎉 Test tugadi! Natijani hisoblayapmiz...");

        Map<Long, List<UserAnswer>> grouped = test.getAnswers().stream()
                .collect(Collectors.groupingBy(ua -> ua.getQuestion().getId()));

        List<Question> questions = new ArrayList<>();
        List<Boolean> responses = new ArrayList<>();

        for (List<UserAnswer> list : grouped.values()) {
            Question qq = list.get(0).getQuestion();
            questions.add(qq);

            boolean correct = list.size() == 1 ?
                    Boolean.TRUE.equals(list.get(0).getCorrect()) :
                    list.stream().anyMatch(a -> "main_a".equals(a.getSubStep()) && Boolean.TRUE.equals(a.getCorrect())) &&
                            list.stream().anyMatch(a -> "main_b".equals(a.getSubStep()) && Boolean.TRUE.equals(a.getCorrect()));

            responses.add(correct);
        }

        double theta = RaschCalculator.calculateAbility(questions, responses);
        test.setAbilityScore(theta);

        double score = (theta + 4) / 8 * 75;
        test.setLevel(determineLevel(score));
        userTestRepository.save(test);

        sendTextMessage(chatId, String.format("""
                📊 **TEST NATIJASI**
                
                🎯 Rasch Theta: %.2f
                📈 Taxminiy ball: %.1f / 75
                🏆 Daraja: %s
                
                PDF sertifikat yuborilmoqda...
                """, theta, score, test.getLevel()));

        ByteArrayInputStream pdf = pdfService.generateResultPdf(test);
        SendDocument doc = new SendDocument();
        doc.setChatId(chatId.toString());
        doc.setDocument(new InputFile(pdf, "natija_" + test.getUserId() + ".pdf"));
        doc.setCaption("📜 Sertifikat");
        tryExecute(doc);
    }

    private String determineLevel(double score) {
        if (score >= 65) return "A+";
        if (score >= 55) return "A";
        if (score >= 45) return "B+";
        if (score >= 35) return "B";
        return "Sertifikat berilmaydi";
    }

    private InlineKeyboardMarkup createOptionsKeyboard() {
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (String opt : List.of("A", "B", "C", "D")) {
            row.add(InlineKeyboardButton.builder().text(opt).callbackData(opt).build());
        }
        return InlineKeyboardMarkup.builder().keyboardRow(row).build();
    }

    private InlineKeyboardMarkup createMatchingKeyboard() {
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        for (String opt : List.of("A", "B", "C")) row1.add(InlineKeyboardButton.builder().text(opt).callbackData(opt).build());
        for (String opt : List.of("D", "E", "F")) row2.add(InlineKeyboardButton.builder().text(opt).callbackData(opt).build());
        return InlineKeyboardMarkup.builder().keyboardRow(row1).keyboardRow(row2).build();
    }

    private void sendTextMessage(Long chatId, String text) {
        SendMessage msg = new SendMessage(chatId.toString(), text);
        msg.setParseMode("Markdown");
        tryExecute(msg);
    }

    private void tryExecute(Object method) {
        try {
            if (method instanceof SendMessage sm) execute(sm);
            else if (method instanceof SendPhoto sp) execute(sp);
            else if (method instanceof SendDocument sd) execute(sd);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}