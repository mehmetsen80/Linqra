import axiosInstance from './axiosInstance';

const BASE_URL = '/api/security/incidents';

const getAllIncidents = async (status) => {
    try {
        const params = status ? { status } : {};
        const response = await axiosInstance.get(BASE_URL, {
            params
        });
        return response.data;
    } catch (error) {
        console.error('Error fetching security incidents:', error);
        throw error;
    }
};

const getIncident = async (id) => {
    try {
        const response = await axiosInstance.get(`${BASE_URL}/${id}`);
        return response.data;
    } catch (error) {
        console.error(`Error fetching incident ${id}:`, error);
        throw error;
    }
};

const updateIncidentStatus = async (id, status, resolutionNotes) => {
    try {
        const response = await axiosInstance.patch(`${BASE_URL}/${id}/status`,
            { status, resolutionNotes }
        );
        return response.data;
    } catch (error) {
        console.error(`Error updating incident ${id}:`, error);
        throw error;
    }
};

export const securityIncidentService = {
    getAllIncidents,
    getIncident,
    updateIncidentStatus
};
