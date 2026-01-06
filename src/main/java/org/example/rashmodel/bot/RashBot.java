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
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        }
    }

    private void handleMessage(Message message) {
        String text = message.getText();
        Long chatId = message.getChatId();
        User tgUser = message.getFrom();
        String adminIdStr = botConfig.getAdminId();

        boolean isAdmin = chatId.toString().equals(adminIdStr);

        // 1. Admin uchun aktivlashtirish buyrug'i
        if (isAdmin && text.startsWith("/verify_")) {
            processVerification(text);
            return;
        }

        // 2. Start buyrug'i
        if (text.equals("/start")) {
            if (isAdmin) {
                sendTextMessage(chatId, "📌 *Xush kelibsiz, Admin!*\nSizda tizimga to'liq ruxsat bor. Yangi foydalanuvchilar haqida xabarnomalarni shu yerda olasiz.\n\nTestni tekshirish uchun: /test");
                AppUser adminUser = userRepository.findById(chatId).orElse(new AppUser(chatId, tgUser.getFirstName(), tgUser.getUserName(), true));
                adminUser.setHasAccess(true);
                userRepository.save(adminUser);
            } else {
                registerAndNotifyAdmin(tgUser, chatId);
            }
            return;
        }

        // 3. Testni boshlash
        if (text.equals("/test")) {
            AppUser user = userRepository.findById(chatId).orElse(null);
            if (isAdmin || (user != null && Boolean.TRUE.equals(user.getHasAccess()))) {
                startTestProcess(chatId);
            } else {
                sendTextMessage(chatId, "⛔️ *Sizda ruxsat mavjud emas!*\n\nTestda ishtirok etish uchun to'lovni amalga oshiring va adminga murojaat qiling.");
            }
            return;
        }

        // 4. Test davomida ochiq savollarga javob berish
        if (states.containsKey(chatId)) {
            handleTextAnswer(chatId, text);
        }
    }

    private void registerAndNotifyAdmin(User tgUser, Long chatId) {
        AppUser user = userRepository.findById(chatId).orElse(null);

        if (user == null) {
            user = new AppUser(chatId, tgUser.getFirstName(), tgUser.getUserName(), false);
            userRepository.save(user);
        }

        // Agar userda ruxsat bo'lmasa, adminga xabar berish
        if (!Boolean.TRUE.equals(user.getHasAccess())) {
            String adminMsg = String.format(
                    "<b>🆕 Yangi foydalanuvchi!</b>\n\n" +
                            "👤 Ism: %s\n" +
                            "🆔 ID: <code>%d</code>\n" +
                            "🔗 Username: @%s\n\n" +
                            "Tasdiqlash: /verify_%d",
                    tgUser.getFirstName(),
                    chatId,
                    (tgUser.getUserName() != null ? tgUser.getUserName() : "yo'q"),
                    chatId
            );
            sendTextMessage(Long.parseLong(botConfig.getAdminId()), adminMsg);
            sendTextMessage(chatId, "👋 *Xush kelibsiz!*\n\nTestga kirish uchun ruxsat berilmagan. Iltimos, adminga murojaat qiling va to'lovni tasdiqlang.");
        } else {
            sendTextMessage(chatId, "Sizda ruxsat bor. Testni boshlash uchun /test ni bosing.");
        }
    }

    private void processVerification(String text) {
        try {
            Long targetId = Long.parseLong(text.replace("/verify_", ""));
            userRepository.findById(targetId).ifPresentOrElse(user -> {
                user.setHasAccess(true);
                userRepository.save(user);
                sendTextMessage(targetId, "✅ *To'lovingiz tasdiqlandi!* \nEndi /test komandasi orqali testni boshlashingiz mumkin.");
                sendTextMessage(Long.parseLong(botConfig.getAdminId()), "✅ Foydalanuvchi (ID: " + targetId + ") aktivlashtirildi.");
            }, () -> sendTextMessage(Long.parseLong(botConfig.getAdminId()), "❌ Foydalanuvchi topilmadi."));
        } catch (Exception e) {
            sendTextMessage(Long.parseLong(botConfig.getAdminId()), "❌ ID xato formatda.");
        }
    }

    private void startTestProcess(Long chatId) {
        UserTest test = testService.startNewTest(chatId, "MATEMATIKA");
        userTestRepository.save(test);
        TestState state = new TestState(test);
        states.put(chatId, state);
        sendQuestion(chatId);
    }

    private void sendQuestion(Long chatId) {
        TestState state = states.get(chatId);
        Question question = questionRepository.findById((long) state.getCurrentStep() + 1).orElse(null);

        if (question == null) {
            finishTest(chatId);
            return;
        }

        state.setCurrentQuestion(question);
        String caption = "Savol №" + question.getId();
        if (Boolean.TRUE.equals(question.getIsDoubleAnswer())) {
            caption += (state.getPendingSubQuestion() == null ? " (a-qismi)" : " (b-qismi)");
        }

        try {
            InputStream is = new ClassPathResource(question.getInternalPath()).getInputStream();
            SendPhoto photo = new SendPhoto(chatId.toString(), new InputFile(is, question.getId() + ".png"));
            photo.setCaption(caption);

            if (question.getId() <= 32) {
                photo.setReplyMarkup(createOptionsKeyboard());
            } else if (question.getId() <= 35) {
                photo.setReplyMarkup(createMatchingKeyboard());
            }
            execute(photo);
        } catch (Exception e) {
            sendTextMessage(chatId, caption + "\n\n[Javobingizni yozing yoki variant tanlang]");
        }
    }

    private void handleCallback(CallbackQuery query) {
        Long chatId = query.getMessage().getChatId();
        TestState state = states.get(chatId);
        if (state == null) return;

        testService.processSingleAnswer(state.getTest(), state.getCurrentQuestion(), query.getData());
        state.next();
        sendQuestion(chatId);

        try { execute(new AnswerCallbackQuery(query.getId())); } catch (Exception ignored) {}
    }

    private void handleTextAnswer(Long chatId, String text) {
        TestState state = states.get(chatId);
        Question q = state.getCurrentQuestion();

        if (Boolean.TRUE.equals(q.getIsDoubleAnswer())) {
            if (state.getPendingSubQuestion() == null) {
                state.setTempAnswerA(text);
                state.setPendingSubQuestion("b");
                sendQuestion(chatId);
            } else {
                testService.processDoubleAnswerPart(state.getTest(), q, state.getTempAnswerA(), "a");
                testService.processDoubleAnswerPart(state.getTest(), q, text, "b");
                state.setPendingSubQuestion(null);
                state.next();
                sendQuestion(chatId);
            }
        } else {
            testService.processSingleAnswer(state.getTest(), q, text);
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

        List<Boolean> results = test.getAnswers().stream().map(UserAnswer::getCorrect).collect(Collectors.toList());
        List<Question> questions = test.getAnswers().stream().map(UserAnswer::getQuestion).collect(Collectors.toList());

        double theta = RaschCalculator.calculateAbility(questions, results);
        test.setAbilityScore(theta);
        test.setCorrectCount((int) results.stream().filter(r -> r).count());
        test.setLevel(raschService.getLevel(theta));

        userTestRepository.save(test);

        sendTextMessage(chatId, "🏁 *Test yakunlandi!* \nNatijangiz hisoblanmoqda va sertifikat generatsiya qilinmoqda...");

        try {
            ByteArrayInputStream bis = pdfService.generateResultPdf(test);
            SendDocument doc = new SendDocument(chatId.toString(), new InputFile(bis, "Sertifikat.pdf"));
            doc.setCaption("Sizning natijangiz.");
            execute(doc);
        } catch (Exception e) {
            sendTextMessage(chatId, "⚠️ Sertifikat yuborishda xatolik yuz berdi.");
        }
    }

    private void sendTextMessage(Long chatId, String text) {
        SendMessage msg = new SendMessage(chatId.toString(), text);
        msg.setParseMode("HTML");
        try { execute(msg); } catch (TelegramApiException e) { e.printStackTrace(); }
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