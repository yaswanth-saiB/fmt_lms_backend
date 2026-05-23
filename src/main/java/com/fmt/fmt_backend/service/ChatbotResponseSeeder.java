package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.entity.ChatbotResponse;
import com.fmt.fmt_backend.repository.ChatbotResponseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds default chatbot_responses on first startup.
 * Safe to run repeatedly — only inserts if the table is empty.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class ChatbotResponseSeeder implements ApplicationRunner {

    private final ChatbotResponseRepository chatbotResponseRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (chatbotResponseRepository.count() > 0) {
            log.debug("Chatbot responses already seeded — skipping");
            return;
        }

        log.info("Seeding chatbot responses...");

        String welcomeButtons = """
                [
                  {"id":"learn_course","title":"About the Course"},
                  {"id":"book_demo","title":"Book Free Demo"},
                  {"id":"talk_team","title":"Talk to Team"}
                ]
                """;

        String courseButtons = """
                [
                  {"id":"book_demo","title":"Book Free Demo"},
                  {"id":"talk_team","title":"Talk to Team"}
                ]
                """;

        chatbotResponseRepository.saveAll(List.of(

            // -------------------------------------------------------
            // INITIAL state — first contact from a new inbound message
            // -------------------------------------------------------
            ChatbotResponse.builder()
                .state("INITIAL")
                .triggerType("DEFAULT")
                .triggerValue(null)
                .messageType("INTERACTIVE_BUTTONS")
                .responseText("Hi {{name}}! 👋 Welcome to *First Million Trade*.\n\nWe help traders master the stock market through live online classes with an experienced mentor.\n\nHow can we help you today?")
                .buttonsJson(welcomeButtons)
                .nextState("MENU_SHOWN")
                .build(),

            // -------------------------------------------------------
            // INITIAL_LEAD_GEN — lead came through Meta ads form
            // -------------------------------------------------------
            ChatbotResponse.builder()
                .state("INITIAL_LEAD_GEN")
                .triggerType("DEFAULT")
                .triggerValue(null)
                .messageType("INTERACTIVE_BUTTONS")
                .responseText("Hi {{name}}! 👋 Thank you for your interest in *First Million Trade*.\n\nWe received your enquiry and we're excited to connect with you!\n\nOur mentor has helped hundreds of traders build consistent income from the markets. What would you like to know?")
                .buttonsJson(welcomeButtons)
                .nextState("MENU_SHOWN")
                .build(),

            // -------------------------------------------------------
            // Button: learn_course — works from any state (*)
            // -------------------------------------------------------
            ChatbotResponse.builder()
                .state("*")
                .triggerType("BUTTON_ID")
                .triggerValue("learn_course")
                .messageType("INTERACTIVE_BUTTONS")
                .responseText("📚 *About Our Course*\n\n✅ Live online classes via Zoom\n✅ Practical trading strategies (options, futures, intraday)\n✅ Recorded sessions available 24/7\n✅ Small batch size for personal attention\n✅ Lifetime community access\n\n💰 *Course Fee:* ₹15,000 (one-time)\n📅 *Duration:* 3 months\n\nWe offer a *free demo class* so you can experience the teaching style before enrolling. Want to book one?")
                .buttonsJson(courseButtons)
                .nextState("MENU_SHOWN")
                .build(),

            // -------------------------------------------------------
            // Button: book_demo — works from any state (*)
            // -------------------------------------------------------
            ChatbotResponse.builder()
                .state("*")
                .triggerType("BUTTON_ID")
                .triggerValue("book_demo")
                .messageType("TEXT")
                .responseText("🎯 Great choice! A free demo class is the best way to experience our teaching.\n\nOur team will contact you within 2-4 hours to schedule your demo at a convenient time.\n\n📞 You can also reach us directly at +91-XXXXXXXXXX.\n\nWe look forward to meeting you! 🙏")
                .nextState("DEMO_BOOKING")
                .updatesLeadStatus("DEMO_BOOKED")
                .build(),

            // -------------------------------------------------------
            // Button: talk_team — escalates to human from any state
            // -------------------------------------------------------
            ChatbotResponse.builder()
                .state("*")
                .triggerType("BUTTON_ID")
                .triggerValue("talk_team")
                .messageType("TEXT")
                .responseText("Sure! I'm connecting you with our team right away. Someone will be with you shortly. 🙏\n\nYou can also reach us at +91-XXXXXXXXXX during business hours (Mon–Sat, 9 AM – 7 PM).")
                .nextState("ESCALATED")
                .build(),

            // -------------------------------------------------------
            // Keyword: FEE_INQUIRY — any state
            // -------------------------------------------------------
            ChatbotResponse.builder()
                .state("*")
                .triggerType("KEYWORD")
                .triggerValue("FEE_INQUIRY")
                .messageType("INTERACTIVE_BUTTONS")
                .responseText("💰 *Course Fees*\n\n• Complete trading course: *₹15,000* (one-time)\n• Includes 3 months live classes + lifetime recorded access\n• EMI options available on request\n\nWe also offer a *FREE demo class* — no payment needed to try us out!")
                .buttonsJson(courseButtons)
                .nextState("MENU_SHOWN")
                .build(),

            // -------------------------------------------------------
            // Keyword: DEMO_BOOKING — any state
            // -------------------------------------------------------
            ChatbotResponse.builder()
                .state("*")
                .triggerType("KEYWORD")
                .triggerValue("DEMO_BOOKING")
                .messageType("TEXT")
                .responseText("🎯 We'd love to have you in a free demo class!\n\nOur team will contact you within 2-4 hours to schedule it at a time that works for you.\n\nLooking forward to seeing you! 🙏")
                .nextState("DEMO_BOOKING")
                .updatesLeadStatus("DEMO_BOOKED")
                .build(),

            // -------------------------------------------------------
            // Keyword: LOCATION — any state
            // -------------------------------------------------------
            ChatbotResponse.builder()
                .state("*")
                .triggerType("KEYWORD")
                .triggerValue("LOCATION")
                .messageType("TEXT")
                .responseText("📍 *Our classes are 100% online* — no need to travel anywhere!\n\nAll sessions are conducted live on *Zoom*. You can join from your phone, tablet, or laptop from anywhere in India.\n\nWould you like to book a free demo class to try it out?")
                .nextState("MENU_SHOWN")
                .build(),

            // -------------------------------------------------------
            // Keyword: TIMING — any state
            // -------------------------------------------------------
            ChatbotResponse.builder()
                .state("*")
                .triggerType("KEYWORD")
                .triggerValue("TIMING")
                .messageType("TEXT")
                .responseText("🕐 *Class Timings*\n\n📅 *Morning Batch:* 7:00 AM – 9:00 AM (Mon–Fri)\n📅 *Evening Batch:* 7:00 PM – 9:00 PM (Mon–Fri)\n\nAll sessions are recorded and available for replay within 24 hours.\n\nWould you like to book a free demo to pick the batch that suits you?")
                .nextState("MENU_SHOWN")
                .build(),

            // -------------------------------------------------------
            // Keyword: ESCALATED — any state
            // -------------------------------------------------------
            ChatbotResponse.builder()
                .state("*")
                .triggerType("KEYWORD")
                .triggerValue("ESCALATED")
                .messageType("TEXT")
                .responseText("I'm connecting you with our team right away. Someone will be with you shortly! 🙏\n\nBusiness hours: Mon–Sat, 9 AM – 7 PM.")
                .nextState("ESCALATED")
                .build()
        ));

        log.info("Chatbot responses seeded successfully ({} entries)", chatbotResponseRepository.count());
    }
}
