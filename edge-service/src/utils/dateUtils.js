import { format, isValid, parseISO } from 'date-fns';

export const formatDateTime = (timestamp) => {
    if (!timestamp) return '-';
    
    try {
        let date;
        
        // Handle array format from LocalDateTime serialization [year, month, day, hour, minute, second, nano]
        if (Array.isArray(timestamp)) {
            const [year, month, day, hour, minute, second, nano] = timestamp;
            date = new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000));
        } else {
            date = new Date(timestamp);
        }
        
        if (isNaN(date.getTime())) return 'Invalid Date';
        
        return date.toLocaleString('en-US', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    } catch (error) {
        console.error('Date formatting error:', error);
        return 'Invalid Date';
    }
};

export const formatDate = (dateValue, formatStr = 'MMM dd, yyyy') => {
    if (!dateValue) return 'N/A';
    try {
        if (Array.isArray(dateValue) && dateValue.length >= 6) {
            const [year, month, day, hour, minute, second] = dateValue;
            const date = new Date(year, month - 1, day, hour, minute, second);
            return isValid(date) ? format(date, formatStr) : 'N/A';
        }
        const date = typeof dateValue === 'string' ? parseISO(dateValue) : new Date(dateValue);
        return isValid(date) ? format(date, formatStr) : 'N/A';
    } catch (error) {
        console.error('Error formatting date:', dateValue, error);
        return 'N/A';
    }
};

export const getDefaultDateRange = () => {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setMonth(startDate.getMonth() - 1);
    
    // Set start to beginning of the day (00:00:00)
    startDate.setHours(0, 0, 0, 0);
    
    // Set end to end of the day (23:59:59.999)
    endDate.setHours(23, 59, 59, 999);
    
    return { startDate, endDate };
};

export const formatDateForApi = (date) => {
    if (!date) return null;
    return new Date(date).toISOString();
}; 