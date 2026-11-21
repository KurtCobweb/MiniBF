package com.example.minibf;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BobbyParserTest {
    @Test public void recognizesCommonGreetingsAndSmallTalk() {
        assertEquals(BobbyParser.MessageIntent.GREETING,
                BobbyParser.classifyMessage("Good morning, Bobby!"));
        assertEquals(BobbyParser.MessageIntent.GREETING,
                BobbyParser.classifyMessage("Hello"));
        assertEquals(BobbyParser.MessageIntent.GREETING,
                BobbyParser.classifyMessage("Hi there Bobby"));
        assertEquals(BobbyParser.MessageIntent.HOW_ARE_YOU,
                BobbyParser.classifyMessage("Hey Bobby, how are you?"));
        assertEquals(BobbyParser.MessageIntent.HOW_ARE_YOU,
                BobbyParser.classifyMessage("What's up?"));
        assertEquals(BobbyParser.MessageIntent.GRATITUDE,
                BobbyParser.classifyMessage("Thank you so much"));
    }

    @Test public void recognizesHelpAndScheduleOverviewQuestions() {
        assertEquals(BobbyParser.MessageIntent.HELP,
                BobbyParser.classifyMessage("What can you do?"));
        assertEquals(BobbyParser.MessageIntent.SCHEDULE_OVERVIEW,
                BobbyParser.classifyMessage("What's on my schedule?"));
        assertEquals(BobbyParser.MessageIntent.SCHEDULE_OVERVIEW,
                BobbyParser.classifyMessage("Show my tasks"));
        assertEquals(BobbyParser.MessageIntent.SCHEDULE_OVERVIEW,
                BobbyParser.classifyMessage("Today's progress"));
        assertEquals(BobbyParser.MessageIntent.SCHEDULE_OVERVIEW,
                BobbyParser.classifyMessage("How am I doing?"));
    }

    @Test public void doesNotTreatOrdinaryStatementsAsScheduleRequests() {
        assertEquals(BobbyParser.MessageIntent.OTHER,
                BobbyParser.classifyMessage("Today was a beautiful day."));
        assertEquals(BobbyParser.MessageIntent.OTHER,
                BobbyParser.classifyMessage("My schedule changes every week."));
        assertEquals(BobbyParser.MessageIntent.OTHER,
                BobbyParser.classifyMessage("I need to understand this topic."));
        assertFalse(BobbyParser.hasScheduleSignal("I was happy today."));
        assertFalse(BobbyParser.looksLikeActivityAnswer("Today was a beautiful day."));
        assertFalse(BobbyParser.looksLikeActivityAnswer("I need to understand this topic."));
    }

    @Test public void recognizesCancellationLanguage() {
        assertEquals(BobbyParser.MessageIntent.CANCEL,
                BobbyParser.classifyMessage("Never mind"));
        assertEquals(BobbyParser.MessageIntent.CANCEL,
                BobbyParser.classifyMessage("No thanks"));
    }

    @Test public void recognizesNaturalLanguageScheduleRequests() {
        assertEquals(BobbyParser.MessageIntent.SCHEDULE_REQUEST,
                BobbyParser.classifyMessage("I need to study tomorrow."));
        assertEquals(BobbyParser.MessageIntent.SCHEDULE_REQUEST,
                BobbyParser.classifyMessage("I have a dentist appointment tomorrow."));
        assertEquals(BobbyParser.MessageIntent.SCHEDULE_REQUEST,
                BobbyParser.classifyMessage("Remind me to call Mum at 7 PM."));
        assertEquals(BobbyParser.MessageIntent.SCHEDULE_REQUEST,
                BobbyParser.classifyMessage("I'll call Mum"));
    }

    @Test public void extractsActivityFromReminderAndSchedulePhrases() {
        BobbyParser.Result reminder = BobbyParser.parse("Remind me to call Mum at 7 PM");
        assertEquals("Call Mum", reminder.activity);
        assertTrue(reminder.ready());

        BobbyParser.Result scheduled = BobbyParser.parse("Add a dentist appointment to my schedule tomorrow at 10 AM");
        assertEquals("Dentist appointment", scheduled.activity);
        assertTrue(scheduled.ready());
    }

    @Test public void stripsGreetingFromTaskDescription() {
        BobbyParser.Result result = BobbyParser.parse(
                "Good morning Bobby, I need to study tomorrow at 7 PM");
        assertEquals("Study", result.activity);
        assertTrue(result.ready());
    }

    @Test public void rejectsQuestionAsAnActivityAnswer() {
        assertFalse(BobbyParser.looksLikeActivityAnswer("What can you do?"));
        assertTrue(BobbyParser.looksLikeActivityAnswer("Call the dentist"));
    }
}
