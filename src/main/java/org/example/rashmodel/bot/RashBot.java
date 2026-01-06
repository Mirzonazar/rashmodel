package org.example.rashmodel.bot;

import org.example.rashmodel.config.BotConfig;
import org.example.rashmodel.entity.*;
import org.example.rashmodel.model.TestState;
import org.example.rashmodel.repository.*;
import org.example.rashmodel.service.*;
import org.example.rashmodel.util.RaschCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class RashBot extends TelegramLongPollingBot {

    private final BotConfig botConfig;
    private final TestService testService;
    private final QuestionRepository questionRepository;
    private final UserTestRepository userTestRepository;
    private final AppUserRepository userRepository;
    private final PdfService pdfService;
    private final RaschService raschService;

    private final Map<Long, TestState> states = new ConcurrentHashMap<>();

    @Autowired
    public RashBot(BotConfig botConfig, TestService testService,
                   QuestionRepository questionRepository, UserTestRepository userTestRepository,
                   AppUserRepository userRepository, PdfService pdfService, RaschService raschService) {
        this.botConfig = botConfig;
        this.testService = testService;
        this.questionRepository = questionRepository;
        this.userTestRepository = userTestRepository;
        this.userRepository = userRepository;
        this.pdfService = pdfService;
        this.raschService = raschService;
    }

    @Override
    public String getBotUsername() { return botConfig.getUsername(); }

    @Override
    public String getBotToken() { return botConfig.getToken(); }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            handleMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        }
    }

    private void handleMessage(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();
        if (text == null) return;

        if (text.equals("/start")) {
            registerAndNotifyAdmin(chatId, message.getFrom());
            sendTextMessage(chatId, "Assalomu alaykum!\nMatematika darajasini aniqlash testiga xush kelibsiz!\n\n/test — testni boshlash");
        } else if (text.equals("/test")) {
            startTestProcess(chatId);
        } else if (text.startsWith("/verify_") && String.valueOf(chatId).equals(botConfig.getAdminId())) {
            handleVerifyCommand(chatId, text);
        } else {
            TestState state = states.get(chatId);
            if (state != null && !state.getTest().getIsFinished()) {
                handleTextAnswer(chatId, text, state);
            }
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        TestState state = states.get(chatId);
        if (state == null) return;

        try {
            execute(new AnswerCallbackQuery(callbackQuery.getId()));
        } catch (Exception ignored) {}

        Question q = state.getCurrentQuestion();
        testService.processSingleAnswer(state.getTest(), q, data);
        state.next();
        sendQuestion(chatId);
    }

    private void registerAndNotifyAdmin(Long chatId, User from) {
        AppUser user = userRepository.findById(chatId).orElse(null);
        boolean isNew = (user == null);

        if (isNew) {
            user = new AppUser();
            user.setId(chatId);
            user.setFirstName(from.getFirstName());
            user.setUsername(from.getUserName());
            user.setHasAccess(false);
            userRepository.save(user);
        }

        // Admin xabari — Markdownsiz, eng sodda shaklda
        String status = isNew ? "YANGI" : "qayta";
        String msg = status + " foydalanuvchi:\n" +
                "ID: " + chatId + "\n" +
                "Ism: " + from.getFirstName() + "\n" +
                "Username: " + (from.getUserName() != null ? "@" + from.getUserName() : "yo'q") + "\n" +
                "Tasdiqlash: /verify_" + chatId;

        sendTextMessage(Long.parseLong(botConfig.getAdminId()), msg);

        sendTextMessage(chatId, "Xush kelibsiz! /test buyrug'i bilan testni boshlashingiz mumkin.");
    }

    private void handleVerifyCommand(Long chatId, String text) {
        try {
            Long targetId = Long.parseLong(text.substring(8).trim());
            AppUser target = userRepository.findById(targetId).orElse(null);
            if (target != null) {
                target.setHasAccess(true);
                target.setAccessGrantedAt(LocalDateTime.now());
                target.setAccessExpiresAt(LocalDateTime.now().plusDays(30));
                userRepository.save(target);

                sendTextMessage(chatId, "✅ " + targetId + " uchun kirish faollashtirildi (30 kun).");
                sendTextMessage(targetId, "🎉 Tabriklaymiz! Endi testdan o'tishingiz mumkin.\n/test buyrug'ini yuboring.");
            } else {
                sendTextMessage(chatId, "⚠️ Foydalanuvchi topilmadi.");
            }
        } catch (Exception e) {
            sendTextMessage(chatId, "Xatolik: " + e.getMessage());
        }
    }

    private void startTestProcess(Long chatId) {
        AppUser user = userRepository.findById(chatId).orElse(null);
        boolean isAdmin = String.valueOf(chatId).equals(botConfig.getAdminId());

        if (!isAdmin) {
            if (user == null || !user.isAccessActive()) {
                if (user != null && Boolean.TRUE.equals(user.getHasAccess())) {
                    sendTextMessage(chatId, "⏰ Kirish huquqingiz muddati tugagan.\nQayta to'lov qiling va admin bilan bog'laning.");
                } else {
                    sendTextMessage(chatId, "🔒 Testdan o'tish uchun to'lov talab qilinadi.\nAdmin bilan bog'laning.");
                }
                return;
            }
        }

        // Admin bo'lsa yoki to'lov bor bo'lsa davom etamiz
        UserTest existing = userTestRepository.findLatestOpenTest(chatId, false);
        if (existing != null) {
            sendTextMessage(chatId, "Davom etayotgan test topildi. Uni davom ettiramiz.");
            TestState state = new TestState(existing);
            states.put(chatId, state);
            sendQuestion(chatId);
            return;
        }

        UserTest test = testService.startNewTest(chatId, "MATEMATIKA");
        userTestRepository.save(test);

        TestState state = new TestState(test);
        states.put(chatId, state);

        sendTextMessage(chatId, "Test boshlandi! Jami 45 ta savol.");
        sendQuestion(chatId);
    }

    private void sendQuestion(Long chatId) {
        TestState state = states.get(chatId);
        if (state == null) return;

        if (state.isLast()) {
            finishTest(chatId);
            return;
        }

        int step = state.getCurrentStep();
        Question question = questionRepository.findById((long) (step + 1)).orElse(null);
        if (question == null) {
            sendTextMessage(chatId, "Xatolik: savol topilmadi.");
            return;
        }

        state.setCurrentQuestion(question);

        String caption = "Savol " + (step + 1) + "/45";
        if (question.getIsDoubleAnswer()) {
            String part = state.getPendingSubQuestion() != null ? " (b-qismi)" : " (a-qismi)";
            caption += part;
        }

        SendPhoto photo = new SendPhoto();
        photo.setChatId(chatId.toString());
        photo.setCaption(caption);

        try {
            ClassPathResource resource = new ClassPathResource(question.getInternalPath());
            InputStream is = resource.getInputStream();
            photo.setPhoto(new InputFile(is, question.getId() + ".png"));

            if (!question.getIsDoubleAnswer()) {
                if (question.getIsBlockQuestion()) {
                    photo.setReplyMarkup(createMatchingKeyboard());
                } else if (question.getQuestionGroup().equals("1_32")) {
                    photo.setReplyMarkup(createOptionsKeyboard());
                }
            }

            execute(photo);
        } catch (Exception e) {
            sendTextMessage(chatId, "Rasm yuklanmadi.\nJavobingizni yozing (savol ID: " + question.getId() + ")");
        }
    }

    private void handleTextAnswer(Long chatId, String text, TestState state) {
        Question q = state.getCurrentQuestion();
        UserTest test = state.getTest();

        if (q.getIsDoubleAnswer()) {
            if (state.getPendingSubQuestion() == null) {
                state.setTempAnswerA(text.trim());
                state.setPendingSubQuestion("b");
                sendTextMessage(chatId, "a-qismi qabul qilindi!\nEndi b-qismiga javob yozing:");
                return;
            } else {
                testService.processDoubleAnswerPart(test, q, state.getTempAnswerA(), "a");
                testService.processDoubleAnswerPart(test, q, text.trim(), "b");
                state.setPendingSubQuestion(null);
                state.setTempAnswerA(null);
                state.next();
                sendQuestion(chatId);
            }
        } else {
            testService.processSingleAnswer(test, q, text.trim());
            state.next();
            sendQuestion(chatId);
        }
    }

    private void finishTest(Long chatId) {
        TestState state = states.remove(chatId);
        if (state == null) return;

        UserTest test = state.getTest();
        test.setIsFinished(true);
        test.setFinishedAt(LocalDateTime.now());

        List<UserAnswer> answers = test.getAnswers();
        List<Question> questions = answers.stream().map(UserAnswer::getQuestion).collect(Collectors.toList());
        List<Boolean> results = answers.stream().map(UserAnswer::getCorrect).collect(Collectors.toList());

        double theta = RaschCalculator.calculateAbility(questions, results);
        test.setAbilityScore(theta);
        test.setCorrectCount((int) results.stream().filter(Boolean::booleanValue).count());
        test.setLevel(raschService.getLevel(theta));

        userTestRepository.save(test);

        sendTextMessage(chatId, "🏁 Test yakunlandi!\nNatija hisoblanmoqda...");

        try {
            ByteArrayInputStream bis = pdfService.generateResultPdf(test);
            SendDocument doc = new SendDocument();
            doc.setChatId(chatId.toString());
            doc.setDocument(new InputFile(bis, "Sertifikat.pdf"));
            doc.setCaption("Natijangiz va sertifikatingiz.");
            execute(doc);
        } catch (Exception e) {
            sendTextMessage(chatId, "Sertifikat yuborishda xatolik yuz berdi.");
        }
    }

    private void sendTextMessage(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        // ParseMode qo'yilmaydi — xavfsizroq
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            System.err.println("Xabar yuborishda xato (chatId: " + chatId + "): " + e.getMessage());
        }
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
}