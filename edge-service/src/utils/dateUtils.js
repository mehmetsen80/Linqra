import { format, isValid, parseISO } from 'date-fns';

/**
 * Formats a timestamp into a human-readable date and time.
 * Handles both string/Date objects and UTC arrays from Java LocalDateTime.
 */
export const formatDateTime = (timestamp, formatStr = 'MM/dd/yyyy HH:mm:ss') => {
    if (!timestamp) return '-';
    
    try {
        let date;
        
        // Handle array format from LocalDateTime serialization [year, month, day, hour, minute, second, nano]
        if (Array.isArray(timestamp)) {
            const [year, month, day, hour, minute, second, nano] = timestamp;
            // LocalDateTime from backend is UTC, so we must use Date.UTC to avoid local offset shift twice
            date = new Date(Date.UTC(year, month - 1, day, hour, minute, second || 0, Math.floor((nano || 0) / 1000000)));
        } else {
            date = new Date(timestamp);
        }
        
        if (isNaN(date.getTime())) return 'Invalid Date';
        
        // Return local time formatted string
        return format(date, formatStr);
    } catch (error) {
        console.error('Date formatting error:', error);
        return 'Invalid Date';
    }
};

/**
 * Formats a date value into a human-readable date string.
 * Handles both string/Date objects and UTC arrays.
 */
export const formatDate = (dateValue, formatStr = 'MMM dd, yyyy HH:mm') => {
    if (!dateValue) return 'N/A';
    try {
        let date;
        if (Array.isArray(dateValue)) {
            // Handle array format [year, month, day, hour, minute, second?, nano?]
            // We use Date.UTC because these arrays represent UTC time from the backend
            const [year, month, day, hour, minute, second, nano] = dateValue;
            date = new Date(Date.UTC(year, month - 1, day, hour || 0, minute || 0, second || 0));
        } else {
            date = typeof dateValue === 'string' ? parseISO(dateValue) : new Date(dateValue);
        }
        return isValid(date) ? format(date, formatStr) : 'N/A';
    } catch (error) {
        console.error('Error formatting date:', dateValue, error);
        return 'N/A';
    }
};

/**
 * Converts a UTC-based cron description to local time.
 * Accounts for current DST state by using a new Date() for offset calculation.
 */
export const convertCronDescriptionToLocal = (cronDescription, cronExpression) => {
    if (!cronDescription || !cronExpression) return cronDescription || 'N/A';

    try {
        // Parse cron expression to get UTC hour and minute
        // Expected Quartz format: [seconds] [minutes] [hours] [dayOfMonth] [month] [dayOfWeek]
        const cronParts = cronExpression.trim().split(/\s+/);
        if (cronParts.length < 6) return cronDescription;

        const utcMinute = parseInt(cronParts[1]);
        const utcHour = parseInt(cronParts[2]);

        if (isNaN(utcMinute) || isNaN(utcHour)) return cronDescription;

        // Convert UTC to local time using CURRENT date to account for actual DST offset
        const date = new Date();
        date.setUTCHours(utcHour, utcMinute, 0, 0);
        
        const localHour = date.getHours();
        const localMinute = date.getMinutes();

        // Prepare 12-hour format patterns for replacement
        const utcHour12 = utcHour === 0 ? 12 : utcHour > 12 ? utcHour - 12 : utcHour;
        const utcAmPm = utcHour >= 12 ? 'PM' : 'AM';
        
        const localHour12 = localHour === 0 ? 12 : localHour > 12 ? localHour - 12 : localHour;
        const localAmPm = localHour >= 12 ? 'PM' : 'AM';

        let localDescription = cronDescription;

        // Patterns to look for (e.g., "7:35 AM" or "7 AM")
        const utcTimePattern = `${utcHour12}:${utcMinute.toString().padStart(2, '0')} ${utcAmPm}`;
        const localTimePattern = `${localHour12}:${localMinute.toString().padStart(2, '0')} ${localAmPm}`;
        
        const utcHourOnlyPattern = `${utcHour12} ${utcAmPm}`;
        const localHourOnlyPattern = `${localHour12} ${localAmPm}`;

        // Replace the UTC time in the description with the local time
        if (localDescription.includes(utcTimePattern)) {
            localDescription = localDescription.replace(new RegExp(utcTimePattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), localTimePattern);
        } else if (localDescription.includes(utcHourOnlyPattern)) {
            // Be careful to only replace when it's exactly the hour (e.g., avoid partial matches in minutes)
            localDescription = localDescription.replace(new RegExp(utcHourOnlyPattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), localHourOnlyPattern);
        }

        return localDescription;
    } catch (error) {
        console.error('Error converting cron description to local:', error);
        return cronDescription;
    }
};

export const convertUTCToLocal = (utcHour, utcMinute) => {
    const date = new Date();
    date.setUTCHours(utcHour, utcMinute, 0, 0);
    return {
        hour: date.getHours(),
        minute: date.getMinutes()
    };
};

export const getDefaultDateRange = () => {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setMonth(startDate.getMonth() - 1);
    
    startDate.setHours(0, 0, 0, 0);
    endDate.setHours(23, 59, 59, 999);
    
    return { startDate, endDate };
};

export const formatDateForApi = (date) => {
    if (!date) return null;
    return new Date(date).toISOString();
};
 