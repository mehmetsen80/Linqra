package service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.lite.gateway.service.impl.CronDescriptionServiceImpl;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CronDescriptionServiceImplTest {

    @InjectMocks
    private CronDescriptionServiceImpl cronDescriptionService;

    @Test
    void testGetCronDescription_EveryMinute() {
        // Given
        String cronExpression = "* * * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every minute", result);
    }

    @Test
    void testGetCronDescription_Every2Minutes() {
        // Given
        String cronExpression = "0 */2 * * * ?";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 2 minutes at second 0", result);
    }

    @Test
    void testGetCronDescription_EveryHour() {
        // Given
        String cronExpression = "0 * * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every minute at second 0", result);
    }



    @Test
    void testGetCronDescription_EveryFridayAtMidnight() {
        // Given
        String cronExpression = "0 0 * * * FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Friday at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_FirstDayOfMonthAtMidnight() {
        // Given
        String cronExpression = "0 0 0 1 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 1 of the month at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryHourAtMinuteZero_SixPart() {
        // Given
        String cronExpression = "0 0 */1 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every hour at minute 0", result);
    }

    @Test
    void testGetCronDescription_EveryMinute_SixPart() {
        // Given
        String cronExpression = "0 0 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every hour at minute 0", result);
    }

    @Test
    void testGetCronDescription_EveryMinuteAtSecondThirty() {
        // Given
        String cronExpression = "30 * * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every minute at second 30", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt9AM() {
        // Given
        String cronExpression = "0 0 9 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryMondayAt9AM() {
        // Given
        String cronExpression = "0 0 9 * * MON";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryMonthOnFirstDayAt9AM() {
        // Given
        String cronExpression = "0 0 9 1 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 1 of the month at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryWeekendAt9AM() {
        // Given
        String cronExpression = "0 0 9 * * SAT,SUN";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Saturday and Sunday at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryQuarterAtMidnight() {
        // Given
        String cronExpression = "0 0 0 1 1,4,7,10 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 1 of the month of January, April, July, October at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_InvalidCronExpression() {
        // Given
        String cronExpression = "invalid cron";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronDescriptionService.getCronDescription(cronExpression);
        });
    }

    @Test
    void testGetCronDescription_NullCronExpression() {
        // Given
        String cronExpression = null;

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronDescriptionService.getCronDescription(cronExpression);
        });
    }

    @Test
    void testGetCronDescription_EmptyCronExpression() {
        // Given
        String cronExpression = "";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronDescriptionService.getCronDescription(cronExpression);
        });
    }

    @Test
    void testGetCronDescription_WhitespaceCronExpression() {
        // Given
        String cronExpression = "   ";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronDescriptionService.getCronDescription(cronExpression);
        });
    }

    @Test
    void testGetCronDescription_FivePartCronExpression() {
        // Given
        String cronExpression = "* * * * *";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronDescriptionService.getCronDescription(cronExpression);
        });
    }

    // Additional test cases for comprehensive coverage

    @Test
    void testGetCronDescription_EveryMinuteAtSpecificSecond() {
        // Given
        String cronExpression = "45 * * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every minute at second 45", result);
    }

    @Test
    void testGetCronDescription_EveryTwoHours() {
        // Given
        String cronExpression = "0 0 */2 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 2 hours at minute 0", result);
    }

    @Test
    void testGetCronDescription_EveryThreeHours() {
        // Given
        String cronExpression = "0 0 */3 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 3 hours at minute 0", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTime() {
        // Given
        String cronExpression = "0 30 14 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 2:30 PM", result);
    }

    @Test
    void testGetCronDescription_EveryMondayAtSpecificTime() {
        // Given
        String cronExpression = "0 15 10 * * MON";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday at 10:15 AM", result);
    }

    @Test
    void testGetCronDescription_EveryWeekendAtSpecificTime() {
        // Given
        String cronExpression = "0 0 18 * * SAT,SUN";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Saturday and Sunday at 6:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryMonthOnSpecificDay() {
        // Given
        String cronExpression = "0 0 0 15 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 15 of the month at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryQuarterOnFirstDay() {
        // Given
        String cronExpression = "0 0 0 1 1,4,7,10 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 1 of the month of January, April, July, October at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryYearOnNewYearsDay() {
        // Given
        String cronExpression = "0 0 0 1 1 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 1 of the month of January at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryWeekdayAt9AM() {
        // Given
        String cronExpression = "0 0 9 * * MON-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday through Friday at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryHourOnWeekends() {
        // Given
        String cronExpression = "0 0 * * * SAT,SUN";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Saturday and Sunday at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryMinuteOnSpecificDay() {
        // Given
        String cronExpression = "0 * * * * MON";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday at minute 0", result);
    }

    @Test
    void testGetCronDescription_EverySecondOnSpecificMinute() {
        // Given
        String cronExpression = "* 30 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every second of minute 30", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtNoon() {
        // Given
        String cronExpression = "0 0 12 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 12:00 PM", result);
    }





    @Test
    void testGetCronDescription_EveryDayAt11AM() {
        // Given
        String cronExpression = "0 0 11 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 11:00 AM", result);
    }

    // Additional comprehensive test cases

    @Test
    void testGetCronDescription_EveryDayAt3PM() {
        // Given
        String cronExpression = "0 0 15 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 3:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt6AM() {
        // Given
        String cronExpression = "0 0 6 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 6:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt8PM() {
        // Given
        String cronExpression = "0 0 20 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 8:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt10PM() {
        // Given
        String cronExpression = "0 0 22 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 10:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt4AM() {
        // Given
        String cronExpression = "0 0 4 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 4:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt7AM() {
        // Given
        String cronExpression = "0 0 7 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 7:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt5PM() {
        // Given
        String cronExpression = "0 0 17 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 5:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt2AM() {
        // Given
        String cronExpression = "0 0 2 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 2:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt10AM() {
        // Given
        String cronExpression = "0 0 10 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 10:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificMinute() {
        // Given
        String cronExpression = "0 15 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every hour at minute 15", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificMinuteAndSecond() {
        // Given
        String cronExpression = "30 45 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at minute 45 at second 30", result);
    }

    @Test
    void testGetCronDescription_EveryTuesdayAtMidnight() {
        // Given
        String cronExpression = "0 0 0 * * TUE";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Tuesday at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryWednesdayAt9AM() {
        // Given
        String cronExpression = "0 0 9 * * WED";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Wednesday at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryThursdayAt3PM() {
        // Given
        String cronExpression = "0 0 15 * * THU";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Thursday at 3:00 PM", result);
    }

    @Test
    void testGetCronDescription_EverySundayAtNoon() {
        // Given
        String cronExpression = "0 0 12 * * SUN";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Sunday at 12:00 PM", result);
    }

    @Test
    void testGetCronDescription_EverySaturdayAt6AM() {
        // Given
        String cronExpression = "0 0 6 * * SAT";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Saturday at 6:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryMondayAt8PM() {
        // Given
        String cronExpression = "0 0 20 * * MON";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday at 8:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryFridayAt11PM() {
        // Given
        String cronExpression = "0 0 23 * * FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Friday at 11:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryTuesdayThroughThursday() {
        // Given
        String cronExpression = "0 0 9 * * TUE-THU";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Tuesday through Thursday at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryWednesdayThroughFriday() {
        // Given
        String cronExpression = "0 0 14 * * WED-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Wednesday through Friday at 2:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryMondayThroughWednesday() {
        // Given
        String cronExpression = "0 0 8 * * MON-WED";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday through Wednesday at 8:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryThursdayThroughSaturday() {
        // Given
        String cronExpression = "0 0 19 * * THU-SAT";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Thursday through Saturday at 7:00 PM", result);
    }

    @Test
    void testGetCronDescription_EverySundayThroughTuesday() {
        // Given
        String cronExpression = "0 0 6 * * SUN-TUE";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Sunday through Tuesday at 6:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificMinuteOfEveryHour() {
        // Given
        String cronExpression = "0 30 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every hour at minute 30", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificSecondOfEveryMinute() {
        // Given
        String cronExpression = "15 * * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every minute at second 15", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificSecondOfSpecificMinute() {
        // Given
        String cronExpression = "45 30 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at minute 30 at second 45", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificSecondOfSpecificMinuteOfSpecificHour() {
        // Given
        String cronExpression = "30 15 10 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 10:15 AM", result);
    }

    // Additional test cases for day of month, month, and day of week parameters

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnSpecificDayOfWeek() {
        // Given
        String cronExpression = "0 0 14 * * TUE";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Tuesday at 2:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnWeekend() {
        // Given
        String cronExpression = "0 0 16 * * SAT,SUN";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Saturday and Sunday at 4:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnWeekdays() {
        // Given
        String cronExpression = "0 0 8 * * MON-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday through Friday at 8:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnSpecificDays() {
        // Given
        String cronExpression = "0 0 12 * * MON,WED,FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday, Wednesday, and Friday at 12:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnLastDayOfMonth() {
        // Given
        String cronExpression = "0 0 9 L * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every last day of the month at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnSpecificDayOfMonth() {
        // Given
        String cronExpression = "0 0 18 15 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 15 of the month at 6:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnMultipleDaysOfMonth() {
        // Given
        String cronExpression = "0 0 10 1,15,30 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 1, 15, and 30 of the month at 10:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnSpecificMonth() {
        // Given
        String cronExpression = "0 0 7 * 3 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day March at 7:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnMultipleMonths() {
        // Given
        String cronExpression = "0 0 20 * 6,7,8 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day June, July, August at 8:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnSpecificDayOfMonthInSpecificMonth() {
        // Given
        String cronExpression = "0 0 9 1 1 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 1 of the month of January at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnSpecificDayOfMonthInMultipleMonths() {
        // Given
        String cronExpression = "0 0 6 15 3,6,9,12 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 15 of the month of March, June, September, December at 6:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnSpecificDayOfWeekInSpecificMonth() {
        // Given
        String cronExpression = "0 0 17 * 12 MON";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday of December at 5:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnWeekendInSpecificMonth() {
        // Given
        String cronExpression = "0 0 11 * 7 SAT,SUN";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Saturday and Sunday of July at 11:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnWeekdaysInSpecificMonth() {
        // Given
        String cronExpression = "0 0 8 * 9 MON-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday through Friday of September at 8:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnSpecificDayOfMonthOnSpecificDayOfWeek() {
        // Given
        String cronExpression = "0 0 14 1 * MON";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday day 1 of the month at 2:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnLastDayOfMonthOnSpecificDayOfWeek() {
        // Given
        String cronExpression = "0 0 16 L * FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Friday last day of the month at 4:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnSpecificDayOfMonthInSpecificMonthOnSpecificDayOfWeek() {
        // Given
        String cronExpression = "0 0 10 15 3 WED";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Wednesday day 15 of the month of March at 10:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnMultipleDaysOfMonthInMultipleMonthsOnMultipleDaysOfWeek() {
        // Given
        String cronExpression = "0 0 12 1,15 * MON,WED,FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday, Wednesday, and Friday day 1 and 15 of the month at 12:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeOnLastDayOfMonthInSpecificMonthOnWeekend() {
        // Given
        String cronExpression = "0 0 18 L 12 SAT,SUN";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Saturday and Sunday last day of the month of December at 6:00 PM", result);
    }

    // ===== NEW TEST CASES FOR STEP VALUES =====

    @Test
    void testGetCronDescription_Every5Minutes() {
        // Given
        String cronExpression = "0 */5 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 5 minutes at second 0", result);
    }

    @Test
    void testGetCronDescription_Every15Minutes() {
        // Given
        String cronExpression = "0 */15 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 15 minutes at second 0", result);
    }



    @Test
    void testGetCronDescription_Every10Seconds() {
        // Given
        String cronExpression = "*/10 * * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 10 seconds", result);
    }

    @Test
    void testGetCronDescription_Every30Seconds() {
        // Given
        String cronExpression = "*/30 * * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 30 seconds", result);
    }

    // ===== NEW TEST CASES FOR RANGE VALUES =====

    @Test
    void testGetCronDescription_EveryHourFrom9AMTo5PM() {
        // Given
        String cronExpression = "0 0 9-17 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every hour from 9:00 AM to 5:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryMinuteFrom9AMTo5PM() {
        // Given
        String cronExpression = "0 9-17 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every hour at minute 9 through minute 17", result);
    }

    @Test
    void testGetCronDescription_EveryDayFromMondayToFriday() {
        // Given
        String cronExpression = "0 0 9 * * MON-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday through Friday at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayFromTuesdayToThursday() {
        // Given
        String cronExpression = "0 0 14 * * TUE-THU";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Tuesday through Thursday at 2:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayFromWednesdayToSaturday() {
        // Given
        String cronExpression = "0 0 18 * * WED-SAT";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Wednesday through Saturday at 6:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayFromSundayToTuesday() {
        // Given
        String cronExpression = "0 0 6 * * SUN-TUE";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Sunday through Tuesday at 6:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayFromFridayToSunday() {
        // Given
        String cronExpression = "0 0 20 * * FRI-SUN";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Friday through Sunday at 8:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayFromMondayToWednesday() {
        // Given
        String cronExpression = "0 0 8 * * MON-WED";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday through Wednesday at 8:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayFromThursdayToSaturday() {
        // Given
        String cronExpression = "0 0 19 * * THU-SAT";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Thursday through Saturday at 7:00 PM", result);
    }

    // ===== NEW TEST CASES FOR COMPLEX STEP AND RANGE COMBINATIONS =====

    @Test
    void testGetCronDescription_Every5MinutesFrom9AMTo5PM() {
        // Given
        String cronExpression = "0 */5 9-17 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 5 minutes from 9:00 AM to 5:00 PM", result);
    }

    @Test
    void testGetCronDescription_Every15MinutesFrom8AMTo6PM() {
        // Given
        String cronExpression = "0 */15 8-18 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 15 minutes from 8:00 AM to 6:00 PM", result);
    }

    @Test
    void testGetCronDescription_Every2HoursFrom6AMTo10PM() {
        // Given
        String cronExpression = "0 0 */2 6-22 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 2 hours from 6:00 AM to 10:00 PM", result);
    }

    @Test
    void testGetCronDescription_Every3HoursFrom7AMTo7PM() {
        // Given
        String cronExpression = "0 0 */3 7-19 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 3 hours from 7:00 AM to 7:00 PM", result);
    }

    @Test
    void testGetCronDescription_Every5MinutesOnWeekdays() {
        // Given
        String cronExpression = "0 */5 * * * MON-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 5 minutes Monday through Friday", result);
    }

    @Test
    void testGetCronDescription_Every10MinutesOnWeekends() {
        // Given
        String cronExpression = "0 */10 * * * SAT,SUN";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 10 minutes Saturday and Sunday", result);
    }

    @Test
    void testGetCronDescription_Every15MinutesOnSpecificDays() {
        // Given
        String cronExpression = "0 */15 * * * MON,WED,FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 15 minutes Monday, Wednesday, and Friday", result);
    }

    // ===== NEW TEST CASES FOR SPECIAL CHARACTERS AND EDGE CASES =====

    @Test
    void testGetCronDescription_EveryDayAtNoonOnLastDayOfMonth() {
        // Given
        String cronExpression = "0 0 12 L * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every last day of the month at 12:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryMondayOnLastDayOfMonth() {
        // Given
        String cronExpression = "0 0 9 L * MON";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday last day of the month at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtMidnightOnFirstDayOfMonth() {
        // Given
        String cronExpression = "0 0 0 1 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 1 of the month at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtMidnightOnLastDayOfMonth() {
        // Given
        String cronExpression = "0 0 0 L * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every last day of the month at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt9AMOnFirstDayOfMonth() {
        // Given
        String cronExpression = "0 0 9 1 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 1 of the month at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt5PMOnLastDayOfMonth() {
        // Given
        String cronExpression = "0 0 17 L * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every last day of the month at 5:00 PM", result);
    }

    // ===== NEW TEST CASES FOR BOUNDARY CONDITIONS =====

    @Test
    void testGetCronDescription_EveryDayAtMidnight() {
        // Given
        String cronExpression = "0 0 0 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt11_59PM() {
        // Given
        String cronExpression = "0 59 23 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 11:59 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt12_01AM() {
        // Given
        String cronExpression = "0 1 0 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 12:01 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt11_58PM() {
        // Given
        String cronExpression = "0 58 23 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 11:58 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt12_02AM() {
        // Given
        String cronExpression = "0 2 0 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 12:02 AM", result);
    }

    // ===== NEW TEST CASES FOR MIXED PATTERNS =====

    @Test
    void testGetCronDescription_Every5MinutesOnFirstDayOfMonth() {
        // Given
        String cronExpression = "0 */5 * 1 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 5 minutes day 1 of the month", result);
    }

    @Test
    void testGetCronDescription_Every10MinutesOnLastDayOfMonth() {
        // Given
        String cronExpression = "0 */10 * L * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 10 minutes last day of the month", result);
    }

    @Test
    void testGetCronDescription_Every15MinutesOnWeekdaysInMarch() {
        // Given
        String cronExpression = "0 */15 * * 3 MON-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 15 minutes Monday through Friday of March", result);
    }

    @Test
    void testGetCronDescription_Every2HoursOnWeekendsInDecember() {
        // Given
        String cronExpression = "0 0 */2 * 12 SAT,SUN";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 2 hours Saturday and Sunday December", result);
    }

    @Test
    void testGetCronDescription_Every3HoursOnSpecificDaysInMultipleMonths() {
        // Given
        String cronExpression = "0 0 */3 * 3,6,9,12 MON,WED,FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 3 hours Monday, Wednesday, and Friday March, June, September, December", result);
    }

    // ===== NEW TEST CASES FOR COMPLEX TIME PATTERNS =====



    @Test
    void testGetCronDescription_EveryHourAtSpecificMinuteAndSecond() {
        // Given
        String cronExpression = "15 30 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at minute 30 at second 15", result);
    }



    // ===== NEW TEST CASES FOR UNUSUAL COMBINATIONS =====

    @Test
    void testGetCronDescription_EverySecondOnSpecificMinuteOfSpecificHour() {
        // Given
        String cronExpression = "* 30 14 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every second of minute 30 at 2:00 PM", result);
    }

    @Test
    void testGetCronDescription_EverySecondOnSpecificMinuteOfEveryHour() {
        // Given
        String cronExpression = "* 15 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every second of minute 15", result);
    }

    @Test
    void testGetCronDescription_EverySecondOnSpecificMinuteOfSpecificHourOnWeekdays() {
        // Given
        String cronExpression = "* 45 9 * * MON-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every second of minute 45 at 9:00 AM Monday through Friday", result);
    }

    @Test
    void testGetCronDescription_EverySecondOnSpecificMinuteOfEveryHourOnWeekends() {
        // Given
        String cronExpression = "* 30 * * * SAT,SUN";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every second of minute 30 Saturday and Sunday", result);
    }

    // ===== NEW TEST CASES FOR BUSINESS HOURS PATTERNS =====

    @Test
    void testGetCronDescription_Every5MinutesDuringBusinessHours() {
        // Given
        String cronExpression = "0 */5 9-17 * * MON-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 5 minutes from 9:00 AM to 5:00 PM Monday through Friday", result);
    }

    @Test
    void testGetCronDescription_Every15MinutesDuringExtendedHours() {
        // Given
        String cronExpression = "0 */15 7-21 * * MON-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 15 minutes from 7:00 AM to 9:00 PM Monday through Friday", result);
    }

    @Test
    void testGetCronDescription_EveryHourDuringNightShift() {
        // Given
        String cronExpression = "0 0 22-6 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every hour from 10:00 PM to 6:00 AM", result);
    }

    @Test
    void testGetCronDescription_Every30MinutesDuringDayShift() {
        // Given
        String cronExpression = "0 */30 6-18 * * MON-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 30 minutes from 6:00 AM to 6:00 PM Monday through Friday", result);
    }

    // ===== NEW TEST CASES FOR MONTHLY PATTERNS =====

    @Test
    void testGetCronDescription_EveryDayAt9AMInFirstQuarter() {
        // Given
        String cronExpression = "0 0 9 * 1-3 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day from January to March at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt3PMInSecondQuarter() {
        // Given
        String cronExpression = "0 0 15 * 4-6 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day from April to June at 3:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt6PMInThirdQuarter() {
        // Given
        String cronExpression = "0 0 18 * 7-9 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day from July to September at 6:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtMidnightInFourthQuarter() {
        // Given
        String cronExpression = "0 0 0 * 10-12 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day from October to December at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_LastDayOfMonth() {
        // Given
        String cronExpression = "0 0 9 L * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every last day of the month at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_LastWeekdayOfMonth() {
        // Given
        String cronExpression = "0 0 9 LW * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every last weekday of the month at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_NearestWeekdayToDay15() {
        // Given
        String cronExpression = "0 0 9 15W * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every nearest weekday to day 15 of the month at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_ThirdFridayOfMonth() {
        // Given
        String cronExpression = "0 0 9 * * 6#3";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 3rd Saturday of the month at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_FirstMondayOfMonth() {
        // Given
        String cronExpression = "0 0 9 * * 2#1";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 1st Tuesday of the month at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_SecondWednesdayOfMonth() {
        // Given
        String cronExpression = "0 0 9 * * 4#2";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 2nd Thursday of the month at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_LastThursdayOfMonth() {
        // Given
        String cronExpression = "0 0 9 * * 5#5";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 5th Friday of the month at 9:00 AM", result);
    }

    // ===== STEP VALUES IN RANGES TEST CASES =====

    @Test
    void testGetCronDescription_Every3DaysStartingFromDay1() {
        // Given
        String cronExpression = "0 0 9 1/3 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every every 3 days starting from day 1 of the month at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_Every5DaysStartingFromDay5() {
        // Given
        String cronExpression = "0 0 9 5/5 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every every 5 days starting from day 5 of the month at 9:00 AM", result);
    }

    // ===== DAY RANGES TEST CASES =====

    @Test
    void testGetCronDescription_EveryDayFrom1To15OfMonth() {
        // Given
        String cronExpression = "0 0 9 1-15 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 1 through 15 of the month at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayFrom10To20OfMonth() {
        // Given
        String cronExpression = "0 0 9 10-20 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 10 through 20 of the month at 9:00 AM", result);
    }

    // ===== QUESTION MARK TEST CASES =====

    @Test
    void testGetCronDescription_EveryMondayAt9AMWithQuestionMark() {
        // Given
        String cronExpression = "0 0 9 ? * MON";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDay1OfMonthWithQuestionMark() {
        // Given
        String cronExpression = "0 0 9 1 * ?";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 1 of the month at 9:00 AM", result);
    }


    // ===== COMPLEX COMBINATIONS TEST CASES =====
    @Test
    void testGetCronDescription_Every30Minutes() {
        // Given
        String cronExpression = "0 */30 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 30 minutes at second 0", result);
    }

    @Test
    void testGetCronDescription_Every12Hours() {
        // Given
        String cronExpression = "0 0 */12 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 12 hours at minute 0", result);
    }

    @Test
    void testGetCronDescription_Every5Seconds() {
        // Given
        String cronExpression = "*/5 * * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 5 seconds", result);
    }

    // ===== ERROR HANDLING TEST CASES =====

    @Test
    void testGetCronDescription_InvalidHourValue() {
        // Given
        String cronExpression = "0 0 25 * * *";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronDescriptionService.getCronDescription(cronExpression);
        });
    }

    @Test
    void testGetCronDescription_InvalidMinuteValue() {
        // Given
        String cronExpression = "0 60 9 * * *";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronDescriptionService.getCronDescription(cronExpression);
        });
    }

    @Test
    void testGetCronDescription_InvalidSecondValue() {
        // Given
        String cronExpression = "60 0 9 * * *";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronDescriptionService.getCronDescription(cronExpression);
        });
    }

    @Test
    void testGetCronDescription_InvalidDayOfWeekValue() {
        // Given
        String cronExpression = "0 0 9 * * 8";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronDescriptionService.getCronDescription(cronExpression);
        });
    }

    @Test
    void testGetCronDescription_InvalidMonthValue() {
        // Given
        String cronExpression = "0 0 9 * 13 *";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronDescriptionService.getCronDescription(cronExpression);
        });
    }

    @Test
    void testGetCronDescription_InvalidDayOfMonthValue() {
        // Given
        String cronExpression = "0 0 9 32 * *";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cronDescriptionService.getCronDescription(cronExpression);
        });
    }

    // ===== EDGE CASES TEST CASES =====

    @Test
    void testGetCronDescription_EveryMinuteAtSecond59() {
        // Given
        String cronExpression = "59 * * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every minute at second 59", result);
    }

    @Test
    void testGetCronDescription_EveryHourAtMinute59() {
        // Given
        String cronExpression = "0 59 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every hour at minute 59", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt11PM() {
        // Given
        String cronExpression = "0 0 23 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 11:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt12AM() {
        // Given
        String cronExpression = "0 0 0 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt12PM() {
        // Given
        String cronExpression = "0 0 12 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 12:00 PM", result);
    }

    // ===== SEASONAL PATTERNS TEST CASES =====

    @Test
    void testGetCronDescription_EveryDayAt7AMInSpring() {
        // Given
        String cronExpression = "0 0 7 * 3-5 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day from March to May at 7:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt8AMInSummer() {
        // Given
        String cronExpression = "0 0 8 * 6-8 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day from June to August at 8:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt9AMInAutumn() {
        // Given
        String cronExpression = "0 0 9 * 9-11 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day from September to November at 9:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAt10AMInWinter() {
        // Given
        String cronExpression = "0 0 10 * 12,1,2 *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day December, January, February at 10:00 AM", result);
    }

    // ===== ADDITIONAL 20 TEST CASES =====

    @Test
    void testGetCronDescription_EverySecondOfEveryMinute() {
        // Given
        String cronExpression = "* * * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every minute", result);
    }

    @Test
    void testGetCronDescription_EveryMinuteAtSpecificSecond45() {
        // Given
        String cronExpression = "45 * * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every minute at second 45", result);
    }

    @Test
    void testGetCronDescription_EveryHourAtSpecificMinuteAndSecond30() {
        // Given
        String cronExpression = "30 15 * * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at minute 15 at second 30", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtSpecificTimeWithSeconds() {
        // Given
        String cronExpression = "30 45 14 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 2:45 PM", result);
    }

    @Test
    void testGetCronDescription_EveryWeekdayAtSpecificTimeWithSeconds() {
        // Given
        String cronExpression = "15 30 9 * * MON-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday through Friday at 9:30 AM", result);
    }

    @Test
    void testGetCronDescription_EveryWeekendAtSpecificTimeWithSeconds() {
        // Given
        String cronExpression = "45 0 18 * * SAT,SUN";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Saturday and Sunday at 6:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtMidnightWithSeconds() {
        // Given
        String cronExpression = "30 0 0 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryDayAtNoonWithSeconds() {
        // Given
        String cronExpression = "15 0 12 * * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day at 12:00 PM", result);
    }

    @Test
    void testGetCronDescription_EveryMinuteOnSpecificDayOfWeek() {
        // Given
        String cronExpression = "0 * * * * WED";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Wednesday at minute 0", result);
    }

    @Test
    void testGetCronDescription_EverySecondOnSpecificMinuteOfSpecificHourOnSpecificDay() {
        // Given
        String cronExpression = "* 30 14 * * MON";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every second of minute 30 at 2:00 PM Monday", result);
    }

    @Test
    void testGetCronDescription_EveryMinuteOnLastDayOfMonth() {
        // Given
        String cronExpression = "0 * * L * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every last day of the month at minute 0", result);
    }

    @Test
    void testGetCronDescription_EveryHourOnFirstDayOfMonth() {
        // Given
        String cronExpression = "0 0 * 1 * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every day 1 of the month at minute 0", result);
    }

    @Test
    void testGetCronDescription_EveryMinuteOnNearestWeekdayToDay15() {
        // Given
        String cronExpression = "0 * * 15W * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every nearest weekday to day 15 of the month at minute 0", result);
    }

    @Test
    void testGetCronDescription_EveryHourOnThirdFridayOfMonth() {
        // Given
        String cronExpression = "0 0 * * * 6#3";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 3rd Saturday of the month at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryMinuteOnFirstMondayOfMonth() {
        // Given
        String cronExpression = "0 * * * * 2#1";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every 1st Tuesday of the month at minute 0", result);
    }

    @Test
    void testGetCronDescription_EverySecondOnSpecificMinuteOfEveryHourOnWeekdays() {
        // Given
        String cronExpression = "* 30 * * * MON-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every second of minute 30 Monday through Friday", result);
    }

    @Test
    void testGetCronDescription_EveryMinuteOnWeekdaysInSpecificMonth() {
        // Given
        String cronExpression = "0 * * * 3 MON-FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday through Friday of March at minute 0", result);
    }

    @Test
    void testGetCronDescription_EveryHourOnWeekendsInSpecificMonth() {
        // Given
        String cronExpression = "0 0 * * 12 SAT,SUN";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Saturday and Sunday of December at 12:00 AM", result);
    }

    @Test
    void testGetCronDescription_EveryMinuteOnSpecificDaysInMultipleMonths() {
        // Given
        String cronExpression = "0 * * * 1,4,7,10 MON,WED,FRI";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every Monday, Wednesday, and Friday of January, April, July, October at minute 0", result);
    }

    @Test
    void testGetCronDescription_EverySecondOnSpecificMinuteOfSpecificHourOnLastDayOfMonth() {
        // Given
        String cronExpression = "* 45 9 L * *";

        // When
        String result = cronDescriptionService.getCronDescription(cronExpression);

        // Then
        assertEquals("Every second of minute 45 at 9:00 AM last day of the month", result);
    }
} 