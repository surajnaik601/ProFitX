package com.harsh.profit.fitness;

public class Steps {
    public int totalsteps;

    public Steps() {
        // Default constructor required for calls to DataSnapshot.getValue(Steps.class)
    }

    public Steps(int totalsteps) {
        this.totalsteps = totalsteps;
    }
}
