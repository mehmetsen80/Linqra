package org.lite.gateway.service.impl;

import org.lite.gateway.service.CronDescriptionService;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CronDescriptionServiceImpl implements CronDescriptionService {
    private static final String[] MONTH_NAMES = {
        "", "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    };
    private static final String[] DAY_NAMES = {
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    };
    private static final String[] DAY_ABBREVIATIONS = {
        "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"
    };

        public String getCronDescription(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            throw new IllegalArgumentException("Cron expression cannot be null or empty");
        }
                
        String[] parts = cronExpression.trim().split("\\s+");
        if (parts.length != 6) {
            throw new IllegalArgumentException("Invalid cron expression: must contain 6 fields");
        }

        try {
            String second = parts[0];
            String minute = parts[1];
            String hour = parts[2];
            String dayOfMonth = parts[3];
            String month = parts[4];
            String dayOfWeek = parts[5];

            // Validate cron expression values
            validateCronFields(second, minute, hour, dayOfMonth, month, dayOfWeek);

            StringBuilder description = new StringBuilder("Every ");

            // Handle special case: every minute
            if ("*".equals(second) && "*".equals(minute) && "*".equals(hour) &&
                "*".equals(dayOfMonth) && "*".equals(month) && "*".equals(dayOfWeek)) {
                return "Every minute";
            }

            // Handle time part (second, minute, hour)
            String timeDesc = describeTime(second, minute, hour);
            boolean hasTime = !timeDesc.isEmpty();

            // Handle day of month
            String dayOfMonthDesc = describeDayOfMonth(dayOfMonth);

            // Handle month
            String monthDesc = describeMonth(month);

            // Handle day of week
            String dayOfWeekDesc = describeDayOfWeek(dayOfWeek);

            // Build final description
            if (dayOfWeekDesc.isEmpty() && dayOfMonthDesc.isEmpty() && monthDesc.isEmpty()) {
                // Handle every second of specific minute at specific hour first
                if (second.equals("*") && !minute.equals("*") && !hour.equals("*")) {
                    description.append("second of minute ").append(minute).append(" at ").append(formatHour(Integer.parseInt(hour)));
                } else if (hasTime) {
                    if (second.equals("0") && minute.equals("0") && hour.equals("*")) {
                        description.append("hour at minute 0");
                    } else if (second.equals("0") && minute.equals("0") && hour.startsWith("*/")) {
                        String stepValue = hour.replace("*/", "");
                        if ("1".equals(stepValue)) {
                            description.append("hour at minute 0");
                        } else {
                            description.append(stepValue).append(" hours at minute 0");
                        }
                    } else if (second.equals("0") && minute.equals("0") && hour.startsWith("*/") && dayOfMonth.contains("-")) {
                        // Special case: step value with hour range (e.g., */3 with 7-19)
                        // Check if dayOfMonth range represents hours (0-23 range)
                        String[] range = dayOfMonth.split("-");
                        if (range.length == 2) {
                            try {
                                int startHour = Integer.parseInt(range[0].trim());
                                int endHour = Integer.parseInt(range[1].trim());
                                // If both values are in valid hour range (0-23), treat as hour constraint
                                if (startHour >= 0 && startHour <= 23 && endHour >= 0 && endHour <= 23) {
                                    String stepValue = hour.replace("*/", "");
                                    String startTime = formatHour(startHour);
                                    String endTime = formatHour(endHour);
                                    description.append(stepValue).append(" hours from ").append(startTime).append(" to ").append(endTime);
                                } else {
                                    // Fall back to regular step value handling
                                    String stepValue = hour.replace("*/", "");
                                    if ("1".equals(stepValue)) {
                                        description.append("hour at minute 0");
                                    } else {
                                        description.append(stepValue).append(" hours at minute 0");
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // Fall back to regular step value handling
                                String stepValue = hour.replace("*/", "");
                                if ("1".equals(stepValue)) {
                                    description.append("hour at minute 0");
                                } else {
                                    description.append(stepValue).append(" hours at minute 0");
                                }
                            }
                        } else {
                            // Fall back to regular step value handling
                            String stepValue = hour.replace("*/", "");
                            if ("1".equals(stepValue)) {
                                description.append("hour at minute 0");
                            } else {
                                description.append(stepValue).append(" hours at minute 0");
                            }
                        }
                    } else if (second.equals("*") && minute.equals("*")) {
                        description.append("minute");
                    } else if (second.startsWith("*/") && minute.equals("*") && hour.equals("*")) {
                        // Special case: step value in seconds (e.g., */5 for every 5 seconds)
                        String stepValue = second.replace("*/", "");
                                                description.append(stepValue).append(" seconds");
                    } else if (!second.equals("0") && minute.equals("*") && hour.equals("*")) {
                        description.append("minute at second ").append(second);
                    } else if (second.equals("0") && minute.startsWith("*/") && hour.equals("*")) {
                        // Special case: step value in minutes (e.g., */30 for every 30 minutes)
                        String stepValue = minute.replace("*/", "");
                        description.append(stepValue).append(" minutes at second 0");
                    } else if (second.equals("0") && minute.startsWith("*/") && hour.contains("-")) {
                        // Special case: step value in minutes with hour range (e.g., */5 with 9-17)
                        String stepValue = minute.replace("*/", "");
                        String[] range = hour.split("-");
                        if (range.length == 2) {
                            try {
                                int startHour = Integer.parseInt(range[0].trim());
                                int endHour = Integer.parseInt(range[1].trim());
                                String startTime = formatHour(startHour);
                                String endTime = formatHour(endHour);
                                description.append(stepValue).append(" minutes from ").append(startTime).append(" to ").append(endTime);
                            } catch (NumberFormatException e) {
                                // Fall back to regular handling
                                description.append(stepValue).append(" minutes at second 0");
                            }
                        } else {
                            description.append(stepValue).append(" minutes at second 0");
                        }
                    } else if (second.equals("0") && minute.contains("-") && hour.equals("*")) {
                        // Special case: minute range (e.g., 9-17 for minute 9 through minute 17)
                        String[] range = minute.split("-");
                        if (range.length == 2) {
                            description.append("hour at minute ").append(range[0]).append(" through minute ").append(range[1]);
                        } else {
                            description.append("hour at minute ").append(minute);
                        }
                    } else if ("0".equals(second) && !minute.equals("*") && hour.equals("*")) {
                        description.append("hour at minute ").append(minute);
                    } else if ("*".equals(second) && !minute.equals("*") && hour.equals("*")) {
                        description.append("second of minute ").append(minute);
                    } else if ("0".equals(second) && "*".equals(minute) && "*".equals(hour)) {
                        description.append("minute at second 0");
                    } else if (hour.contains("-")) {
                        // Special case: hour range (e.g., 22-6 for 10:00 PM to 6:00 AM)
                        description.append("hour ").append(timeDesc);
                    } else {
                        description.append("day at ").append(timeDesc);
                    }
                } else {
                    description.append("day");
                }
            } else {
                // Special case: step value with hour range in dayOfMonth field (e.g., */3 with 7-19)
                if (second.equals("0") && minute.equals("0") && hour.startsWith("*/") && dayOfMonth.contains("-")) {
                    String[] range = dayOfMonth.split("-");
                    if (range.length == 2) {
                        try {
                            int startHour = Integer.parseInt(range[0].trim());
                            int endHour = Integer.parseInt(range[1].trim());
                            // If both values are in valid hour range (0-23), treat as hour constraint
                            if (startHour >= 0 && startHour <= 23 && endHour >= 0 && endHour <= 23) {
                                String stepValue = hour.replace("*/", "");
                                String startTime = formatHour(startHour);
                                String endTime = formatHour(endHour);
                                description.append(stepValue).append(" hours from ").append(startTime).append(" to ").append(endTime);
                                if (!dayOfWeekDesc.isEmpty()) {
                                    description.append(" ").append(dayOfWeekDesc);
                                }
                                if (!monthDesc.isEmpty()) {
                                    description.append(" of ").append(monthDesc);
                                }
                            } else {
                                // Fall back to regular handling
                                description.append(dayOfWeekDesc.isEmpty() ? "" : dayOfWeekDesc);
                                if (!dayOfMonthDesc.isEmpty()) {
                                    description.append(" ").append(dayOfMonthDesc);
                                }
                                if (!monthDesc.isEmpty()) {
                                    description.append(" of ").append(monthDesc);
                                }
                                if (hasTime) {
                                    description.append(" at ").append(timeDesc);
                                }
                            }
                        } catch (NumberFormatException e) {
                            // Fall back to regular handling
                            description.append(dayOfWeekDesc.isEmpty() ? "" : dayOfWeekDesc);
                            if (!dayOfMonthDesc.isEmpty()) {
                                description.append(" ").append(dayOfMonthDesc);
                            }
                            if (!monthDesc.isEmpty()) {
                                description.append(" of ").append(monthDesc);
                            }
                            if (hasTime) {
                                description.append(" at ").append(timeDesc);
                            }
                        }
                    } else {
                        // Fall back to regular handling
                        description.append(dayOfWeekDesc.isEmpty() ? "" : dayOfWeekDesc);
                        if (!dayOfMonthDesc.isEmpty()) {
                            description.append(" ").append(dayOfMonthDesc);
                        }
                        if (!monthDesc.isEmpty()) {
                            description.append(" of ").append(monthDesc);
                        }
                        if (hasTime) {
                            description.append(" at ").append(timeDesc);
                        }
                    }
                } else if (minute.startsWith("*/") && hour.equals("*") && !dayOfWeekDesc.isEmpty()) {
                    // Special case: step value in minutes with day constraint (e.g., */5 with MON-FRI)
                    String stepValue = minute.replace("*/", "");
                    description.append(stepValue).append(" minutes ");
                    description.append(dayOfWeekDesc);
                    if (!dayOfMonthDesc.isEmpty()) {
                        description.append(" ").append(dayOfMonthDesc);
                    }
                    if (!monthDesc.isEmpty()) {
                        description.append(" of ").append(monthDesc);
                    }
                } else if (minute.startsWith("*/") && hour.equals("*") && !dayOfMonthDesc.isEmpty()) {
                    // Special case: step value in minutes with day of month constraint (e.g., */5 with day 1)
                    String stepValue = minute.replace("*/", "");
                    description.append(stepValue).append(" minutes ");
                    description.append(dayOfMonthDesc);
                    if (!monthDesc.isEmpty()) {
                        description.append(" of ").append(monthDesc);
                    }
                } else if (second.equals("0") && minute.equals("0") && hour.startsWith("*/") && !dayOfWeekDesc.isEmpty()) {
                    // Special case: step value in hours with day constraint (e.g., */2 with SAT,SUN)
                    String stepValue = hour.replace("*/", "");
                    description.append(stepValue).append(" hours ");
                    description.append(dayOfWeekDesc);
                    if (!monthDesc.isEmpty()) {
                        description.append(" ").append(monthDesc);
                    }
                } else if (second.equals("*") && !minute.equals("*") && !hour.equals("*") && !dayOfWeekDesc.isEmpty()) {
                    // Special case: every second of specific minute at specific hour with day constraint (e.g., * 45 9 MON-FRI)
                    description.append("second of minute ").append(minute).append(" at ").append(formatHour(Integer.parseInt(hour)));
                    description.append(" ").append(dayOfWeekDesc);
                    if (!monthDesc.isEmpty()) {
                        description.append(" of ").append(monthDesc);
                    }
                } else if (second.equals("*") && !minute.equals("*") && !hour.equals("*") && !dayOfMonthDesc.isEmpty()) {
                    // Special case: every second of specific minute at specific hour with day of month constraint (e.g., * 45 9 L)
                    description.append("second of minute ").append(minute).append(" at ").append(formatHour(Integer.parseInt(hour)));
                    description.append(" ").append(dayOfMonthDesc);
                    if (!monthDesc.isEmpty()) {
                        description.append(" of ").append(monthDesc);
                    }
                } else if (second.equals("*") && !minute.equals("*") && hour.equals("*") && !dayOfWeekDesc.isEmpty()) {
                    // Special case: every second of specific minute of every hour with day constraint (e.g., * 30 * SAT,SUN)
                    description.append("second of minute ").append(minute).append(" ");
                    description.append(dayOfWeekDesc);
                    if (!monthDesc.isEmpty()) {
                        description.append(" of ").append(monthDesc);
                    }
                } else if (minute.startsWith("*/") && hour.contains("-") && !dayOfWeekDesc.isEmpty()) {
                    // Special case: step value with time range (e.g., */15 with 7-21)
                    String stepValue = minute.replace("*/", "");
                    description.append(stepValue).append(" minutes ");
                    description.append(timeDesc);
                    description.append(" ").append(dayOfWeekDesc);
                    if (!dayOfMonthDesc.isEmpty()) {
                        description.append(" ").append(dayOfMonthDesc);
                    }
                    if (!monthDesc.isEmpty()) {
                        description.append(" of ").append(monthDesc);
                    }
                } else if (!dayOfWeekDesc.isEmpty()) {
                    description.append(dayOfWeekDesc);
                    if (!dayOfMonthDesc.isEmpty()) {
                        description.append(" ").append(dayOfMonthDesc);
                    }
                    if (!monthDesc.isEmpty()) {
                        description.append(" of ").append(monthDesc);
                    }
                    if (hasTime) {
                        // For specific days, format time appropriately
                        if (second.equals("0") && minute.equals("0") && hour.equals("*")) {
                            description.append(" at 12:00 AM");
                        } else if (second.equals("0") && minute.equals("*") && hour.equals("*")) {
                            description.append(" at minute 0");
                        } else {
                            description.append(" at ").append(timeDesc);
                        }
                    }
                } else if (!dayOfMonthDesc.isEmpty()) {
                    description.append(dayOfMonthDesc);
                    if (!monthDesc.isEmpty()) {
                        description.append(" of ").append(monthDesc);
                    }
                    if (hasTime) {
                        description.append(" at ").append(timeDesc);
                    }
                } else if (!monthDesc.isEmpty()) {
                    description.append("day ").append(monthDesc);
                    if (hasTime) {
                        description.append(" at ").append(timeDesc);
                    }
                }
            }

            return description.toString().replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cron expression format: " + e.getMessage());
        }
    }

    private String describeTime(String second, String minute, String hour) {
        if ("*".equals(second) && "*".equals(minute) && "*".equals(hour)) {
            return "";
        }

        if ("*".equals(second) && "*".equals(minute)) {
            return "minute";
        }

        if ("0".equals(second) && "0".equals(minute) && "*".equals(hour)) {
            return "minute 0";
        }

        if ("0".equals(second) && "0".equals(minute) && hour.startsWith("*/")) {
            String stepValue = hour.replace("*/", "");
            if ("1".equals(stepValue)) {
                return "hour at minute 0";
            } else {
                return String.format("%s hours at minute 0", stepValue);
            }
        }

        if (!second.equals("*") && "*".equals(minute) && "*".equals(hour)) {
            return String.format("minute %s", second);
        }

        if ("0".equals(second) && !minute.equals("*") && hour.equals("*")) {
            return String.format("hour at minute %s", minute);
        }

        if ("*".equals(second) && !minute.equals("*") && hour.equals("*")) {
            return String.format("hour at minute %s", minute);
        }

        if (!second.equals("0") && minute.equals("*") && hour.equals("*")) {
            return String.format("minute at second %s", second);
        }

        // Handle case where both second and minute are specific values
        if (!second.equals("0") && !minute.equals("*") && hour.equals("*")) {
            return String.format("minute %s at second %s", minute, second);
        }

        // Handle case where second is wildcard but minute and hour are specific
        if ("*".equals(second) && !minute.equals("*") && !hour.equals("*")) {
            return ""; // Let the main logic handle this case
        }





        // Handle step values in ranges (e.g., 1-5/2 for every 2nd value in range 1-5)
        if (hour.contains("/")) {
            String[] parts = hour.split("/");
            if (parts.length == 2 && parts[0].contains("-")) {
                String[] range = parts[0].split("-");
                if (range.length == 2) {
                    return String.format("every %s hours from %s to %s", parts[1], range[0], range[1]);
                }
            }
        }

        // Handle hour ranges (e.g., 7-21 for 7:00 AM to 9:00 PM)
        if (hour.contains("-")) {
            String[] range = hour.split("-");
            if (range.length == 2) {
                int startHour = Integer.parseInt(range[0].trim());
                int endHour = Integer.parseInt(range[1].trim());
                String startTime = formatHour(startHour);
                String endTime = formatHour(endHour);
                return String.format("from %s to %s", startTime, endTime);
            }
        }

        // Special case: when seconds and minutes are 0, format as time
        if ("0".equals(second) && "0".equals(minute) && !hour.equals("*")) {
            int hourNum = Integer.parseInt(hour);
            String amPm = hourNum < 12 ? "AM" : "PM";
            if (hourNum == 0) {
                hourNum = 12;
            } else if (hourNum > 12) {
                hourNum -= 12;
            }
            return String.format("%d:00 %s", hourNum, amPm);
        }

        // Special case: when second is wildcard but minute and hour are specific
        if ("*".equals(second) && !minute.equals("*") && !hour.equals("*")) {
            return ""; // Let the main logic handle this case
        }

        // Format as time (e.g., 9:00 AM)
        int hourNum = hour.equals("*") ? 0 : Integer.parseInt(hour);
        String minuteStr = minute.equals("*") ? "00" : String.format("%02d", Integer.parseInt(minute));
        String amPm = hourNum < 12 ? "AM" : "PM";
        if (hourNum == 0) {
            hourNum = 12;
        } else if (hourNum > 12) {
            hourNum -= 12;
        }
        return String.format("%d:%s %s", hourNum, minuteStr, amPm);
    }

    private String formatHour(int hour) {
        String amPm = hour < 12 ? "AM" : "PM";
        if (hour == 0) {
            return "12:00 AM";
        } else if (hour > 12) {
            hour -= 12;
        }
        return String.format("%d:00 %s", hour, amPm);
    }

    private String describeDayOfMonth(String dayOfMonth) {
        if ("*".equals(dayOfMonth) || "?".equals(dayOfMonth)) {
            return "";
        } else if ("L".equals(dayOfMonth)) {
            return "last day of the month";
        } else if ("LW".equals(dayOfMonth)) {
            return "last weekday of the month";
        } else if (dayOfMonth.contains(",")) {
            String[] days = dayOfMonth.split(",");
            if (days.length == 2) {
                // For 2 days, use "and"
                return String.format("day %s and %s of the month", days[0].trim(), days[1].trim());
            } else {
                // For 3+ days, use commas with "and" before the last
                StringBuilder result = new StringBuilder("day ");
                for (int i = 0; i < days.length; i++) {
                    if (i > 0) {
                        if (i == days.length - 1) {
                            result.append(", and ");
                        } else {
                            result.append(", ");
                        }
                    }
                    result.append(days[i].trim());
                }
                result.append(" of the month");
                return result.toString();
            }
        } else if (dayOfMonth.contains("W")) {
            // Handle weekday nearest day (e.g., 15W for nearest weekday to 15th)
            String day = dayOfMonth.replace("W", "");
            return String.format("nearest weekday to day %s of the month", day);
        } else if (dayOfMonth.contains("/")) {
            // Handle step values in day of month (e.g., 1/2 for every 2nd day starting from 1)
            String[] parts = dayOfMonth.split("/");
            if (parts.length == 2) {
                return String.format("every %s days starting from day %s of the month", parts[1], parts[0]);
            }
        } else if (dayOfMonth.contains("-")) {
            // Handle day ranges (e.g., 1-15)
            String[] range = dayOfMonth.split("-");
            if (range.length == 2) {
                return String.format("day %s through %s of the month", range[0].trim(), range[1].trim());
            }
        } else {
            return String.format("day %s of the month", dayOfMonth);
        }
        return ""; // Fallback for unhandled cases
    }

    private String describeMonth(String month) {
        if ("*".equals(month)) {
            return "";
        }
        if (month.contains(",")) {
            // Handles: 1,4,7,10 → "January, April, July, October"
            String[] months = month.split(",");
            return Arrays.stream(months)
                .map(m -> MONTH_NAMES[Integer.parseInt(m)])
                .collect(Collectors.joining(", "));
        }
        if (month.contains("-")) {
            String[] range = month.split("-");
            if (range.length == 2) {
                String startMonth = MONTH_NAMES[Integer.parseInt(range[0].trim())];
                String endMonth = MONTH_NAMES[Integer.parseInt(range[1].trim())];
                return String.format("from %s to %s", startMonth, endMonth);
            }
        }
        // Handles: 3 → "March"
        return MONTH_NAMES[Integer.parseInt(month)];
    }

    private String describeDayOfWeek(String dayOfWeek) {
        if ("*".equals(dayOfWeek) || "?".equals(dayOfWeek)) {
            return "";
        }
        
        // Handle Nth day of week (e.g., 6#3 for 3rd Friday)
        if (dayOfWeek.contains("#")) {
            String[] parts = dayOfWeek.split("#");
            if (parts.length == 2) {
                String day = formatSingleDayOfWeek(parts[0].trim());
                String nth = parts[1].trim();
                return String.format("%s %s of the month", getOrdinal(nth), day);
            }
        }
        
        // Handle range patterns like MON-FRI
        if (dayOfWeek.contains("-")) {
            String[] range = dayOfWeek.split("-");
            if (range.length == 2) {
                String startDay = formatSingleDayOfWeek(range[0].trim());
                String endDay = formatSingleDayOfWeek(range[1].trim());
                return String.format("%s through %s", startDay, endDay);
            }
        }
        
        if (dayOfWeek.contains(",")) {
            String[] days = dayOfWeek.split(",");
            if (days.length == 2) {
                // For 2 days, use "and"
                String day1 = formatSingleDayOfWeek(days[0].trim());
                String day2 = formatSingleDayOfWeek(days[1].trim());
                return String.format("%s and %s", day1, day2);
            } else {
                // For 3+ days, use commas with "and" before the last
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < days.length; i++) {
                    if (i > 0) {
                        if (i == days.length - 1) {
                            result.append(", and ");
                        } else {
                            result.append(", ");
                        }
                    }
                    result.append(formatSingleDayOfWeek(days[i].trim()));
                }
                return result.toString();
            }
        }
        
        for (int i = 0; i < DAY_ABBREVIATIONS.length; i++) {
            if (DAY_ABBREVIATIONS[i].equalsIgnoreCase(dayOfWeek)) {
                return DAY_NAMES[i];
            }
        }
        return DAY_NAMES[Integer.parseInt(dayOfWeek)];
    }
    
    private String getOrdinal(String number) {
        int n = Integer.parseInt(number);
        if (n >= 11 && n <= 13) {
            return n + "th";
        }
        switch (n % 10) {
            case 1: return n + "st";
            case 2: return n + "nd";
            case 3: return n + "rd";
            default: return n + "th";
        }
    }
    
    private String formatSingleDayOfWeek(String day) {
        for (int i = 0; i < DAY_ABBREVIATIONS.length; i++) {
            if (DAY_ABBREVIATIONS[i].equalsIgnoreCase(day)) {
                return DAY_NAMES[i];
            }
        }
        try {
            return DAY_NAMES[Integer.parseInt(day)];
        } catch (NumberFormatException e) {
            return day; // Return as-is if it's not a number
        }
    }

    private void validateCronFields(String second, String minute, String hour, String dayOfMonth, String month, String dayOfWeek) {
        // Validate second (0-59)
        if (!isValidField(second, 0, 59) && !isSpecialValue(second)) {
            throw new IllegalArgumentException("Invalid second value: " + second);
        }

        // Validate minute (0-59)
        if (!isValidField(minute, 0, 59) && !isSpecialValue(minute)) {
            throw new IllegalArgumentException("Invalid minute value: " + minute);
        }

        // Validate hour (0-23)
        if (!isValidField(hour, 0, 23) && !isSpecialValue(hour)) {
            throw new IllegalArgumentException("Invalid hour value: " + hour);
        }

        // Validate day of month (1-31, L, LW, W, ?)
        if (!isValidDayOfMonth(dayOfMonth)) {
            throw new IllegalArgumentException("Invalid day of month value: " + dayOfMonth);
        }

        // Validate month (1-12)
        if (!isValidMonth(month)) {
            throw new IllegalArgumentException("Invalid month value: " + month);
        }

        // Validate day of week (0-7, SUN-SAT, ?)
        if (!isValidDayOfWeek(dayOfWeek)) {
            throw new IllegalArgumentException("Invalid day of week value: " + dayOfWeek);
        }
    }

    private boolean isValidField(String field, int min, int max) {
        if (field.equals("*") || field.equals("?")) {
            return true;
        }

        // Handle step values (e.g., */5, 1/2)
        if (field.contains("/")) {
            String[] parts = field.split("/");
            if (parts.length != 2) {
                return false;
            }
            String base = parts[0];
            String step = parts[1];
            
            // Validate step value
            try {
                int stepValue = Integer.parseInt(step);
                if (stepValue <= 0) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }

            // If base is a range, validate the range
            if (base.contains("-")) {
                return isValidRange(base, min, max);
            }
            
            // If base is a specific value, validate it
            if (!base.equals("*")) {
                try {
                    int value = Integer.parseInt(base);
                    return value >= min && value <= max;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            
            return true;
        }

        // Handle ranges (e.g., 1-5)
        if (field.contains("-")) {
            return isValidRange(field, min, max);
        }

        // Handle lists (e.g., 1,3,5)
        if (field.contains(",")) {
            String[] values = field.split(",");
            for (String value : values) {
                try {
                    int intValue = Integer.parseInt(value.trim());
                    if (intValue < min || intValue > max) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return true;
        }

        // Handle single value
        try {
            int value = Integer.parseInt(field);
            return value >= min && value <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidRange(String field, int min, int max) {
        String[] range = field.split("-");
        if (range.length != 2) {
            return false;
        }
        try {
            int start = Integer.parseInt(range[0].trim());
            int end = Integer.parseInt(range[1].trim());
            
            // For hours, allow wrapping around midnight (e.g., 22-6 is valid)
            if (max == 23) { // This is the hour field
                return start >= min && start <= max && end >= min && end <= max;
            }
            
            // For other fields, require start <= end
            return start >= min && start <= max && end >= min && end <= max && start <= end;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidDayOfMonth(String dayOfMonth) {
        if (dayOfMonth.equals("*") || dayOfMonth.equals("?") || 
            dayOfMonth.equals("L") || dayOfMonth.equals("LW")) {
            return true;
        }

        // Handle W (weekday nearest day)
        if (dayOfMonth.contains("W")) {
            String day = dayOfMonth.replace("W", "");
            try {
                int dayValue = Integer.parseInt(day);
                return dayValue >= 1 && dayValue <= 31;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return isValidField(dayOfMonth, 1, 31);
    }

    private boolean isValidDayOfWeek(String dayOfWeek) {
        if (dayOfWeek.equals("*") || dayOfWeek.equals("?")) {
            return true;
        }

        // Handle # (nth day of week)
        if (dayOfWeek.contains("#")) {
            String[] parts = dayOfWeek.split("#");
            if (parts.length != 2) {
                return false;
            }
            String day = parts[0];
            String nth = parts[1];
            
            try {
                int nthValue = Integer.parseInt(nth);
                if (nthValue < 1 || nthValue > 5) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }

            // Validate the day part
            return isValidDayOfWeekValue(day);
        }

        return isValidDayOfWeekValue(dayOfWeek);
    }

    private boolean isValidDayOfWeekValue(String dayOfWeek) {
        // Check for day abbreviations
        for (String abbreviation : DAY_ABBREVIATIONS) {
            if (abbreviation.equalsIgnoreCase(dayOfWeek)) {
                return true;
            }
        }

        // Handle ranges with day abbreviations (e.g., MON-FRI)
        if (dayOfWeek.contains("-")) {
            String[] range = dayOfWeek.split("-");
            if (range.length == 2) {
                boolean startValid = false;
                boolean endValid = false;
                
                // Check if start and end are valid day abbreviations
                for (String abbreviation : DAY_ABBREVIATIONS) {
                    if (abbreviation.equalsIgnoreCase(range[0].trim())) {
                        startValid = true;
                    }
                    if (abbreviation.equalsIgnoreCase(range[1].trim())) {
                        endValid = true;
                    }
                }
                
                if (startValid && endValid) {
                    return true;
                }
            }
        }

        // Handle lists with day abbreviations (e.g., MON,WED,FRI)
        if (dayOfWeek.contains(",")) {
            String[] days = dayOfWeek.split(",");
            for (String day : days) {
                boolean dayValid = false;
                for (String abbreviation : DAY_ABBREVIATIONS) {
                    if (abbreviation.equalsIgnoreCase(day.trim())) {
                        dayValid = true;
                        break;
                    }
                }
                if (!dayValid) {
                    // If not a day abbreviation, try as numeric
                    try {
                        int value = Integer.parseInt(day.trim());
                        if (value < 0 || value > 7) {
                            return false;
                        }
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            }
            return true;
        }

        // Handle single numeric value
        try {
            int value = Integer.parseInt(dayOfWeek);
            return value >= 0 && value <= 7;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidMonth(String month) {
        if (month.equals("*") || month.equals("?")) {
            return true;
        }

        // Handle month abbreviations (JAN, FEB, MAR, etc.)
        String[] monthAbbreviations = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", 
                                      "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        
        for (String abbreviation : monthAbbreviations) {
            if (abbreviation.equalsIgnoreCase(month)) {
                return true;
            }
        }

        // Handle ranges, lists, and single values
        return isValidField(month, 1, 12);
    }

    private boolean isSpecialValue(String field) {
        return field.equals("*") || field.equals("?") || field.equals("L") || field.equals("LW");
    }
}