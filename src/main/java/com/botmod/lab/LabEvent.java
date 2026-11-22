package com.botmod.lab;

/** Одно событие журнала лаборатории. */
public record LabEvent(double time, String type, String message) {
}
