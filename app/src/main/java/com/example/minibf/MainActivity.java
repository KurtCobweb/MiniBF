package com.example.minibf;

import android.Manifest;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

/** Main editor for multiple independent study and water reminder slots. */
public class MainActivity extends AppCompatActivity {
    private final List<ReminderStore.Slot> slots=new ArrayList<>();
    private LinearLayout studyRows,waterRows;
    private Switch masterSwitch, floatingSwitch;
    private LinearLayout waterEntryRows;
    private TextView waterTotalText, waterStatusText, waterEmptyText;
    private WaterMeterView waterMeter;
    private static final int REQUEST_NOTIFICATIONS=501;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        findViewById(R.id.openBobbyChatButton).setOnClickListener(v ->
                startActivity(new Intent(this, BobbyChatActivity.class)));
        studyRows=findViewById(R.id.studySlotRows); waterRows=findViewById(R.id.waterSlotRows);
        masterSwitch=findViewById(R.id.remindersEnabledSwitch);
        floatingSwitch=findViewById(R.id.floatingCharactersSwitch);
        floatingSwitch.setChecked(ReminderStore.floatingEnabled(this));
        floatingSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked && !Settings.canDrawOverlays(this)) {
                floatingSwitch.setChecked(false);
                getSharedPreferences("pixel_companion_settings", MODE_PRIVATE)
                        .edit().putBoolean("floating_permission_pending", true).apply();
                new AlertDialog.Builder(this)
                        .setTitle("Allow floating characters")
                        .setMessage("Pixel Companion needs the Android permission 'Display over other apps' to let its character appear at the right edge of your screen when a reminder alarm fires. The character will disappear automatically, and you can dismiss or move it.")
                        .setPositiveButton("Open permission settings", (dialog, which) -> {
                            Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(permission);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                ReminderStore.setFloatingEnabled(this, checked);
                if (!checked) stopService(new Intent(this, FloatingReminderService.class));
                Toast.makeText(this, checked ? "Floating reminders enabled" : "Floating reminders disabled", Toast.LENGTH_SHORT).show();
            }
        });
        slots.addAll(ReminderStore.load(this));
        masterSwitch.setChecked(ReminderStore.masterEnabled(this));
        waterEntryRows=findViewById(R.id.waterEntryRows);
        waterTotalText=findViewById(R.id.waterTotalText);
        waterStatusText=findViewById(R.id.waterStatusText);
        waterEmptyText=findViewById(R.id.waterEmptyText);
        waterMeter=findViewById(R.id.waterMeter);
        findViewById(R.id.addWaterEntryButton).setOnClickListener(v->showWaterEntryDialog(null));
        renderWater();
        findViewById(R.id.addStudySlotButton).setOnClickListener(v->addSlot(ReminderStore.STUDY));
        findViewById(R.id.addWaterSlotButton).setOnClickListener(v->addSlot(ReminderStore.WATER));
        findViewById(R.id.saveSettingsButton).setOnClickListener(v->saveAndSchedule(true));
        masterSwitch.setOnCheckedChangeListener((button,checked)->{
            ReminderStore.setMasterEnabled(this,checked); saveAndSchedule(false);
        });
        renderSlots(); requestNotificationPermission();
        createNotificationChannel();
        requestExactAlarmAccessIfNeeded();
        ReminderScheduler.scheduleAll(this);
    }

    private void renderSlots() {
        studyRows.removeAllViews(); waterRows.removeAllViews();
        for(ReminderStore.Slot slot:slots) {
            View row=LayoutInflater.from(this).inflate(R.layout.item_reminder_slot,
                    slot.type.equals(ReminderStore.STUDY)?studyRows:waterRows,false);
            TextView time=row.findViewById(R.id.slotTimeText), days=row.findViewById(R.id.slotRepeatDaysText);
            Switch enabled=row.findViewById(R.id.slotEnabledSwitch);
            time.setText(slot.timeText()); days.setText("Repeats: "+ReminderStore.daysText(slot.daysMask));
            enabled.setChecked(slot.enabled);
            row.findViewById(R.id.editSlotTimeButton).setOnClickListener(v->new TimePickerDialog(this,(picker,h,m)->{
                slot.hour=h; slot.minute=m; renderSlots();
            },slot.hour,slot.minute,true).show());
            days.setOnClickListener(v->chooseDays(slot));
            enabled.setOnCheckedChangeListener((b,checked)->slot.enabled=checked);
            row.findViewById(R.id.deleteSlotButton).setOnClickListener(v->{
                ReminderScheduler.cancel(this,slot); slots.remove(slot); renderSlots();
            });
            (slot.type.equals(ReminderStore.STUDY)?studyRows:waterRows).addView(row);
        }
    }

    // ------------------------------------------------------------------ water intake

    private static final int[] ZONE_COLOR_RES={R.color.water_red,R.color.water_yellow,R.color.water_green,R.color.water_blue};

    /** Rebuilds the meter, total and entry list from the saved log (re-read each time). */
    private void renderWater() {
        java.util.List<WaterStore.Entry> entries=WaterStore.today(this);
        int total=WaterStore.totalMl(entries);
        int zone=WaterLevels.zoneFor(total);
        waterMeter.setTotalMl(total);
        waterTotalText.setText(WaterLevels.litres(total));
        waterTotalText.setTextColor(ContextCompat.getColor(this,ZONE_COLOR_RES[zone]));
        waterStatusText.setText(WaterLevels.zoneLabel(zone));
        waterEmptyText.setVisibility(entries.isEmpty()?View.VISIBLE:View.GONE);
        waterEntryRows.removeAllViews();
        for(WaterStore.Entry e:entries) {
            View row=LayoutInflater.from(this).inflate(R.layout.item_water_entry,waterEntryRows,false);
            ((TextView)row.findViewById(R.id.waterEntryText)).setText(e.timeText()+"  "+e.describe());
            View minus=row.findViewById(R.id.waterGulpMinus);
            View plus=row.findViewById(R.id.waterGulpPlus);
            // Gulp entries are cumulative counters; mL entries remain timestamped log records.
            minus.setVisibility(e.isGulps()?View.VISIBLE:View.GONE);
            plus.setVisibility(e.isGulps()?View.VISIBLE:View.GONE);
            minus.setOnClickListener(v->{
                if (!e.isGulps()) return;
                if (e.amount <= 1) WaterStore.delete(this,e.id);
                else WaterStore.update(this,e.id,WaterStore.UNIT_GULPS,e.amount-1);
                renderWater();
            });
            plus.setOnClickListener(v->{
                if (e.isGulps()) WaterStore.update(this,e.id,WaterStore.UNIT_GULPS,e.amount+1);
                renderWater();
            });
            row.findViewById(R.id.waterEntryDelete).setOnClickListener(v->{
                WaterStore.delete(this,e.id); renderWater();
            });
            waterEntryRows.addView(row);
        }
    }

    /** Adds a new timestamped drink record in gulps or mL. Existing records are adjusted with +/- controls. */
    private void showWaterEntryDialog(final WaterStore.Entry existing) {
        View content=LayoutInflater.from(this).inflate(R.layout.dialog_water_entry,null);
        final RadioButton gulps=content.findViewById(R.id.waterUnitGulps);
        final RadioButton ml=content.findViewById(R.id.waterUnitMl);
        final EditText amount=content.findViewById(R.id.waterAmountInput);
        final TextView hint=content.findViewById(R.id.waterAmountHint);
        boolean startGulps=existing==null||existing.isGulps();
        gulps.setChecked(startGulps); ml.setChecked(!startGulps);
        amount.setText(existing!=null?String.valueOf(existing.amount):(startGulps?"1":"250"));
        amount.setSelection(amount.getText().length());
        final Runnable refreshHint=()->{
            int n=0; try{ n=Integer.parseInt(amount.getText().toString().trim()); }catch(NumberFormatException ignored){}
            hint.setText(gulps.isChecked()
                    ? "1 gulp = "+WaterLevels.GULP_VOLUME_ML+" mL  ->  "+WaterLevels.toMl(true,n)+" mL"
                    : "= "+WaterLevels.litres(n));
        };
        gulps.setOnCheckedChangeListener((b,c)->{
            if(existing==null&&c&&"250".equals(amount.getText().toString())) amount.setText("1");
            if(existing==null&&!c&&"1".equals(amount.getText().toString())) amount.setText("250");
            refreshHint.run();
        });
        amount.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(android.text.Editable s){ refreshHint.run(); }
        });
        refreshHint.run();
        new AlertDialog.Builder(this)
                .setTitle("Add water log entry")
                .setView(content)
                .setPositiveButton("Save",(d,w)->{
                    int n; try{ n=Integer.parseInt(amount.getText().toString().trim()); }catch(NumberFormatException ex){ n=0; }
                    if(n<=0){ Toast.makeText(this,"Enter an amount above 0",Toast.LENGTH_SHORT).show(); return; }
                    String unit=gulps.isChecked()?WaterStore.UNIT_GULPS:WaterStore.UNIT_ML;
                    WaterStore.add(this,unit,n);
                    renderWater();
                })
                .setNegativeButton("Cancel",null)
                .show();
    }

    private void addSlot(String type) {
        java.util.Calendar c=java.util.Calendar.getInstance();
        int id=ReminderStore.newId(this,slots);
        ReminderStore.Slot s=new ReminderStore.Slot(id,type,c.get(java.util.Calendar.HOUR_OF_DAY),
                ((c.get(java.util.Calendar.MINUTE)+4)/5)*5%60,true,127);
        slots.add(s);
        new TimePickerDialog(this,(picker,h,m)->{s.hour=h;s.minute=m;renderSlots();},s.hour,s.minute,true).show();
        renderSlots();
    }

    private void chooseDays(ReminderStore.Slot slot) {
        String[] labels={"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};
        boolean[] checked=new boolean[7];
        for(int i=0;i<7;i++) checked[i]=(slot.daysMask&(1<<i))!=0;
        new AlertDialog.Builder(this).setTitle("Repeat on")
                .setMultiChoiceItems(labels,checked,(dialog,which,isChecked)->checked[which]=isChecked)
                .setPositiveButton("Apply",(d,w)->{
                    int mask=0; for(int i=0;i<7;i++)if(checked[i])mask|=1<<i;
                    if(mask==0){Toast.makeText(this,"Select at least one day",Toast.LENGTH_SHORT).show();return;}
                    slot.daysMask=mask; renderSlots();
                }).setNegativeButton("Cancel",null).show();
    }

    private void saveAndSchedule(boolean showMessage) {
        ReminderStore.save(this,slots);
        ReminderScheduler.scheduleAll(this);
        if(showMessage) Toast.makeText(this,"Reminder schedule saved",Toast.LENGTH_SHORT).show();
    }

    /** Android 12+ may require the user to grant exact-alarm special access. */
    private void requestExactAlarmAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                new AlertDialog.Builder(this)
                        .setTitle("Allow scheduled reminders")
                        .setMessage("To deliver reminders close to the times you choose, allow Pixel Companion to schedule exact alarms. If you skip this, Android may deliver reminders later than scheduled.")
                        .setPositiveButton("Open settings", (dialog, which) -> {
                            try {
                                Intent settings = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:" + getPackageName()));
                                startActivity(settings);
                            } catch (Exception ignored) { }
                        })
                        .setNegativeButton("Not now", null)
                        .show();
            }
        }
    }

    private void requestNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.POST_NOTIFICATIONS},REQUEST_NOTIFICATIONS);
    }
    private void createNotificationChannel() {
        if(Build.VERSION.SDK_INT>=26) {
            android.app.NotificationChannel channel=new android.app.NotificationChannel(
                    ReminderReceiver.CHANNEL_ID,"Pixel Companion reminders",android.app.NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Study and water reminder notifications");
            ((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }
    @Override protected void onResume(){
        super.onResume();
        if (floatingSwitch != null) {
            android.content.SharedPreferences prefs = getSharedPreferences("pixel_companion_settings", MODE_PRIVATE);
            boolean permissionPending = prefs.getBoolean("floating_permission_pending", false);
            if (permissionPending && Settings.canDrawOverlays(this)) {
                prefs.edit().putBoolean("floating_permission_pending", false).apply();
                ReminderStore.setFloatingEnabled(this, true);
                floatingSwitch.setChecked(true);
            } else if (permissionPending) {
                prefs.edit().putBoolean("floating_permission_pending", false).apply();
            }
            if (ReminderStore.floatingEnabled(this) && !Settings.canDrawOverlays(this)) {
                ReminderStore.setFloatingEnabled(this, false);
                floatingSwitch.setChecked(false);
            }
        }
        if(studyRows!=null) ReminderScheduler.scheduleAll(this);
        if(waterEntryRows!=null) renderWater();   // the floating character may have logged gulps
    }
    @Override protected void onPause(){ super.onPause(); saveAndSchedule(false); }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==702)ReminderScheduler.scheduleAll(this);}
}
