package com.weeklycommit.manager;

/**
 * One chip in a report's outcome spread (F-U3, R8): the RCDO Outcome title (or
 * {@code "Other"} for commitments linked at/above Outcome level) and how many of the
 * report's current-week commitments roll up to it.
 */
public record OutcomeCountDto(String outcome, long count) {}
