package com.example.minibf;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small deterministic English parser. It works locally and does not call a network service. */
public final class BobbyParser {
    private BobbyParser() {}

    public static final class Result {
        public final String activity;
        public final LocalDate date;
        public final LocalTime time;
        public final String question;
        Result(String a, LocalDate d, LocalTime t, String q) {
            activity=a; date=d; time=t; question=q;
        }
        public boolean ready() { return activity != null && !activity.isEmpty() && date != null && time != null; }
    }

    private static final Pattern CLOCK = Pattern.compile(
        "(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?\\b");
    private static final Pattern COLON_24 = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");

    /** Unambiguous time mention: explicit am/pm or 24h colon form. Used to decide whether a
     * message contains more than one schedule item, and whether it contains any schedule
     * content at all (as opposed to small talk). */
    private static final Pattern STRICT_TIME = Pattern.compile(
        "(?i)\\b\\d{1,2}(?::\\d{2})?\\s*(?:a\\.?m\\.?|p\\.?m\\.?)\\b|\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");

    private static final Pattern EXPLICIT_SCHEDULE_LANGUAGE = Pattern.compile(
        "(?i)\\b(?:schedule\\s+(?:a|an|the|my|this|it|that|something|anything|"
            + "meeting|appointment|class|exam|task|reminder|call|study|workout)|"
            + "remind(?:\\s+me)?(?:\\s+to|\\s+about)?|reminder\\s+(?:for|to|about)|"
            + "don't forget to|do not forget to|"
            + "add\\s+.+\\s+to\\s+(?:my\\s+)?schedule|"
            + "put\\s+.+\\s+in\\s+(?:my\\s+)?schedule|"
            + "keep\\s+.+\\s+in\\s+(?:my\\s+)?schedule)\\b");
    private static final Pattern DATE_REFERENCE = Pattern.compile(
        "(?i)\\b(?:day after tomorrow|tomorrow|today|"
            + "(?:next|this|on)\\s+(?:sunday|monday|tuesday|wednesday|thursday|friday|saturday)|"
            + "sunday|monday|tuesday|wednesday|thursday|friday|saturday)\\b");
    private static final Pattern DATE_ONLY = Pattern.compile(
        "(?i)^(?:day after tomorrow|tomorrow|today|"
            + "(?:(?:on|next|this)\\s+)?(?:sunday|monday|tuesday|wednesday|thursday|friday|saturday))\\.?$");
    private static final Pattern FIRST_PERSON_PLAN = Pattern.compile(
        "(?i)\\b(?:i need to|i have to|i must|i should|i plan to|i want to|"
            + "i am going to|i'm going to|i've got to|i will|i'll)\\b");
    private static final Pattern NON_TASK_PLAN_START = Pattern.compile(
        "(?i)\\b(?:i need to|i want to|i should|i plan to|i will|i'll)\\s+"
            + "(?:know|understand|ask|learn|find out|figure out|see|hear|think|be|feel|hope)\\b");
    private static final Pattern ACTIVITY_WITH_DATE = Pattern.compile(
        "(?i)\\b(?:meeting|appointment|class|lecture|exam|test|interview|call|workout|"
            + "gym|study|shopping|visit|doctor|dentist|practice|pickup|pick up|"
            + "pay bills|laundry|errand)\\b");
    private static final Pattern PERSONAL_EVENT = Pattern.compile(
        "(?i)\\b(?:i have|i've got|there is|there's)\\s+(?:a|an|the)?\\s*"
            + "(?:meeting|appointment|class|lecture|exam|test|interview|workout|"
            + "doctor|dentist|practice|call|errand)\\b");

    public enum MessageIntent {
        GREETING, HOW_ARE_YOU, GRATITUDE, HELP, SCHEDULE_OVERVIEW, CANCEL,
        SCHEDULE_REQUEST, OTHER
    }

    /** Classifies chat before the schedule parser is allowed to consume the message. */
    public static MessageIntent classifyMessage(String raw) {
        if (raw == null || raw.trim().isEmpty()) return MessageIntent.OTHER;
        String text = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}:'?]+", " ")
                .replaceAll("\\s+", " ").trim();

        if (isScheduleOverviewRequest(text)) return MessageIntent.SCHEDULE_OVERVIEW;
        if (isHelpRequest(text)) return MessageIntent.HELP;
        if (isCancelRequest(text)) return MessageIntent.CANCEL;
        if (isHowAreYou(text)) return MessageIntent.HOW_ARE_YOU;
        if (isGreeting(text)) return MessageIntent.GREETING;
        if (isGratitude(text)) return MessageIntent.GRATITUDE;
        if (hasScheduleSignal(raw)) return MessageIntent.SCHEDULE_REQUEST;
        return MessageIntent.OTHER;
    }

    /** True when the message contains a supported day/date reference. */
    public static boolean hasDateSignal(String raw) {
        return raw != null && DATE_REFERENCE.matcher(raw).find();
    }

    /** Avoid treating casual uses of words such as "today" or "schedule" as task data. */
    public static boolean hasScheduleSignal(String raw) {
        if (raw == null || raw.trim().isEmpty()) return false;
        if (STRICT_TIME.matcher(raw).find()) return true;
        if (EXPLICIT_SCHEDULE_LANGUAGE.matcher(raw).find()) return true;
        if (PERSONAL_EVENT.matcher(raw).find()) return true;
        String lower = raw.toLowerCase(Locale.ROOT).trim();
        if (DATE_ONLY.matcher(lower).matches()) return true;
        if (hasDateSignal(raw)) {
            return (FIRST_PERSON_PLAN.matcher(raw).find()
                        && !NON_TASK_PLAN_START.matcher(raw).find())
                    || ACTIVITY_WITH_DATE.matcher(raw).find();
        }
        return FIRST_PERSON_PLAN.matcher(raw).find()
                && !NON_TASK_PLAN_START.matcher(raw).find();
    }

    /** A short task phrase is a valid answer when Bobby has explicitly asked for an activity. */
    public static boolean looksLikeActivityAnswer(String raw) {
        if (raw == null) return false;
        String text = raw.trim();
        if (text.isEmpty() || text.length() > 160 || text.contains("?")) return false;
        String lower = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (!hasScheduleSignal(text) && (lower.matches("^(?:today|yesterday|tomorrow|"
                + "monday|tuesday|wednesday|thursday|friday|saturday|sunday|"
                + "i am|i'm|it is|it's|this is|that is|there is|there's|"
                + "i feel|i'm feeling|i think|i love|i like|i hate|the weather|weather|"
                + "my schedule|schedule changes)\\b.*")
                || NON_TASK_PLAN_START.matcher(text).find()
                || lower.matches("^(?:hello|hi|hey|hiya|good morning|good afternoon|good evening)"
                        + "(?: there| bobby)?[,! ]+.*"))) return false;
        if (lower.matches("^(what|why|how|who|when|where|can you|could you|do you|are you|"
                + "tell me|show me|explain|write me|help me|i don't know|not sure)\\b.*")) return false;
        String[] words = lower.split("\\s+");
        return words.length <= 16;
    }

    private static boolean isScheduleOverviewRequest(String text) {
        boolean asksToSee = text.matches(".*\\b(show|view|open|display|list|check|see|tell me)\\b.*")
                || text.startsWith("what ") || text.startsWith("what's ") || text.startsWith("whats ");
        boolean mentionsSchedule = text.matches(".*\\b(schedule|tasks?|plans?|progress|summary)\\b.*")
                || text.contains("what do i have today") || text.contains("what have i got today");
        boolean shortOverview = text.matches("^(?:my )?(?:schedule|tasks?|plans?|progress|summary)[.!?]*$")
                || text.matches("^(?:today's|todays) (?:tasks?|progress|summary)[.!?]*$")
                || text.matches("^(?:how am i doing|how did i do|how's my progress|how is my progress)[.!?]*$");
        return shortOverview || (asksToSee && mentionsSchedule);
    }

    private static boolean isHelpRequest(String text) {
        return text.matches("^(?:help|what can you do(?: for me)?|what can i ask you|"
                + "how do i use you|how does this work|give me examples|show me examples|"
                + "how can you help|how do i schedule(?: something)?)\\s*(?:bobby)?[.!?]*$");
    }

    private static boolean isCancelRequest(String text) {
        return text.matches("^(?:cancel|never mind|nevermind|forget it|stop|no|nope|no thanks|not now|"
                + "don't schedule it|do not schedule it|cancel that|cancel this|cancel the reminder|cancel the task)\\s*[.!?]*$");
    }

    private static boolean isHowAreYou(String text) {
        return text.matches("^(?:(?:hi|hello|hey|hiya|howdy|good morning|good afternoon|good evening)"
                + "(?: there)? )?(?:bobby )?(?:how are you|how's it going|how are things|how's everything|"
                + "what's up|how do you do)(?: bobby)?[.!?]*$");
    }

    private static boolean isGreeting(String text) {
        return text.matches("^(?:hi|hello|hey|hiya|howdy|yo|morning|afternoon|evening|"
                + "good morning|good afternoon|good evening)(?: there)?(?: bobby)?[.!?]*$");
    }

    private static boolean isGratitude(String text) {
        return text.matches("^(?:thanks|thank you|thanks a lot|thank you so much|"
                + "much appreciated|i appreciate it|appreciate it)(?: bobby)?[.!?]*$");
    }

    /** Splits a message into one or more schedule items. If the message contains at most one
     * unambiguous time, it is treated as a single item (identical to {@link #parse(String)}).
     * If it contains two or more, the text is split on common separators ("and", "also",
     * "then", commas, semicolons) and each piece is parsed independently. */
    public static java.util.List<Result> parseMultiple(String raw) {
        java.util.List<Result> out = new java.util.ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) { out.add(parse(raw)); return out; }
        Matcher countMatcher = STRICT_TIME.matcher(raw);
        int timeCount = 0;
        while (countMatcher.find()) timeCount++;
        if (timeCount <= 1) { out.add(parse(raw)); return out; }
        String[] parts = raw.split("(?i)\\s*,\\s*|\\s+and\\s+|\\s+also\\s+|\\s+then\\s+|\\s*;\\s*");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            out.add(parse(p));
        }
        if (out.isEmpty()) out.add(parse(raw));
        return out;
    }

    public static Result parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new Result(null, null, null, "What would you like me to schedule?");
        String s = raw.trim();
        String lower = s.toLowerCase(Locale.ROOT);

        LocalDate date = LocalDate.now();
        if (lower.contains("day after tomorrow")) date = date.plusDays(2);
        else if (lower.contains("tomorrow")) date = date.plusDays(1);
        else {
            String[] days = {"sunday","monday","tuesday","wednesday","thursday","friday","saturday"};
            for (int i=0;i<days.length;i++) {
                if (lower.contains(days[i])) {
                    int current = date.getDayOfWeek().getValue() % 7;
                    int delta = (i-current+7)%7;
                    if (delta == 0 || lower.contains("next " + days[i])) delta += 7;
                    date = date.plusDays(delta);
                    break;
                }
            }
        }

        LocalTime time = null;
        Matcher m = CLOCK.matcher(s);
        while (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            String ap = m.group(3);
            if (min > 59 || h > 23) continue;
            if (ap != null) {
                if (h > 12 || h == 0) continue;
                String a = ap.toLowerCase(Locale.ROOT).replace(".", "");
                if (a.equals("pm") && h < 12) h += 12;
                if (a.equals("am") && h == 12) h = 0;
            } else if (h >= 1 && h <= 12) {
                return new Result(extractActivity(s, m.start(), m.end()), date, null,
                        "Is that " + h + (min == 0 ? "" : ":" + String.format(Locale.ROOT,"%02d",min)) + " AM or PM?");
            }
            time = LocalTime.of(h, min);
            break;
        }
        if (time == null) {
            Matcher m24 = COLON_24.matcher(s);
            if (m24.find()) time = LocalTime.of(Integer.parseInt(m24.group(1)), Integer.parseInt(m24.group(2)));
        }

        String activity = extractActivity(s, -1, -1);
        if (activity == null || activity.isEmpty())
            return new Result(null, date, time, "What activity should I add?");
        if (time == null) return new Result(activity, date, null, "What time should I schedule " + activity + " for?");
        return new Result(activity, date, time, null);
    }

    private static String extractActivity(String input, int timeStart, int timeEnd) {
        String s = input;
        if (timeStart >= 0 && timeEnd > timeStart) s = s.substring(0,timeStart) + " " + s.substring(timeEnd);
        s = s.replaceAll("(?i)^\\s*(?:good morning|good afternoon|good evening|hello|hiya|howdy|hey|hi)(?:\\s+bobby)?\\s*[,!]*", " ");
        s = s.replaceAll("(?i)\\b(hey\\s+bobby|hey|bobby|please|don't forget to|do not forget to|keep it in my schedule|add it to my schedule|put it in my schedule|to my schedule|in my schedule|remind me|reminder|schedule|at|on|for|tomorrow|today|day after tomorrow|sunday|monday|tuesday|wednesday|thursday|friday|saturday|next|this|the|a|an|i'll|i will|i have to|i need to|i want to|i plan to|i should|i must|i'm going to|i am going to|i've got to|i have (?:a|an|the)|i've got (?:a|an|the)|there is (?:a|an|the)|there's (?:a|an|the)|have to|need to|going to|will|must|can you|could you|please)\\b", " ");
        s = s.replaceAll("(?i)^\\s*(?:add|put)\\s+", " ");
        s = s.replaceAll("(?i)^\\s*(?:to|about)\\s+", " ");
        s = s.replaceAll("(?i)\\b(\\d{1,2})(?::\\d{2})?\\s*(a\\.?m\\.?|p\\.?m\\.?)\\b", " ");
        s = s.replaceAll("[,!.?]", " ").replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return null;
        if (s.toLowerCase(Locale.ROOT).startsWith("go out")) return "Going out";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
