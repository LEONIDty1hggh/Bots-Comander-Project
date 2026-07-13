package com.botscomander.Util;


// Да, я создал класс для таймера спустя год.
public class Timer {
    private long startTime;
    private long duration;
    private boolean isTimerOn = false;

    public boolean startTimer(long time) {
        long now = System.currentTimeMillis();
        if ((now - this.startTime) > this.duration && this.isTimerOn) {
            this.isTimerOn = false;
            this.duration = 0;
            this.startTime = 0;
            return true;
        }

        if (!this.isTimerOn){
            this.isTimerOn = true;
            this.duration = time;
            this.startTime = now;
        }
        return false;
    }

    public boolean isTimerOn() {
        return this.isTimerOn;
    }
}
