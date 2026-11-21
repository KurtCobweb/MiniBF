package com.example.minibf;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class BobbyChatActivity extends AppCompatActivity {
    private LinearLayout messages;
    private EditText input;
    private ScrollView scroll;
    private BobbyParser.Result pending;
    private String awaiting = "";
    private final java.util.ArrayDeque<BobbyParser.Result> taskQueue = new java.util.ArrayDeque<>();

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        buildUi();
        addMessage("Bobby", "Hi! I'm Bobby. Tell me your plans in ordinary English. For example: “I have to go out at 7:00 AM.”");
    }

    private void buildUi() {
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16),dp(12),dp(16),dp(12)); page.setBackgroundColor(0xFFFFF8E8);
        ImageView bobbyAvatar = new ImageView(this);
        bobbyAvatar.setImageResource(R.drawable.bobby_avatar);
        bobbyAvatar.setContentDescription("Bobby avatar");
        bobbyAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        avatarParams.gravity = Gravity.CENTER_HORIZONTAL;
        page.addView(bobbyAvatar, avatarParams);

        TextView title=new TextView(this); title.setText("BOBBY"); title.setTextSize(23); title.setGravity(Gravity.CENTER); title.setTextColor(0xFF302A20); title.setPadding(0,dp(2),0,dp(8)); page.addView(title);
        Button progressButton=new Button(this); progressButton.setText("View / Edit Schedule & Progress");
        progressButton.setOnClickListener(v->showSchedule()); page.addView(progressButton);
        scroll=new ScrollView(this); messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); scroll.addView(messages);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        input=new EditText(this); input.setSingleLine(false); input.setMinLines(1); input.setMaxLines(4); input.setHint("Message Bobby…");
        input.setTextColor(0xFF302A20);
        input.setHintTextColor(0xFF8A8175);
        row.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        Button send=new Button(this); send.setText("Send"); row.addView(send);
        page.addView(row); send.setOnClickListener(v->sendMessage());
        setContentView(page);
    }

    private void sendMessage() {
        String text=input.getText().toString().trim();
        if(text.isEmpty()) return;
        input.setText("");
        addMessage("You",text);

        BobbyParser.MessageIntent intent=BobbyParser.classifyMessage(text);
        if(handleChatIntent(intent)) return;

        if(!awaiting.isEmpty()) {
            if(awaiting.equals("ACTIVITY")) {
                if(!BobbyParser.looksLikeActivityAnswer(text)) {
                    addMessage("Bobby","I didn't recognise that as a task name. Please tell me the activity, such as ‘study’ or ‘call the dentist’. Your reminder draft is still here.");
                    return;
                }
                String act=text.replaceFirst("(?i)^\\s*(?:good morning|good afternoon|good evening|hello|hiya|howdy|hey|hi)(?:\\s+bobby)?\\s*[,!:]*\\s*", "").trim();
                java.time.LocalDate taskDate=pending.date;
                java.time.LocalTime taskTime=pending.time;
                if(BobbyParser.hasScheduleSignal(text)) {
                    BobbyParser.Result parsed=BobbyParser.parse(text);
                    if(BobbyParser.hasDateSignal(text)) taskDate=parsed.date;
                    if(parsed.time!=null) taskTime=parsed.time;
                    if(parsed.activity!=null && !parsed.activity.trim().isEmpty()) act=parsed.activity;
                    else {
                        pending=new BobbyParser.Result(null,taskDate,taskTime,null);
                        String detail=taskTime==null ? "What activity should I add?" : "I have the time. What activity should I schedule for it?";
                        addMessage("Bobby",detail);
                        awaiting="ACTIVITY";
                        return;
                    }
                }
                if(act.isEmpty()) {
                    addMessage("Bobby","I still need an activity name. What should I add?");
                    return;
                }
                String capped=Character.toUpperCase(act.charAt(0))+act.substring(1);
                pending=new BobbyParser.Result(capped,taskDate,taskTime,null);
                awaiting="";
                if(pending.time==null) {
                    addMessage("Bobby","What time should I schedule "+pending.activity+" for?");
                    awaiting="TIME";
                    return;
                }
                showProposal();
                return;
            }
            if(awaiting.equals("TIME")) {
                BobbyParser.Result follow=BobbyParser.parse(text);
                java.time.LocalDate taskDate=pending.date;
                boolean dateProvided=BobbyParser.hasDateSignal(text);
                if(dateProvided) taskDate=follow.date;
                if(follow.time==null) {
                    if(dateProvided) pending=new BobbyParser.Result(pending.activity,taskDate,pending.time,null);
                    String clarification;
                    if(follow.question != null && follow.question.startsWith("Is that ")) {
                        clarification=follow.question;
                    } else if(dateProvided) {
                        clarification="Okay, " + taskDate.format(DateTimeFormatter.ofPattern("EEE, d MMM",Locale.getDefault()))
                                + ". What time should I schedule " + pending.activity + " for?";
                    } else {
                        clarification="I still need a clear time, such as 7:00 AM or 19:30.";
                    }
                    addMessage("Bobby",clarification);
                    return;
                }
                pending=new BobbyParser.Result(pending.activity,taskDate,follow.time,null);
                awaiting="";
                showProposal();
                return;
            }
            if(awaiting.equals("CONFIRM")) {
                String l=text.toLowerCase(Locale.ROOT).trim().replaceAll("[.!]+$", "");
                if(l.matches("^(yes|yeah|yep|add|add it|confirm|confirm it|okay|ok|sure|go ahead|yes please)$")) {
                    savePending();
                } else if(l.matches("^(no|nope|cancel|don't|do not|no thanks|not now)$")) {
                    pending=null; awaiting="";
                    addMessage("Bobby","No problem. I haven't added anything.");
                    advanceQueue();
                } else {
                    addMessage("Bobby","Please reply ‘Add’ to save this reminder or ‘Cancel’ to discard it.");
                }
                return;
            }
        }

        if(intent!=BobbyParser.MessageIntent.SCHEDULE_REQUEST) {
            addMessage("Bobby","I'm not sure whether you'd like to chat or schedule something. I can add reminders and show your saved tasks. Try ‘Remind me to study tomorrow at 7 PM’ or ask ‘Show my schedule’.");
            return;
        }

        java.util.List<BobbyParser.Result> items=BobbyParser.parseMultiple(text);
        taskQueue.clear();
        taskQueue.addAll(items);
        if(taskQueue.size()>1) addMessage("Bobby","I found "+taskQueue.size()+" tasks — let's go through them one by one.");
        advanceQueue();
    }

    /** Handles conversation and schedule-management intents before schedule parsing. */
    private boolean handleChatIntent(BobbyParser.MessageIntent intent) {
        boolean hasDraft=pending!=null || !taskQueue.isEmpty() || !awaiting.isEmpty();
        switch(intent) {
            case SCHEDULE_OVERVIEW:
                showSchedule();
                return true;
            case GREETING:
                addMessage("Bobby",hasDraft
                        ? "Hello! I'm still here with your reminder draft. We can continue whenever you're ready."
                        : "Hello! What would you like to plan today?");
                return true;
            case HOW_ARE_YOU:
                addMessage("Bobby",hasDraft
                        ? "I'm doing well, thanks for asking! Your reminder draft is still here whenever you'd like to continue."
                        : "Doing great, thanks for asking! What would you like to plan?");
                return true;
            case GRATITUDE:
                addMessage("Bobby",hasDraft
                        ? "You're welcome! We can carry on with your reminder whenever you're ready."
                        : "You're welcome!");
                return true;
            case HELP:
                addMessage("Bobby","I can create reminders from everyday language, show today's progress and your saved schedule, and let you edit or delete tasks. For example: ‘I need to study tomorrow at 7 PM’, ‘Schedule a dentist appointment on Friday at 10 AM’, or ‘Show my tasks’.");
                return true;
            case CANCEL:
                if(hasDraft) {
                    pending=null;
                    awaiting="";
                    taskQueue.clear();
                    addMessage("Bobby","I've cancelled the reminder draft. Your saved schedule hasn't been changed.");
                } else {
                    addMessage("Bobby","There isn't a reminder draft to cancel. Your saved schedule is unchanged.");
                }
                return true;
            default:
                return false;
        }
    }

    /** Pulls the next item off the queue (if any) and either asks for whatever is missing or
     * shows the confirmation proposal. Leaves pending/awaiting cleared if the queue is empty. */
    private void advanceQueue() {
        if(taskQueue.isEmpty()) { pending=null; awaiting=""; return; }
        pending=taskQueue.poll();
        if(pending.activity==null) { addMessage("Bobby",pending.question==null?"I couldn't identify the activity. What should I add?":pending.question); awaiting="ACTIVITY"; return; }
        if(pending.time==null) { addMessage("Bobby",pending.question); awaiting="TIME"; return; }
        showProposal();
    }

    private void showProposal() {
        String date=pending.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy",Locale.getDefault()));
        String time=pending.time.format(DateTimeFormatter.ofPattern("h:mm a",Locale.getDefault()));
        addMessage("Bobby","I understood:\n\n"+pending.activity+"\n"+date+" at "+time+"\n\nShould I add this reminder?");
        awaiting="CONFIRM";
    }

    private void savePending() {
        if(pending==null)return;
        LocalDateTime dt=LocalDateTime.of(pending.date,pending.time);
        long when=dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if(when<=System.currentTimeMillis()) {
            addMessage("Bobby","That time has already passed. Please give me a future time or date.");
            awaiting=""; pending=null; advanceQueue(); return;
        }
        BobbyDatabase db=new BobbyDatabase(this);
        long id=db.addEvent(pending.activity,when);
        BobbyEvent event=new BobbyEvent(id,pending.activity,when,true,BobbyEvent.SCHEDULED);
        db.close();
        BobbyAlarmScheduler.schedule(this,event);
        addMessage("Bobby","Added! I'll remind you about "+pending.activity+" at "+pending.time.format(DateTimeFormatter.ofPattern("h:mm a",Locale.getDefault()))+".");
        pending=null; awaiting="";
        advanceQueue();
    }

    private void showSchedule() {
        if(activeScheduleDialog!=null && activeScheduleDialog.isShowing()) activeScheduleDialog.dismiss();
        BobbyDatabase db=new BobbyDatabase(this);
        java.util.List<BobbyEvent> events=db.getAll();
        java.util.List<BobbyEvent> today=db.getToday();
        long now=System.currentTimeMillis();
        int finished=0, inProgress=0, unfinished=0, changed=0, pendingCount=0;
        for(BobbyEvent e:today) {
            String status=e.effectiveStatus(now);
            if(BobbyEvent.FINISHED.equals(status)) finished++;
            else if(BobbyEvent.IN_PROGRESS.equals(status)) inProgress++;
            else if(BobbyEvent.CHANGE_OF_PLAN.equals(status)) changed++;
            else if(BobbyEvent.UNFINISHED.equals(status)) unfinished++;
            else pendingCount++;
        }
        db.close();

        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16),dp(8),dp(16),dp(8));
        TextView summary=new TextView(this);
        summary.setText("TODAY'S PROGRESS\n\nFinished: "+finished+
                "\nIn Progress: "+inProgress+"\nUnfinished: "+unfinished+
                "\nChange of plan: "+changed+"\nNot yet due: "+pendingCount+
                "\nTotal tasks: "+today.size());
        summary.setTextSize(16); summary.setTextColor(0xFF302A20);
        summary.setPadding(dp(12),dp(12),dp(12),dp(12));
        summary.setBackgroundColor(0xFFE8F1E4); content.addView(summary);

        TextView heading=new TextView(this); heading.setText("SCHEDULE — EDIT OR REMOVE TASKS");
        heading.setTextSize(14); heading.setTextColor(0xFF302A20);
        heading.setPadding(0,dp(14),0,dp(6)); content.addView(heading);

        if(events.isEmpty()) {
            TextView empty=new TextView(this); empty.setText("No Bobby tasks yet. Add one by chatting with Bobby.");
            empty.setTextColor(0xFF302A20); empty.setPadding(0,dp(12),0,dp(12)); content.addView(empty);
        }
        DateTimeFormatter f=DateTimeFormatter.ofPattern("EEE, d MMM yyyy • h:mm a",Locale.getDefault());
        for(BobbyEvent e:events) {
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(8),dp(12),dp(8)); card.setBackgroundColor(0xFFFFF4DC);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,dp(5),0,dp(5));
            TextView label=new TextView(this);
            String when=LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(e.triggerAtMillis),ZoneId.systemDefault()).format(f);
            label.setText(e.activity+"\n"+when+"\nStatus: "+e.effectiveStatus(now).replace('_',' '));
            label.setTextSize(15); label.setTextColor(0xFF302A20); card.addView(label);
            LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
            Button edit=new Button(this); edit.setText("Edit");
            edit.setOnClickListener(v->editEvent(e)); actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1));
            if(!BobbyEvent.FINISHED.equals(e.effectiveStatus(now)) && !BobbyEvent.CHANGE_OF_PLAN.equals(e.effectiveStatus(now))) {
                Button done=new Button(this); done.setText("Mark Finished");
                done.setOnClickListener(v->{ BobbyDatabase update=new BobbyDatabase(this); update.setStatus(e.id,BobbyEvent.FINISHED); update.close(); showSchedule(); });
                actions.addView(done,new LinearLayout.LayoutParams(0,-2,1));
            }
            Button delete=new Button(this); delete.setText("Delete");
            delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Delete task?")
                    .setMessage("Remove '"+e.activity+"' from your schedule?")
                    .setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->{
                        BobbyAlarmScheduler.cancel(this,e.id);
                        BobbyDatabase remove=new BobbyDatabase(this); remove.delete(e.id); remove.close(); showSchedule();
                    }).show());
            actions.addView(delete,new LinearLayout.LayoutParams(0,-2,1));
            card.addView(actions); content.addView(card,cp);
        }
        ScrollView listScroll=new ScrollView(this); listScroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Bobby — Schedule & Progress")
                .setView(listScroll)
                .setPositiveButton("Close",null).create();
        // Keep the dialog reference so task edits can refresh the visible list.
        activeScheduleDialog=dialog;
        dialog.show();
    }

    private AlertDialog activeScheduleDialog;

    private void editEvent(BobbyEvent event) {
        EditText name=new EditText(this); name.setSingleLine(true); name.setText(event.activity);
        name.setHint("Task / action");
        new AlertDialog.Builder(this).setTitle("Edit scheduled task").setView(name)
                .setNegativeButton("Cancel",null).setPositiveButton("Next",(d,w)->{
                    String updatedName=name.getText().toString().trim();
                    if(updatedName.isEmpty()) { Toast.makeText(this,"Task name cannot be empty",Toast.LENGTH_SHORT).show(); return; }
                    java.util.Calendar c=java.util.Calendar.getInstance(); c.setTimeInMillis(event.triggerAtMillis);
                    new android.app.DatePickerDialog(this,(picker,y,m,day)->{
                        c.set(java.util.Calendar.YEAR,y); c.set(java.util.Calendar.MONTH,m); c.set(java.util.Calendar.DAY_OF_MONTH,day);
                        new android.app.TimePickerDialog(this,(tp,h,min)->{
                            c.set(java.util.Calendar.HOUR_OF_DAY,h); c.set(java.util.Calendar.MINUTE,min); c.set(java.util.Calendar.SECOND,0); c.set(java.util.Calendar.MILLISECOND,0);
                            long newTime=c.getTimeInMillis();
                            if(newTime<=System.currentTimeMillis()) { Toast.makeText(this,"Choose a future date and time",Toast.LENGTH_LONG).show(); return; }
                            BobbyAlarmScheduler.cancel(this,event.id);
                            BobbyDatabase update=new BobbyDatabase(this); update.updateEvent(event.id,updatedName,newTime);
                            BobbyEvent changed=update.get(event.id); update.close();
                            if(changed!=null) BobbyAlarmScheduler.schedule(this,changed);
                            showSchedule();
                        },c.get(java.util.Calendar.HOUR_OF_DAY),c.get(java.util.Calendar.MINUTE),false).show();
                    },c.get(java.util.Calendar.YEAR),c.get(java.util.Calendar.MONTH),c.get(java.util.Calendar.DAY_OF_MONTH)).show();
                }).show();
    }

    private void addMessage(String who,String body) {
        TextView v=new TextView(this); v.setText(who+":\n"+body); v.setTextSize(16); v.setTextColor(0xFF302A20); v.setPadding(dp(12),dp(10),dp(12),dp(10)); v.setBackgroundColor(who.equals("You")?0xFFDCEEDB:0xFFFFFFFF);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(5),0,dp(5)); messages.addView(v,lp);
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}
